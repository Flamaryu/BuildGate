package dev.buildgate.app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.auth.model.AuthSession
import dev.buildgate.app.entities.auth.repository.AuthRepository
import dev.buildgate.app.entities.testerprofile.model.KarmaEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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

    private val _emailInput = MutableStateFlow("alex.dev@gmail.com")
    val emailInput = _emailInput.asStateFlow()

    private val _usernameInput = MutableStateFlow("AlexDeveloper")
    val usernameInput = _usernameInput.asStateFlow()

    private val _passwordInput = MutableStateFlow("password123")
    val passwordInput = _passwordInput.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    fun onEmailChanged(email: String) { _emailInput.value = email }
    fun onUsernameChanged(username: String) { _usernameInput.value = username }
    fun onPasswordChanged(pass: String) { _passwordInput.value = pass }

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
        viewModelScope.launch {
            _isBusy.value = true
            val res = authRepository.signUpWithEmail(_emailInput.value, _passwordInput.value, _usernameInput.value)
            _isBusy.value = false
            res.fold(
                onSuccess = { _eventChannel.send(AuthUiEvent.ShowMessage("Account created! +50 Starter Karma added")) },
                onFailure = { _eventChannel.send(AuthUiEvent.ShowMessage(it.message ?: "Sign-up failed")) }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _eventChannel.send(AuthUiEvent.ShowMessage("Signed out"))
        }
    }
}
