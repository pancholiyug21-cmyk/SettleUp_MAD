package com.example.splitwsie.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.payment.UpiPayment
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.domain.repository.AuthRepository
import com.example.splitwsie.domain.util.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    /** Profiles already created on this device, offered for one-tap sign-in. */
    val existingProfiles: List<User> = emptyList(),
    val showCreateForm: Boolean = false,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val upiId: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val phoneError: String? = null,
    val upiError: String? = null,
    val error: String? = null
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() && email.isNotBlank() && !isSubmitting
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            try {
                val profiles = userRepository.getAllUsers()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        existingProfiles = profiles,
                        // With no profiles on the device there is nothing to choose between,
                        // so go straight to the form rather than showing an empty picker.
                        showCreateForm = profiles.isEmpty()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, showCreateForm = true, error = "Could not load profiles: ${e.message}")
                }
            }
        }
    }

    fun onNameChanged(value: String) =
        _uiState.update { it.copy(name = value.take(60), nameError = null, error = null) }

    fun onEmailChanged(value: String) =
        _uiState.update { it.copy(email = value.trim().take(120), emailError = null, error = null) }

    fun onPhoneChanged(value: String) =
        _uiState.update {
            it.copy(phone = value.filter { c -> c.isDigit() || c == '+' }.take(15), phoneError = null)
        }

    fun onUpiChanged(value: String) =
        _uiState.update { it.copy(upiId = value.trim().take(120), upiError = null) }

    fun showCreateForm() = _uiState.update { it.copy(showCreateForm = true, error = null) }

    fun showProfilePicker() = _uiState.update { it.copy(showCreateForm = false, error = null) }

    /** Signs in as an existing device profile using Firebase Auth. */
    fun signInAs(user: User, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val signInResult = authRepository.signIn(user.email, "password123")
                if (signInResult.isFailure) {
                    _uiState.update { it.copy(isSubmitting = false, error = "Could not sign in: ${signInResult.exceptionOrNull()?.message}") }
                    return@launch
                }
                userRepository.saveCurrentUser(user)
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = "An unexpected error occurred: ${e.message}") }
            }
        }
    }

    /**
     * Creates a new profile after validating every field.
     *
     * Email uniqueness is enforced here because two profiles sharing an email would make the
     * "who am I in this group" question ambiguous everywhere else in the app.
     */
    fun createProfile(onSuccess: () -> Unit) {
        val state = _uiState.value
        val nameError = Validators.validateName(state.name)
        val emailError = Validators.validateEmail(state.email)
        val phoneError = Validators.validateOptionalPhone(state.phone)
        val upiError = if (state.upiId.isBlank() || UpiPayment.isValidVpa(state.upiId)) {
            null
        } else {
            "Enter a valid UPI ID, e.g. name@bank"
        }

        if (nameError != null || emailError != null || phoneError != null || upiError != null) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    emailError = emailError,
                    phoneError = phoneError,
                    upiError = upiError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val normalisedEmail = state.email.trim().lowercase()
                val duplicate = userRepository.getAllUsers()
                    .firstOrNull { it.email.trim().lowercase() == normalisedEmail }
                if (duplicate != null) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            emailError = "A profile with this email already exists on this device."
                        )
                    }
                    return@launch
                }

                val signUpResult = authRepository.signUp(normalisedEmail, "password123") // Dummy password for now, since UI doesn't have password field yet
                if (signUpResult.isFailure) {
                    _uiState.update {
                        it.copy(isSubmitting = false, error = "Could not authenticate: ${signUpResult.exceptionOrNull()?.message}")
                    }
                    return@launch
                }
                
                val uid = signUpResult.getOrNull() ?: UUID.randomUUID().toString()

                val user = User(
                    id = uid,
                    name = state.name.trim(),
                    email = normalisedEmail,
                    phoneNumber = state.phone.takeIf { it.isNotBlank() },
                    upiId = state.upiId.trim().takeIf { it.isNotBlank() }
                )
                userRepository.saveCurrentUser(user)
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, error = "Could not create your profile: ${e.message}")
                }
            }
        }
    }
}
