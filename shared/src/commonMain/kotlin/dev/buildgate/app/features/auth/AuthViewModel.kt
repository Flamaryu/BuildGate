package dev.buildgate.app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.auth.model.AuthSession
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.auth.repository.DisplayNameAvailability
import dev.buildgate.app.entities.testerprofile.model.KarmaEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiEvent {
    data class ShowMessage(val message: String) : AuthUiEvent
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val sessionState: StateFlow<AuthSession> = authRepository.getAuthSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthSession())

    @OptIn(ExperimentalCoroutinesApi::class)
    val karmaLedger: StateFlow<List<KarmaEntry>> = sessionState
        .flatMapLatest { session ->
            val uid = session.user?.id
            if (uid != null) authRepository.getKarmaLedger(uid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _eventChannel = Channel<AuthUiEvent>(Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    private val _emailInput = MutableStateFlow("")
    val emailInput = _emailInput.asStateFlow()

    private val _usernameInput = MutableStateFlow("")
    val usernameInput = _usernameInput.asStateFlow()

    private val _passwordInput = MutableStateFlow("")
    val passwordInput = _passwordInput.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    private val _displayNameStatus = MutableStateFlow<DisplayNameAvailability?>(null)
    val displayNameStatus: StateFlow<DisplayNameAvailability?> = _displayNameStatus.asStateFlow()

    private val _isCheckingDisplayName = MutableStateFlow(false)
    val isCheckingDisplayName: StateFlow<Boolean> = _isCheckingDisplayName.asStateFlow()

    private var availabilityCheckJob: Job? = null

    fun onEmailChanged(email: String) { _emailInput.value = email }
    fun onPasswordChanged(pass: String) { _passwordInput.value = pass }

    fun onUsernameChanged(username: String) {
        _usernameInput.value = username
        availabilityCheckJob?.cancel()
        val trimmed = username.trim()
        if (trimmed.isBlank()) {
            _displayNameStatus.value = null
            _isCheckingDisplayName.value = false
            return
        }
        if (trimmed.length < 2) {
            _displayNameStatus.value = DisplayNameAvailability(
                query = trimmed,
                isAvailable = false,
                errorMessage = "Display name must be at least 2 characters."
            )
            _isCheckingDisplayName.value = false
            return
        }
        _isCheckingDisplayName.value = true
        availabilityCheckJob = viewModelScope.launch {
            delay(350L)
            val result = authRepository.checkDisplayNameAvailability(trimmed)
            if (_usernameInput.value.trim() == trimmed) {
                _displayNameStatus.value = result
                _isCheckingDisplayName.value = false
            }
        }
    }

    fun selectSuggestedDisplayName(suggestion: String) {
        availabilityCheckJob?.cancel()
        _usernameInput.value = suggestion
        _isCheckingDisplayName.value = false
        _displayNameStatus.value = DisplayNameAvailability(
            query = suggestion,
            isAvailable = true
        )
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = authRepository.signInWithGoogle()
            _isBusy.value = false
            res.fold(
                onSuccess = { _eventChannel.send(AuthUiEvent.ShowMessage("Signed in with Google account")) },
                onFailure = { _eventChannel.send(AuthUiEvent.ShowMessage(it.message ?: "Google Sign-In failed")) }
            )
        }
    }

    fun signInWithEmail() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = authRepository.signInWithEmail(_emailInput.value, _passwordInput.value)
            _isBusy.value = false
            res.fold(
                onSuccess = { _eventChannel.send(AuthUiEvent.ShowMessage("Signed in as ${it.user?.email}")) },
                onFailure = { _eventChannel.send(AuthUiEvent.ShowMessage(it.message ?: "Sign-in failed")) }
            )
        }
    }

    fun signUpWithEmail() {
        val cleanName = _usernameInput.value.trim()
        val cleanEmail = _emailInput.value.trim()
        if (cleanName.length < 2) {
            viewModelScope.launch {
                _eventChannel.send(AuthUiEvent.ShowMessage("Choose a display name (at least 2 characters)."))
            }
            return
        }
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            viewModelScope.launch {
                _eventChannel.send(AuthUiEvent.ShowMessage("Enter a valid email address."))
            }
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val availability = authRepository.checkDisplayNameAvailability(cleanName)
            _displayNameStatus.value = availability
            if (!availability.isAvailable) {
                _isBusy.value = false
                _eventChannel.send(
                    AuthUiEvent.ShowMessage(
                        availability.errorMessage ?: "'$cleanName' is already taken — pick one of the 3 free suggestions below!"
                    )
                )
                return@launch
            }

            val res = authRepository.signUpWithEmail(cleanEmail, _passwordInput.value, cleanName)
            _isBusy.value = false
            res.fold(
                onSuccess = { _eventChannel.send(AuthUiEvent.ShowMessage("Account created! +50 Starter Karma added")) },
                onFailure = { err ->
                    val raw = err.message.orEmpty()
                    val friendly = when {
                        raw.contains("already registered", ignoreCase = true) ||
                            raw.contains("user_already_exists", ignoreCase = true) ||
                            raw.contains("profiles_email_lower_unique_idx", ignoreCase = true) ->
                            "That email is already registered — switch to Sign In instead."
                        raw.contains("profiles_username_lower_unique_idx", ignoreCase = true) ->
                            "That display name was just taken — pick one of the suggestions."
                        else -> raw.ifBlank { "Sign-up failed" }
                    }
                    _eventChannel.send(AuthUiEvent.ShowMessage(friendly))
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _eventChannel.send(AuthUiEvent.ShowMessage("Signed out"))
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = authRepository.deleteAccount()
            _isBusy.value = false
            res.fold(
                onSuccess = {
                    _emailInput.value = ""
                    _passwordInput.value = ""
                    _usernameInput.value = ""
                    _displayNameStatus.value = null
                    _eventChannel.send(AuthUiEvent.ShowMessage("Your account and all associated data have been permanently deleted."))
                },
                onFailure = {
                    _eventChannel.send(AuthUiEvent.ShowMessage("Could not delete account: ${it.message ?: "unknown error"}"))
                }
            )
        }
    }
}
