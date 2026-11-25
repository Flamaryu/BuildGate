package dev.buildgate.app.shared.session

import dev.buildgate.app.shared.platform.getPlatformVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GlobalQaSession {
    val coordinator: QaSessionCoordinator by lazy {
        QaSessionCoordinator { getPlatformVerifier().monotonicElapsedMs() }
    }

    private val _resumeTick = MutableStateFlow(0L)
    val resumeTick: StateFlow<Long> = _resumeTick.asStateFlow()

    fun notifyHostResumed() {
        _resumeTick.value = _resumeTick.value + 1L
        coordinator.onHostBackgroundStateChanged(inBackground = false)
    }

    fun notifyHostStopped() {
        coordinator.onHostBackgroundStateChanged(inBackground = true)
    }
}
