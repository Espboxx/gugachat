package com.gugachat.app.core.runtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks process foreground visibility across multiple activities via refcounting
 * and retains a brief pause grace window around transitions so Android 12+
 * foreground service starts remain eligible during activity handoffs/backgrounding.
 */
class AppForegroundTracker(
    private val pauseGraceWindowMs: Long = DEFAULT_PAUSE_GRACE_WINDOW_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : Application.ActivityLifecycleCallbacks {

    private val lock = Any()
    private var startedCount: Int = 0
    private var lastPauseAtMs: Long = 0L

    private val _isForegroundFlow = MutableStateFlow(false)
    val isForegroundFlow: StateFlow<Boolean> = _isForegroundFlow.asStateFlow()

    val isAppForeground: Boolean
        get() = synchronized(lock) { startedCount > 0 }

    val lastPauseTimestampMs: Long
        get() = synchronized(lock) { lastPauseAtMs }

    val activeStartedCount: Int
        get() = synchronized(lock) { startedCount }

    fun isForegroundOrPauseGrace(nowMs: Long = clock()): Boolean = synchronized(lock) {
        if (startedCount > 0) return@synchronized true
        val pausedAt = lastPauseAtMs
        if (pausedAt <= 0L || pauseGraceWindowMs <= 0L) return@synchronized false
        nowMs >= pausedAt && (nowMs - pausedAt) <= pauseGraceWindowMs
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        recordStarted()
    }

    override fun onActivityResumed(activity: Activity) {
        recordResumed()
    }

    override fun onActivityPaused(activity: Activity) {
        recordPaused(clock())
    }

    override fun onActivityStopped(activity: Activity) {
        recordStopped()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    internal fun recordStarted() {
        synchronized(lock) {
            startedCount += 1
            _isForegroundFlow.value = startedCount > 0
        }
    }

    internal fun recordResumed() {
        synchronized(lock) {
            lastPauseAtMs = 0L
        }
    }

    internal fun recordPaused(nowMs: Long = clock()) {
        synchronized(lock) {
            lastPauseAtMs = nowMs
        }
    }

    internal fun recordStopped() {
        synchronized(lock) {
            startedCount = (startedCount - 1).coerceAtLeast(0)
            _isForegroundFlow.value = startedCount > 0
        }
    }

    companion object {
        const val DEFAULT_PAUSE_GRACE_WINDOW_MS = 1500L
    }
}
