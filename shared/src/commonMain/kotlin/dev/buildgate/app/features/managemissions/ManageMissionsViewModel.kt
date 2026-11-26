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
    val editingMissionId: String? = null,
    val newTitle: String = "",
    val newInstructions: String = "",
    val newBuildVersion: String = "v1.0.0",
    val newIsRequired: Boolean = true,
    val isSaving: Boolean = false
) {
    val isEditing: Boolean get() = editingMissionId != null
}

sealed interface MissionUiEvent {
    data class ShowMessage(val msg: String) : MissionUiEvent
}

class ManageMissionsViewModel(
    private val appListingRepository: AppListingRepository
) : ViewModel() {

    private val _eventChannel = Channel<MissionUiEvent>(Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    private val _selectedAppId = MutableStateFlow("app_1")
    private val _defaultBuildVersion = MutableStateFlow("v1.0.0")
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
        val appChanged = _selectedAppId.value != appId
        _selectedAppId.value = appId
        if (!currentBuildVersion.isNullOrBlank()) {
            _defaultBuildVersion.value = currentBuildVersion
        }
        if (appChanged) {
            _draftState.update {
                it.copy(
                    editingMissionId = null,
                    newTitle = "",
                    newInstructions = "",
                    newBuildVersion = currentBuildVersion?.ifBlank { _defaultBuildVersion.value }
                        ?: _defaultBuildVersion.value,
                    newIsRequired = true
                )
            }
        } else if (!currentBuildVersion.isNullOrBlank() && _draftState.value.editingMissionId == null) {
            _draftState.update { it.copy(newBuildVersion = currentBuildVersion) }
        }
    }

    fun onTitleChanged(t: String) { _draftState.update { it.copy(newTitle = t) } }
    fun onInstructionsChanged(i: String) { _draftState.update { it.copy(newInstructions = i) } }
    fun onBuildVersionChanged(v: String) { _draftState.update { it.copy(newBuildVersion = v) } }
    fun onRequiredChanged(req: Boolean) { _draftState.update { it.copy(newIsRequired = req) } }

    fun startEditing(mission: TestMission) {
        _draftState.update {
            it.copy(
                editingMissionId = mission.id,
                newTitle = mission.title,
                newInstructions = mission.instructions,
                newBuildVersion = mission.buildVersion,
                newIsRequired = mission.isRequired
            )
        }
    }

    fun cancelEditing() {
        _draftState.update {
            it.copy(
                editingMissionId = null,
                newTitle = "",
                newInstructions = "",
                newBuildVersion = _defaultBuildVersion.value,
                newIsRequired = true
            )
        }
    }

    fun addMission() = saveMission()

    fun saveMission() {
        val draft = _draftState.value
        val appId = _selectedAppId.value
        val trimmedTitle = draft.newTitle.trim()
        if (trimmedTitle.length < 3) {
            viewModelScope.launch {
                _eventChannel.send(MissionUiEvent.ShowMessage("Mission title must be at least 3 characters"))
            }
            return
        }

        val buildVer = draft.newBuildVersion.trim().ifBlank { _defaultBuildVersion.value.ifBlank { "1.0.0" } }
        val instructions = draft.newInstructions.trim().ifBlank { "Verify core flow works smoothly." }
        val editingId = draft.editingMissionId

        viewModelScope.launch {
            _draftState.update { it.copy(isSaving = true) }
            val result = runCatching {
                if (editingId == null) {
                    val mission = TestMission(
                        id = "m_${(1000..9999).random()}",
                        appId = appId,
                        buildVersion = buildVer,
                        title = trimmedTitle,
                        instructions = instructions,
                        isRequired = draft.newIsRequired,
                        isCompleted = false
                    )
                    appListingRepository.createMission(mission)
                    "QA mission published for $buildVer"
                } else {
                    val updated = TestMission(
                        id = editingId,
                        appId = appId,
                        buildVersion = buildVer,
                        title = trimmedTitle,
                        instructions = instructions,
                        isRequired = draft.newIsRequired,
                        isCompleted = false
                    )
                    appListingRepository.updateMission(updated)
                    "QA mission updated"
                }
            }
            _draftState.update { it.copy(isSaving = false) }
            result.onSuccess { msg ->
                cancelEditing()
                _eventChannel.send(MissionUiEvent.ShowMessage(msg))
            }.onFailure { err ->
                _eventChannel.send(MissionUiEvent.ShowMessage("Could not save mission: ${err.message ?: "unknown error"}"))
            }
        }
    }

    fun toggleMissionRequired(mission: TestMission) {
        viewModelScope.launch {
            runCatching {
                appListingRepository.updateMission(mission.copy(isRequired = !mission.isRequired))
            }.onSuccess {
                val label = if (!mission.isRequired) "Required" else "Optional"
                _eventChannel.send(MissionUiEvent.ShowMessage("Marked '${mission.title}' as $label"))
            }.onFailure { err ->
                _eventChannel.send(MissionUiEvent.ShowMessage("Failed to update mission: ${err.message ?: "unknown error"}"))
            }
        }
    }

    fun deleteMission(missionId: String) {
        viewModelScope.launch {
            runCatching {
                appListingRepository.deleteMission(missionId)
            }.onSuccess {
                if (_draftState.value.editingMissionId == missionId) {
                    cancelEditing()
                }
                _eventChannel.send(MissionUiEvent.ShowMessage("QA mission deleted"))
            }.onFailure { err ->
                _eventChannel.send(MissionUiEvent.ShowMessage("Could not delete mission: ${err.message ?: "unknown error"}"))
            }
        }
    }
}
