package dev.buildgate.app.features.managemissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.applisting.repository.AppListingRepository
import dev.buildgate.app.entities.testmission.model.TestMission
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val _eventChannel = Channel<MissionUiEvent>(Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    private val _selectedAppId = MutableStateFlow("app_1")
    private val _draftState = MutableStateFlow(MissionUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MissionUiState> = combine(
        _selectedAppId,
        _selectedAppId.flatMapLatest { appListingRepository.getMissionsForApp(it) },
        _draftState
    ) { appId, missions, draft ->
        draft.copy(
            appId = appId,
            missions = missions
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MissionUiState())

    fun selectApp(appId: String, currentBuildVersion: String? = null) {
        _selectedAppId.value = appId
        if (!currentBuildVersion.isNullOrBlank()) {
            _draftState.update { it.copy(newBuildVersion = currentBuildVersion) }
        }
    }

    fun onTitleChanged(t: String) { _draftState.update { it.copy(newTitle = t) } }
    fun onInstructionsChanged(i: String) { _draftState.update { it.copy(newInstructions = i) } }
    fun onBuildVersionChanged(v: String) { _draftState.update { it.copy(newBuildVersion = v) } }
    fun onRequiredChanged(req: Boolean) { _draftState.update { it.copy(newIsRequired = req) } }

    fun addMission() {
        val draft = _draftState.value
        val appId = _selectedAppId.value
        if (draft.newTitle.isBlank()) {
            viewModelScope.launch {
                _eventChannel.send(MissionUiEvent.ShowMessage("Enter a mission title first"))
            }
            return
        }

        viewModelScope.launch {
            val mission = TestMission(
                id = "m_${(1000..9999).random()}",
                appId = appId,
                buildVersion = draft.newBuildVersion.ifBlank { "1.0.0" },
                title = draft.newTitle.trim(),
                instructions = draft.newInstructions.trim().ifBlank { "Verify core flow works smoothly." },
                isRequired = draft.newIsRequired,
                isCompleted = false
            )
            appListingRepository.createMission(mission)
            _draftState.update { it.copy(newTitle = "", newInstructions = "") }
            _eventChannel.send(MissionUiEvent.ShowMessage("Test mission published for ${mission.buildVersion}"))
        }
    }
}
