package dev.buildgate.app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.buildgate.app.entities.auth.model.AuthSession
import dev.buildgate.app.entities.auth.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _eventChannel = Channel<AuthUiEvent>()
    val eventFlow = _eventChannel.receiveAsFlow()

    private val _emailInput = MutableStateFlow("alex@playdeveloper.com")
    val emailInput = _emailInput.asStateFlow()

    private val _passwordInput = MutableStateFlow("password123")
    val passwordInput = _passwordInput.asStateFlow()

    fun onEmailChanged(email: String) { _emailInput.value = email }
    fun onPasswordChanged(pass: String) { _passwordInput.value = pass }

    fun signIn() {
        viewModelScope.launch {
            authRepository.signInWithEmail(_emailInput.value, _passwordInput.value)
            _eventChannel.send(AuthUiEvent.ShowMessage("Signed in successfully"))
        }
    }

    fun switchRole(role: String) {
        viewModelScope.launch {
            authRepository.switchUserRole(role)
            _eventChannel.send(AuthUiEvent.ShowMessage("Role switched to $role"))
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _eventChannel.send(AuthUiEvent.ShowMessage("Signed out"))
        }
    }
}
