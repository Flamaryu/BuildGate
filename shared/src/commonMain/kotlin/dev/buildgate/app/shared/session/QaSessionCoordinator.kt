package dev.buildgate.app.shared.session

import dev.buildgate.app.entities.applisting.model.AppListing
import dev.buildgate.app.entities.testmission.model.TestMission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class QaSessionPhase {
    IDLE,
    RUNNING_IN_TARGET_APP,
    PAUSED_FOR_FEEDBACK,
    PAUSED_TARGET_BACKGROUNDED,
    READY_TO_VERIFY,
    VERIFIED
}

data class ActiveQaSessionState(
    val phase: QaSessionPhase = QaSessionPhase.IDLE,
    val activeApp: AppListing? = null,
    val enrollmentId: String? = null,
    val testerId: String? = null,
    val sessionToken: String = "",
    val queueApps: List<AppListing> = emptyList(),
    val missionsByAppId: Map<String, List<TestMission>> = emptyMap(),
    val currentQueueIndex: Int = 0,
    val targetDurationMs: Long = 60_000L,
    val accumulatedActiveMs: Long = 0L,
    val segmentStartedRealtimeMs: Long? = null,
    val sessionLaunchedRealtimeMs: Long = 0L,
    val isHostInBackground: Boolean = false,
    val pauseReason: String? = null,
    val verifiedViaDeepLink: Boolean = false
) {
    fun currentElapsedMs(nowRealtimeMs: Long): Long {
        val liveDelta = if (phase == QaSessionPhase.RUNNING_IN_TARGET_APP && segmentStartedRealtimeMs != null) {
            (nowRealtimeMs - segmentStartedRealtimeMs).coerceAtLeast(0L)
        } else {
            0L
        }
        return (accumulatedActiveMs + liveDelta).coerceAtMost(targetDurationMs)
    }

    fun secondsRemaining(nowRealtimeMs: Long): Int {
        val remMs = (targetDurationMs - currentElapsedMs(nowRealtimeMs)).coerceAtLeast(0L)
        return ((remMs + 999L) / 1000L).toInt()
    }

    val hasNextQueuedApp: Boolean
        get() = queueApps.isNotEmpty() && currentQueueIndex + 1 < queueApps.size

    val nextQueuedApp: AppListing?
        get() = queueApps.getOrNull(currentQueueIndex + 1)

    val activeAppMissions: List<TestMission>
        get() = activeApp?.let { missionsByAppId[it.id] }.orEmpty()
}

interface QaOverlayController {
    fun onSessionUpdated(state: ActiveQaSessionState)
    fun onSessionStopped()
}

class QaSessionCoordinator(
    private val clockRealtimeMs: () -> Long
) {
    private val _state = MutableStateFlow(ActiveQaSessionState())
    val state: StateFlow<ActiveQaSessionState> = _state.asStateFlow()

    private var overlayController: QaOverlayController? = null

    fun attachOverlayController(controller: QaOverlayController?) {
        overlayController = controller
    }

    fun startQueue(
        apps: List<AppListing>,
        testerId: String,
        missionsByAppId: Map<String, List<TestMission>> = emptyMap()
    ) {
        if (apps.isEmpty()) return
        _state.update {
            ActiveQaSessionState(
                phase = QaSessionPhase.IDLE,
                activeApp = apps.first(),
                testerId = testerId,
                queueApps = apps,
                missionsByAppId = missionsByAppId,
                currentQueueIndex = 0
            )
        }
    }

    fun setMissionsForApp(appId: String, missions: List<TestMission>) {
        _state.update { prev ->
            val updatedMap = prev.missionsByAppId.toMutableMap().apply { put(appId, missions) }
            val next = prev.copy(missionsByAppId = updatedMap)
            if (prev.phase != QaSessionPhase.IDLE && prev.activeApp?.id == appId) {
                overlayController?.onSessionUpdated(next)
            }
            next
        }
    }

    fun markMissionCompleted(missionId: String, completed: Boolean = true) {
        _state.update { prev ->
            val activeId = prev.activeApp?.id ?: return@update prev
            val currentList = prev.missionsByAppId[activeId] ?: return@update prev
            val updatedList = currentList.map { m ->
                if (m.id == missionId) m.copy(isCompleted = completed) else m
            }
            val updatedMap = prev.missionsByAppId.toMutableMap().apply { put(activeId, updatedList) }
            val next = prev.copy(missionsByAppId = updatedMap)
            overlayController?.onSessionUpdated(next)
            next
        }
    }

    fun startAppSession(
        app: AppListing,
        enrollmentId: String?,
        testerId: String,
        sessionToken: String,
        targetDurationMs: Long = 60_000L
    ) {
        val now = clockRealtimeMs()
        _state.update { prev ->
            val queueIdx = prev.queueApps.indexOfFirst { it.id == app.id }.takeIf { it >= 0 } ?: prev.currentQueueIndex
            val next = prev.copy(
                phase = QaSessionPhase.RUNNING_IN_TARGET_APP,
                activeApp = app,
                enrollmentId = enrollmentId,
                testerId = testerId,
                sessionToken = sessionToken,
                currentQueueIndex = queueIdx,
                targetDurationMs = targetDurationMs,
                accumulatedActiveMs = 0L,
                segmentStartedRealtimeMs = now,
                sessionLaunchedRealtimeMs = now,
                pauseReason = null,
                verifiedViaDeepLink = false
            )
            overlayController?.onSessionUpdated(next)
            next
        }
    }

    fun onTargetForegroundChanged(isForeground: Boolean) {
        val now = clockRealtimeMs()
        _state.update { prev ->
            if (prev.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK) return@update prev
            if (!isForeground && prev.phase == QaSessionPhase.RUNNING_IN_TARGET_APP) {
                val banked = prev.currentElapsedMs(now)
                val next = prev.copy(
                    phase = QaSessionPhase.PAUSED_TARGET_BACKGROUNDED,
                    accumulatedActiveMs = banked,
                    segmentStartedRealtimeMs = null,
                    pauseReason = "Return to ${prev.activeApp?.title ?: "target app"} to resume timer"
                )
                overlayController?.onSessionUpdated(next)
                next
            } else if (isForeground && prev.phase == QaSessionPhase.PAUSED_TARGET_BACKGROUNDED) {
                val nextPhase = if (prev.accumulatedActiveMs >= prev.targetDurationMs) {
                    QaSessionPhase.READY_TO_VERIFY
                } else {
                    QaSessionPhase.RUNNING_IN_TARGET_APP
                }
                val next = prev.copy(
                    phase = nextPhase,
                    segmentStartedRealtimeMs = if (nextPhase == QaSessionPhase.RUNNING_IN_TARGET_APP) now else null,
                    pauseReason = null
                )
                overlayController?.onSessionUpdated(next)
                next
            } else {
                prev
            }
        }
    }

    fun pauseForFeedback(reason: String = "Logging QA Feedback") {
        val now = clockRealtimeMs()
        _state.update { prev ->
            if (prev.phase != QaSessionPhase.RUNNING_IN_TARGET_APP &&
                prev.phase != QaSessionPhase.PAUSED_TARGET_BACKGROUNDED
            ) return@update prev
            val banked = prev.currentElapsedMs(now)
            val next = prev.copy(
                phase = QaSessionPhase.PAUSED_FOR_FEEDBACK,
                accumulatedActiveMs = banked,
                segmentStartedRealtimeMs = null,
                pauseReason = reason
            )
            overlayController?.onSessionUpdated(next)
            next
        }
    }

    fun resumeFromFeedback() {
        val now = clockRealtimeMs()
        _state.update { prev ->
            if (prev.phase != QaSessionPhase.PAUSED_FOR_FEEDBACK) return@update prev
            val nextPhase = if (prev.accumulatedActiveMs >= prev.targetDurationMs) {
                QaSessionPhase.READY_TO_VERIFY
            } else {
                QaSessionPhase.RUNNING_IN_TARGET_APP
            }
            val next = prev.copy(
                phase = nextPhase,
                segmentStartedRealtimeMs = if (nextPhase == QaSessionPhase.RUNNING_IN_TARGET_APP) now else null,
                pauseReason = null
            )
            overlayController?.onSessionUpdated(next)
            next
        }
    }

    fun evaluateProgress(): ActiveQaSessionState {
        val now = clockRealtimeMs()
        var snapshot = _state.value
        if (snapshot.phase == QaSessionPhase.RUNNING_IN_TARGET_APP &&
            snapshot.currentElapsedMs(now) >= snapshot.targetDurationMs
        ) {
            _state.update { prev ->
                val next = prev.copy(
                    phase = QaSessionPhase.READY_TO_VERIFY,
                    accumulatedActiveMs = prev.targetDurationMs,
                    segmentStartedRealtimeMs = null
                )
                snapshot = next
                overlayController?.onSessionUpdated(next)
                next
            }
        }
        return snapshot
    }

    fun markVerified(viaDeepLink: Boolean = false) {
        val now = clockRealtimeMs()
        _state.update { prev ->
            val banked = prev.currentElapsedMs(now)
            val next = prev.copy(
                phase = QaSessionPhase.VERIFIED,
                accumulatedActiveMs = banked.coerceAtLeast(prev.accumulatedActiveMs),
                segmentStartedRealtimeMs = null,
                pauseReason = null,
                verifiedViaDeepLink = viaDeepLink
            )
            overlayController?.onSessionUpdated(next)
            next
        }
    }

    fun advanceToNextQueuedApp(): AppListing? {
        var nextApp: AppListing? = null
        _state.update { prev ->
            if (prev.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK || !prev.hasNextQueuedApp) {
                return@update prev
            }
            val nextIdx = prev.currentQueueIndex + 1
            nextApp = prev.queueApps[nextIdx]
            val updated = prev.copy(
                phase = QaSessionPhase.IDLE,
                activeApp = nextApp,
                currentQueueIndex = nextIdx,
                accumulatedActiveMs = 0L,
                segmentStartedRealtimeMs = null,
                pauseReason = null,
                verifiedViaDeepLink = false
            )
            overlayController?.onSessionUpdated(updated)
            updated
        }
        return nextApp
    }

    fun advanceAndStartNextQueuedApp(
        sessionToken: String,
        targetDurationMs: Long = 60_000L
    ): AppListing? {
        val now = clockRealtimeMs()
        var nextApp: AppListing? = null
        _state.update { prev ->
            if (prev.phase == QaSessionPhase.PAUSED_FOR_FEEDBACK || !prev.hasNextQueuedApp) {
                return@update prev
            }
            val nextIdx = prev.currentQueueIndex + 1
            nextApp = prev.queueApps[nextIdx]
            val updated = prev.copy(
                phase = QaSessionPhase.RUNNING_IN_TARGET_APP,
                activeApp = nextApp,
                enrollmentId = null,
                sessionToken = sessionToken,
                currentQueueIndex = nextIdx,
                targetDurationMs = targetDurationMs,
                accumulatedActiveMs = 0L,
                segmentStartedRealtimeMs = now,
                sessionLaunchedRealtimeMs = now,
                pauseReason = null,
                verifiedViaDeepLink = false
            )
            overlayController?.onSessionUpdated(updated)
            updated
        }
        return nextApp
    }

    fun onHostBackgroundStateChanged(inBackground: Boolean) {
        _state.update { prev ->
            val next = prev.copy(isHostInBackground = inBackground)
            if (next.phase != QaSessionPhase.IDLE) {
                overlayController?.onSessionUpdated(next)
            }
            next
        }
    }

    fun stopSession() {
        _state.value = ActiveQaSessionState()
        overlayController?.onSessionStopped()
    }
}
