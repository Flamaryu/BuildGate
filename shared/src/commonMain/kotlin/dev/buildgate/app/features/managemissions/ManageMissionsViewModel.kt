package dev.buildgate.app.features.managemissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.testmission.model.TestMission
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MissionUiState(
    val appId: String = "app_1",
    val missions: List<TestMission> = emptyList(),
    val newTitle: String = "",
    val newInstructions: String = "",
    val newBuildVersion: String = "v1.0.4-rc1",
    val newIsRequired: Boolean = true
)

sealed interface MissionUiEvent {
    data class ShowMessage(val msg: String) : MissionUiEvent
}

class ManageMissionsViewModel(
    private val appListingRepository: AppListingRepository
) : ViewModel() {

    private val _eventChannel = Channel<MissionUiEvent>()
    val eventFlow = _eventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(MissionUiState())

    val uiState: StateFlow<MissionUiState> = appListingRepository.getMissionsForApp("app_1")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        .let { missionsFlow ->
            MutableStateFlow(MissionUiState()).asStateFlow()
        }

    fun onTitleChanged(t: String) { _uiState.value = _uiState.value.copy(newTitle = t) }
    fun onInstructionsChanged(i: String) { _uiState.value = _uiState.value.copy(newInstructions = i) }

    fun addMission() {
        val state = _uiState.value
        if (state.newTitle.isBlank()) return

        viewModelScope.launch {
            val mission = TestMission(
                id = "m_${(100..999).random()}",
                appId = state.appId,
                buildVersion = state.newBuildVersion,
                title = state.newTitle,
                instructions = state.newInstructions,
                isRequired = state.newIsRequired,
                isCompleted = false
            )
            appListingRepository.createMission(mission)
            _uiState.value = _uiState.value.copy(newTitle = "", newInstructions = "")
            _eventChannel.send(MissionUiEvent.ShowMessage("New test mission created!"))
        }
    }
}
