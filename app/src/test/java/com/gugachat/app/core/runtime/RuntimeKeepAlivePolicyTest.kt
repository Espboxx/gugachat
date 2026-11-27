package com.gugachat.app.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeKeepAlivePolicyTest {

    @Test
    fun decideStartAction_coversAndroid12To14ForegroundBackgroundAndGraceMatrix() {
        // Inactive work always stops service regardless of foreground/sdk/exemption
        assertEquals(
            KeepAliveDecision.STOP_SERVICE,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = false,
                sdkInt = 34,
                isForegroundOrGrace = true,
                canStartFgsInBackground = true,
            ),
        )
        assertEquals(
            KeepAliveDecision.STOP_SERVICE,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = false,
                sdkInt = 28,
                isForegroundOrGrace = false,
            ),
        )

        // Active + foreground (including pause grace) starts dataSync FGS on API 31/34
        assertEquals(
            KeepAliveDecision.START_FGS,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = true,
                sdkInt = 34,
                isForegroundOrGrace = true,
            ),
        )
        assertEquals(
            KeepAliveDecision.START_FGS,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = true,
                sdkInt = 31,
                isForegroundOrGrace = true,
            ),
        )

        // Active + pure background on API 31+/34 NEVER illegally starts FGS -> WAKE_LOCK_ONLY
        assertEquals(
            KeepAliveDecision.WAKE_LOCK_ONLY,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = true,
                sdkInt = 31,
                isForegroundOrGrace = false,
                canStartFgsInBackground = false,
            ),
        )
        assertEquals(
            KeepAliveDecision.WAKE_LOCK_ONLY,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = true,
                sdkInt = 34,
                isForegroundOrGrace = false,
                canStartFgsInBackground = false,
            ),
        )

        // Active + background when FGS already running or exemption granted -> START_FGS
        assertEquals(
            KeepAliveDecision.START_FGS,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = true,
                sdkInt = 34,
                isForegroundOrGrace = false,
                canStartFgsInBackground = true,
            ),
        )

        // Active + background on pre-Android 12 (API 26..30) -> START_FGS allowed
        assertEquals(
            KeepAliveDecision.START_FGS,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = true,
                sdkInt = 30,
                isForegroundOrGrace = false,
                canStartFgsInBackground = false,
            ),
        )
    }

    @Test
    fun foregroundTracker_tracksMultiActivityRefcountAndPauseGraceWindow() {
        var fakeNowMs = 10_000L
        val tracker = AppForegroundTracker(
            pauseGraceWindowMs = 1500L,
            clock = { fakeNowMs },
        )

        assertFalse(tracker.isAppForeground)
        assertFalse(tracker.isForegroundFlow.value)
        assertFalse(tracker.isForegroundOrPauseGrace())

        // First activity starts
        tracker.recordStarted()
        assertTrue(tracker.isAppForeground)
        assertTrue(tracker.isForegroundFlow.value)
        assertEquals(1, tracker.activeStartedCount)

        // Second activity starts (e.g. rotation / sub-activity), first stops -> still foreground
        tracker.recordStarted()
        assertEquals(2, tracker.activeStartedCount)
        assertTrue(tracker.isAppForeground)

        tracker.recordStopped()
        assertEquals(1, tracker.activeStartedCount)
        assertTrue(tracker.isAppForeground)
        assertTrue(tracker.isForegroundFlow.value)

        // Remaining activity stops without pause -> immediately background and not in grace
        tracker.recordStopped()
        assertEquals(0, tracker.activeStartedCount)
        assertFalse(tracker.isAppForeground)
        assertFalse(tracker.isForegroundFlow.value)
        assertFalse(tracker.isForegroundOrPauseGrace())

        // Extra stop never goes negative
        tracker.recordStopped()
        assertEquals(0, tracker.activeStartedCount)
        assertFalse(tracker.isAppForeground)

        // Pause -> stop transition preserves grace window for 1500ms
        tracker.recordStarted()
        fakeNowMs = 20_000L
        tracker.recordPaused()
        tracker.recordStopped()

        assertFalse(tracker.isAppForeground)
        assertTrue(tracker.isForegroundOrPauseGrace(20_000L))
        assertTrue(tracker.isForegroundOrPauseGrace(21_500L))
        assertFalse(tracker.isForegroundOrPauseGrace(21_501L))

        // Policy integrates cleanly with pause grace on Android 14 (SDK 34)
        assertEquals(
            KeepAliveDecision.START_FGS,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = true,
                sdkInt = 34,
                isForegroundOrGrace = tracker.isForegroundOrPauseGrace(21_000L),
            ),
        )
        assertEquals(
            KeepAliveDecision.WAKE_LOCK_ONLY,
            KeepAliveStartPolicy.decideStartAction(
                hasActiveWork = true,
                sdkInt = 34,
                isForegroundOrGrace = tracker.isForegroundOrPauseGrace(22_000L),
            ),
        )

        // Resume clears stale pause timestamp so next stop without pause exits grace immediately
        tracker.recordStarted()
        tracker.recordResumed()
        assertEquals(0L, tracker.lastPauseTimestampMs)
        tracker.recordStopped()
        assertFalse(tracker.isForegroundOrPauseGrace(20_500L))
    }

    @Test
    fun controller_handlesAcquireProgressReleaseStopAllAndOemFgsFallback() {
        var fakeNowMs = 50_000L
        val tracker = AppForegroundTracker(pauseGraceWindowMs = 1500L, clock = { fakeNowMs })
        val bridge = FakeKeepAlivePlatformBridge()
        val controller = RuntimeKeepAliveController(
            foregroundTracker = tracker,
            platformBridge = bridge,
            sdkIntProvider = { 34 },
            clock = { fakeNowMs },
        )

        // 1. Background start on API 34 acquires bounded wake lock without illegal FGS start
        var webdavStopCount = 0
        controller.acquireWork("sync:webdav", "WebDAV 同步中") { webdavStopCount++ }
        assertTrue(controller.hasActiveWork)
        assertEquals(0, bridge.fgsStartAttempts)
        assertEquals(listOf(RuntimeKeepAliveController.WAKE_LOCK_TIMEOUT_MS), bridge.wakeLockAcquires)

        // 2. App enters foreground and acquires streaming task -> starts FGS and releases fallback wake lock
        tracker.recordStarted()
        var streamStopCount = 0
        controller.acquireWork("stream:conv-1", "AI 生成中", charCount = 12) { streamStopCount++ }
        assertEquals(1, bridge.fgsStartAttempts)
        assertEquals(1, bridge.wakeLockReleases)
        assertEquals(2, controller.activeTasks.value.size)

        // 3. Progress update throttles notification refresh to 1s while keeping latest StateFlow
        controller.updateProgress("stream:conv-1", "AI 生成中", charCount = 48)
        assertEquals(48, controller.activeTasks.value["stream:conv-1"]?.charCount)
        assertEquals(0, bridge.notificationRefreshes)

        fakeNowMs += 500L
        controller.updateProgress("stream:conv-1", "AI 生成中", charCount = 96)
        assertEquals(96, controller.activeTasks.value["stream:conv-1"]?.charCount)
        assertEquals(0, bridge.notificationRefreshes)

        fakeNowMs += 700L
        controller.updateProgress("stream:conv-1", "AI 生成中", charCount = 180)
        assertEquals(180, controller.activeTasks.value["stream:conv-1"]?.charCount)
        assertEquals(1, bridge.notificationRefreshes)

        // 4. Releasing one task keeps service alive & refreshes notification without redundant startForegroundService
        controller.releaseWork("sync:webdav")
        assertTrue(controller.hasActiveWork)
        assertEquals(0, bridge.stopCalls)
        assertEquals(1, bridge.fgsStartAttempts)
        assertEquals(2, bridge.notificationRefreshes)

        controller.releaseWork("stream:conv-1")
        assertFalse(controller.hasActiveWork)
        assertEquals(1, bridge.stopCalls)
        assertFalse(bridge.serviceActive)

        // Progress update on released task is ignored (never resurrects stopped work)
        controller.updateProgress("stream:conv-1", "late", charCount = 999)
        assertFalse(controller.hasActiveWork)

        // 5. OEM FGS exception falls back cleanly to PARTIAL_WAKE_LOCK without crashing
        bridge.failNextFgsWith = IllegalStateException("MIUI background FGS blocked")
        var stopCalled = 0
        controller.acquireWork("stream:conv-2", "生成中") { stopCalled++ }
        assertEquals(2, bridge.fgsStartAttempts)
        assertFalse(bridge.serviceActive)
        assertEquals(2, bridge.wakeLockAcquires.size)

        // 6. Add another concurrent task and test onServiceTimeout + requestStopAll
        var syncStop2 = 0
        controller.acquireWork("sync:webdav-2", "WebDAV 上传") { syncStop2++ }
        assertEquals(3, bridge.fgsStartAttempts)
        assertTrue(bridge.serviceActive)
        assertEquals(4, bridge.wakeLockReleases)

        bridge.serviceActive = false
        controller.onServiceTimeout()
        assertTrue(controller.hasActiveWork)
        assertEquals(3, bridge.wakeLockAcquires.size)

        // requestStopAll invokes callbacks ONLY for still-active tasks, clears tasks, releases wake lock & stops service
        controller.requestStopAll()
        assertEquals(1, stopCalled)
        assertEquals(1, syncStop2)
        assertEquals(0, webdavStopCount)
        assertEquals(0, streamStopCount)
        assertFalse(controller.hasActiveWork)
        assertEquals(2, bridge.stopCalls)
        assertEquals(5, bridge.wakeLockReleases)
    }

    private class FakeKeepAlivePlatformBridge : KeepAlivePlatformBridge {
        var serviceActive = false
        var fgsStartAttempts = 0
        var notificationRefreshes = 0
        var stopCalls = 0
        var wakeLockReleases = 0
        val wakeLockAcquires = mutableListOf<Long>()
        var failNextFgsWith: Throwable? = null

        override fun isServiceActiveOrStarting(): Boolean = serviceActive

        override fun startForegroundKeepAliveService(): Result<Unit> {
            fgsStartAttempts += 1
            val err = failNextFgsWith
            failNextFgsWith = null
            return if (err != null) {
                serviceActive = false
                Result.failure(err)
            } else {
                serviceActive = true
                Result.success(Unit)
            }
        }

        override fun refreshNotificationIfRunning(tasks: Map<String, KeepAliveTaskEntry>) {
            notificationRefreshes += 1
        }

        override fun stopOrDetachKeepAliveService() {
            serviceActive = false
            stopCalls += 1
        }

        override fun acquireWakeLock(timeoutMs: Long) {
            wakeLockAcquires += timeoutMs
        }

        override fun releaseWakeLock() {
            wakeLockReleases += 1
        }
    }
}
