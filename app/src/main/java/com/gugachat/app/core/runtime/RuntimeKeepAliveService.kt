package com.gugachat.app.core.runtime

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gugachat.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class KeepAliveDecision {
    START_FGS,
    WAKE_LOCK_ONLY,
    STOP_SERVICE,
}

/**
 * Pure-Kotlin start policy for Android 12+ background FGS restrictions.
 */
object KeepAliveStartPolicy {
    private const val ANDROID_12_SDK_INT = 31

    fun decideStartAction(
        hasActiveWork: Boolean,
        sdkInt: Int,
        isForegroundOrGrace: Boolean,
        canStartFgsInBackground: Boolean = false,
    ): KeepAliveDecision {
        if (!hasActiveWork) return KeepAliveDecision.STOP_SERVICE
        if (isForegroundOrGrace) return KeepAliveDecision.START_FGS
        if (sdkInt < ANDROID_12_SDK_INT) return KeepAliveDecision.START_FGS
        if (canStartFgsInBackground) return KeepAliveDecision.START_FGS
        return KeepAliveDecision.WAKE_LOCK_ONLY
    }
}

data class KeepAliveTaskEntry(
    val taskId: String,
    val label: String,
    val charCount: Int = 0,
    val startedAtMs: Long = 0L,
)

internal interface KeepAlivePlatformBridge {
    fun isServiceActiveOrStarting(): Boolean
    fun startForegroundKeepAliveService(): Result<Unit>
    fun refreshNotificationIfRunning(tasks: Map<String, KeepAliveTaskEntry>)
    fun stopOrDetachKeepAliveService()
    fun acquireWakeLock(timeoutMs: Long)
    fun releaseWakeLock()
}

private class AndroidKeepAlivePlatformBridge(
    private val appContext: Context,
) : KeepAlivePlatformBridge {
    private val wakeLockLock = Any()
    private var partialWakeLock: PowerManager.WakeLock? = null

    override fun isServiceActiveOrStarting(): Boolean =
        RuntimeKeepAliveService.isServiceRunning() || RuntimeKeepAliveService.hasInFlightStarts()

    override fun startForegroundKeepAliveService(): Result<Unit> {
        RuntimeKeepAliveService.markStartDispatched()
        val intent = Intent(appContext, RuntimeKeepAliveService::class.java).apply {
            action = RuntimeKeepAliveService.ACTION_START
        }
        val result = runCatching {
            ContextCompat.startForegroundService(appContext, intent)
        }
        if (result.isFailure) {
            RuntimeKeepAliveService.clearStartDispatchedOnFailure()
        }
        return result
    }

    override fun refreshNotificationIfRunning(tasks: Map<String, KeepAliveTaskEntry>) {
        RuntimeKeepAliveService.refreshRunningNotification(appContext, tasks)
    }

    override fun stopOrDetachKeepAliveService() {
        RuntimeKeepAliveService.stopRunningServiceIfSafe(appContext)
    }

    override fun acquireWakeLock(timeoutMs: Long) {
        synchronized(wakeLockLock) {
            runCatching {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@runCatching
                val wl = partialWakeLock ?: pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    RuntimeKeepAliveController.WAKE_LOCK_TAG,
                ).apply {
                    setReferenceCounted(false)
                }.also { partialWakeLock = it }
                wl.acquire(timeoutMs)
            }
        }
    }

    override fun releaseWakeLock() {
        synchronized(wakeLockLock) {
            runCatching {
                val wl = partialWakeLock ?: return@runCatching
                if (wl.isHeld) {
                    wl.release()
                }
            }
        }
    }
}

/**
 * Coordinates long-running work (streaming generations, WebDAV sync) with
 * Android 14 dataSync foreground keep-alive and bounded PARTIAL_WAKE_LOCK fallback.
 */
class RuntimeKeepAliveController internal constructor(
    private val foregroundTracker: AppForegroundTracker,
    private val platformBridge: KeepAlivePlatformBridge,
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val clock: () -> Long = { System.currentTimeMillis() },
    registerAsActiveController: Boolean = false,
) {
    constructor(
        appContext: Context,
        foregroundTracker: AppForegroundTracker,
    ) : this(
        foregroundTracker = foregroundTracker,
        platformBridge = AndroidKeepAlivePlatformBridge(appContext.applicationContext),
        sdkIntProvider = { Build.VERSION.SDK_INT },
        clock = { System.currentTimeMillis() },
        registerAsActiveController = true,
    )

    private val lock = Any()
    private val _activeTasks = MutableStateFlow<Map<String, KeepAliveTaskEntry>>(emptyMap())
    val activeTasks: StateFlow<Map<String, KeepAliveTaskEntry>> = _activeTasks.asStateFlow()

    private val stopCallbacks = LinkedHashMap<String, () -> Unit>()
    private var lastNotificationRefreshAtMs: Long = 0L

    val hasActiveWork: Boolean
        get() = _activeTasks.value.isNotEmpty()

    init {
        if (registerAsActiveController) {
            bindActiveController(this)
        }
    }

    fun acquireWork(
        taskId: String,
        label: String,
        onStopRequested: (() -> Unit)? = null,
    ) {
        acquireWork(
            taskId = taskId,
            label = label,
            charCount = 0,
            onStopRequested = onStopRequested,
        )
    }

    fun acquireWork(
        taskId: String,
        label: String,
        charCount: Int,
        onStopRequested: (() -> Unit)? = null,
    ) {
        val cleanId = taskId.trim()
        if (cleanId.isEmpty()) return
        val snapshot: Map<String, KeepAliveTaskEntry>
        synchronized(lock) {
            val next = LinkedHashMap(_activeTasks.value)
            val existing = next[cleanId]
            next[cleanId] = KeepAliveTaskEntry(
                taskId = cleanId,
                label = label.ifBlank { cleanId },
                charCount = charCount.coerceAtLeast(0),
                startedAtMs = existing?.startedAtMs ?: clock(),
            )
            if (onStopRequested != null) {
                stopCallbacks[cleanId] = onStopRequested
            }
            _activeTasks.value = next
            snapshot = next
        }
        syncKeepAliveState(snapshot = snapshot, forceRefresh = true)
    }

    fun updateProgress(
        taskId: String,
        label: String,
        charCount: Int? = null,
    ) {
        val cleanId = taskId.trim()
        if (cleanId.isEmpty()) return
        val snapshot: Map<String, KeepAliveTaskEntry>
        val shouldRefresh: Boolean
        synchronized(lock) {
            val current = _activeTasks.value
            val existing = current[cleanId] ?: return
            val nextCharCount = charCount?.coerceAtLeast(0) ?: existing.charCount
            val nextLabel = label.ifBlank { existing.label }
            if (existing.label == nextLabel && existing.charCount == nextCharCount) {
                return
            }
            val next = LinkedHashMap(current)
            next[cleanId] = existing.copy(label = nextLabel, charCount = nextCharCount)
            _activeTasks.value = next
            snapshot = next

            val now = clock()
            shouldRefresh = (now - lastNotificationRefreshAtMs) >= PROGRESS_NOTIFY_THROTTLE_MS
            if (shouldRefresh) {
                lastNotificationRefreshAtMs = now
            }
        }
        if (shouldRefresh) {
            platformBridge.refreshNotificationIfRunning(snapshot)
        }
    }

    fun releaseWork(taskId: String) {
        val cleanId = taskId.trim()
        if (cleanId.isEmpty()) return
        val snapshot: Map<String, KeepAliveTaskEntry>
        synchronized(lock) {
            val current = _activeTasks.value
            if (cleanId !in current) {
                stopCallbacks.remove(cleanId)
                return
            }
            val next = LinkedHashMap(current)
            next.remove(cleanId)
            stopCallbacks.remove(cleanId)
            _activeTasks.value = next
            snapshot = next
        }
        syncKeepAliveState(snapshot = snapshot, forceRefresh = true)
    }

    fun requestStopAll() {
        val callbacksToInvoke: List<() -> Unit>
        synchronized(lock) {
            callbacksToInvoke = stopCallbacks.values.toList()
            stopCallbacks.clear()
            _activeTasks.value = emptyMap()
        }
        platformBridge.releaseWakeLock()
        platformBridge.stopOrDetachKeepAliveService()
        for (callback in callbacksToInvoke) {
            runCatching { callback.invoke() }
        }
    }

    fun refreshPolicy() {
        val snapshot = synchronized(lock) { _activeTasks.value }
        syncKeepAliveState(snapshot = snapshot, forceRefresh = true)
    }

    internal fun onServiceTimeout() {
        if (hasActiveWork) {
            platformBridge.acquireWakeLock(WAKE_LOCK_TIMEOUT_MS)
        } else {
            platformBridge.releaseWakeLock()
        }
    }

    private fun syncKeepAliveState(
        snapshot: Map<String, KeepAliveTaskEntry>,
        forceRefresh: Boolean,
    ) {
        val hasWork = snapshot.isNotEmpty()
        val serviceActive = platformBridge.isServiceActiveOrStarting()
        val decision = KeepAliveStartPolicy.decideStartAction(
            hasActiveWork = hasWork,
            sdkInt = sdkIntProvider(),
            isForegroundOrGrace = foregroundTracker.isForegroundOrPauseGrace(clock()),
            canStartFgsInBackground = serviceActive,
        )

        when (decision) {
            KeepAliveDecision.STOP_SERVICE -> {
                platformBridge.releaseWakeLock()
                platformBridge.stopOrDetachKeepAliveService()
            }

            KeepAliveDecision.WAKE_LOCK_ONLY -> {
                platformBridge.acquireWakeLock(WAKE_LOCK_TIMEOUT_MS)
            }

            KeepAliveDecision.START_FGS -> {
                if (serviceActive) {
                    platformBridge.releaseWakeLock()
                    if (forceRefresh) {
                        synchronized(lock) {
                            lastNotificationRefreshAtMs = clock()
                        }
                        platformBridge.refreshNotificationIfRunning(snapshot)
                    }
                } else {
                    synchronized(lock) {
                        lastNotificationRefreshAtMs = clock()
                    }
                    val startResult = platformBridge.startForegroundKeepAliveService()
                    if (startResult.isSuccess) {
                        platformBridge.releaseWakeLock()
                    } else {
                        // Never crash on ForegroundServiceStartNotAllowedException / SecurityException /
                        // IllegalStateException (including MIUI/ColorOS/HyperOS background start blocks).
                        platformBridge.acquireWakeLock(WAKE_LOCK_TIMEOUT_MS)
                    }
                }
            }
        }
    }

    companion object {
        internal const val WAKE_LOCK_TAG = "GuGuChat:RuntimeKeepAlive"
        const val WAKE_LOCK_TIMEOUT_MS = 10L * 60_000L
        private const val PROGRESS_NOTIFY_THROTTLE_MS = 1000L

        @Volatile
        private var activeControllerRef: WeakReference<RuntimeKeepAliveController>? = null

        val activeController: RuntimeKeepAliveController?
            get() = activeControllerRef?.get()

        internal fun bindActiveController(controller: RuntimeKeepAliveController?) {
            activeControllerRef = controller?.let { WeakReference(it) }
        }
    }
}

class RuntimeKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        serviceRunning.set(true)
        ensureKeepAliveChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceRunning.set(true)
        ensureKeepAliveChannel(this)

        val controller = RuntimeKeepAliveController.activeController
        val tasks = controller?.activeTasks?.value.orEmpty()
        val notification = buildOngoingNotification(this, tasks)

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        val foregroundStarted = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        }.isSuccess
        clearStartDispatched()

        if (!foregroundStarted) {
            serviceRunning.set(false)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val action = intent?.action
        val isExplicitStop = action == ACTION_STOP || action == ACTION_STOP_WORK
        if (isExplicitStop) {
            controller?.requestStopAll()
            detachAndStop(startId)
            return START_NOT_STICKY
        }

        if (controller == null || !controller.hasActiveWork) {
            detachAndStop(startId)
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        RuntimeKeepAliveController.activeController?.onServiceTimeout()
        detachAndStop(startId)
    }

    override fun onDestroy() {
        serviceRunning.set(false)
        clearStartDispatched()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun detachAndStop(startId: Int) {
        serviceRunning.set(false)
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelfResult(startId)
    }

    companion object {
        const val CHANNEL_ID = "gugachat_runtime_keepalive"
        const val COMPLETION_CHANNEL_ID = "gugachat_runtime_completion"
        const val NOTIFICATION_ID = 4001
        const val COMPLETION_NOTIFICATION_ID = 4002

        const val ACTION_START = "com.gugachat.app.runtime.ACTION_START"
        const val ACTION_STOP = "com.gugachat.app.runtime.ACTION_STOP"
        const val ACTION_STOP_WORK = "com.gugachat.app.runtime.ACTION_STOP_WORK"

        private const val REQUEST_CODE_OPEN = 4010
        private const val REQUEST_CODE_STOP = 4011
        private const val REQUEST_CODE_COMPLETION_OPEN = 4012

        val activeController: RuntimeKeepAliveController?
            get() = RuntimeKeepAliveController.activeController

        private val serviceRunning = AtomicBoolean(false)
        private val inFlightStarts = AtomicInteger(0)

        internal fun isServiceRunning(): Boolean = serviceRunning.get()

        internal fun hasInFlightStarts(): Boolean = inFlightStarts.get() > 0

        internal fun markStartDispatched() {
            inFlightStarts.incrementAndGet()
        }

        internal fun clearStartDispatchedOnFailure() {
            clearStartDispatched()
        }

        private fun clearStartDispatched() {
            while (true) {
                val cur = inFlightStarts.get()
                if (cur <= 0) break
                if (inFlightStarts.compareAndSet(cur, cur - 1)) break
            }
        }

        internal fun refreshRunningNotification(
            context: Context,
            tasks: Map<String, KeepAliveTaskEntry>,
        ) {
            if (!serviceRunning.get() || tasks.isEmpty()) return
            val appCtx = context.applicationContext
            ensureKeepAliveChannel(appCtx)
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            runCatching {
                nm.notify(NOTIFICATION_ID, buildOngoingNotification(appCtx, tasks))
            }
        }

        internal fun stopRunningServiceIfSafe(context: Context) {
            // If a startForegroundService call is still queued before onStartCommand runs,
            // let onStartCommand invoke startForeground() first and then self-stop immediately.
            if (inFlightStarts.get() > 0) return
            if (!serviceRunning.get()) return
            val appCtx = context.applicationContext
            runCatching {
                appCtx.stopService(Intent(appCtx, RuntimeKeepAliveService::class.java))
            }
        }

        fun hasNotificationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context.applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun completionNotice(
            context: Context,
            title: String,
            message: String,
            notificationId: Int = COMPLETION_NOTIFICATION_ID,
        ): Boolean {
            val appCtx = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    appCtx,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }

            ensureCompletionChannel(appCtx)
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
            val openPendingIntent = PendingIntent.getActivity(
                appCtx,
                REQUEST_CODE_COMPLETION_OPEN,
                buildLaunchMainIntent(appCtx),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(appCtx, COMPLETION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openPendingIntent)
                .build()

            return runCatching {
                nm.notify(notificationId, notification)
                true
            }.getOrDefault(false)
        }

        private fun ensureKeepAliveChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val isEnglish = isEnglishLocale()
            val channel = NotificationChannel(
                CHANNEL_ID,
                if (isEnglish) "Background Keep-Alive" else "后台生成与同步保活",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = if (isEnglish) {
                    "Keeps active AI generation and sync running smoothly in background"
                } else {
                    "保障 AI 生成与同步任务在切出应用时持续稳定运行"
                }
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        private fun ensureCompletionChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val isEnglish = isEnglishLocale()
            val channel = NotificationChannel(
                COMPLETION_CHANNEL_ID,
                if (isEnglish) "Task Completion Alerts" else "后台任务完成提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = if (isEnglish) {
                    "Notifies when a background generation or sync task completes"
                } else {
                    "后台生成或备份同步完成时发出提醒"
                }
            }
            nm.createNotificationChannel(channel)
        }

        private fun buildOngoingNotification(
            context: Context,
            tasks: Map<String, KeepAliveTaskEntry>,
        ): Notification {
            val isEnglish = isEnglishLocale()
            val title = if (isEnglish) "GuGuChat running in background" else "GuGuChat 后台运行中"
            val body = formatNotificationBody(tasks, isEnglish)
            val stopLabel = if (isEnglish) "Stop" else "停止"

            val openPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN,
                buildLaunchMainIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val stopIntent = Intent(context, RuntimeKeepAliveService::class.java).apply {
                action = ACTION_STOP_WORK
            }
            val stopPendingIntent = PendingIntent.getForegroundService(
                context,
                REQUEST_CODE_STOP,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(body)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openPendingIntent)
                .addAction(0, stopLabel, stopPendingIntent)
                .build()
        }

        private fun buildLaunchMainIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        private fun formatNotificationBody(
            tasks: Map<String, KeepAliveTaskEntry>,
            isEnglish: Boolean,
        ): String {
            if (tasks.isEmpty()) {
                return if (isEnglish) "Finishing background task…" else "正在收尾后台任务…"
            }
            val entries = tasks.values.toList()
            val primary = entries.first()
            val base = if (primary.charCount > 0) {
                if (isEnglish) "${primary.label} (${primary.charCount} chars)" else "${primary.label}（${primary.charCount} 字）"
            } else {
                primary.label
            }
            return if (entries.size <= 1) {
                base
            } else if (isEnglish) {
                "$base (+${entries.size - 1} active)"
            } else {
                "$base 等 ${entries.size} 项任务"
            }
        }

        private fun isEnglishLocale(): Boolean =
            Locale.getDefault().language.startsWith("en", ignoreCase = true)
    }
}
