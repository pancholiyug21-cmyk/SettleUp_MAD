package com.example.splitwsie.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitwsie.data.local.SessionManager
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.payment.PaymentVerifier
import com.example.splitwsie.domain.payment.UpiPayment
import com.example.splitwsie.domain.repository.SettlementRepository
import com.example.splitwsie.domain.repository.UserRepository
import com.example.splitwsie.domain.repository.AuthRepository
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.domain.util.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Headline numbers about the user's real settlements. */
data class PaymentStats(
    val totalPaid: Double = 0.0,
    val totalReceived: Double = 0.0,
    val completedCount: Int = 0,
    val pendingCount: Int = 0
)

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val stats: PaymentStats = PaymentStats(),
    val supportedCurrencies: List<String> = listOf("INR"),
    /** Describes how strongly payments are verified, shown in the security section. */
    val verificationAssurance: String = "",
    val isServerVerified: Boolean = false,
    val error: String? = null
)

sealed class ProfileEvent {
    data class Message(val text: String) : ProfileEvent()
    data class Error(val text: String) : ProfileEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val settlementRepository: SettlementRepository,
    private val authRepository: AuthRepository,
    private val paymentVerifier: PaymentVerifier,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    init {
        observeProfile()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            val currentUser = runCatching { userRepository.getCurrentUser() }.getOrNull()
            if (currentUser == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Your session has expired. Please sign in again.")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    user = currentUser,
                    verificationAssurance = paymentVerifier.assuranceDescription,
                    isServerVerified = paymentVerifier.isServerVerified
                )
            }

            // Stats come from settlements, not expenses. The previous screen listed expenses
            // under "payment history" and stamped every row "Verified", describing payments
            // that had never been made.
            settlementRepository.observeForUser(currentUser.id).collect { settlements ->
                val verified = settlements.filter { it.status == SettlementStatus.VERIFIED }
                _uiState.update {
                    it.copy(
                        stats = PaymentStats(
                            totalPaid = Money.round(
                                verified.filter { s -> s.fromUserId == currentUser.id }
                                    .sumOf { s -> s.amount }
                            ),
                            totalReceived = Money.round(
                                verified.filter { s -> s.toUserId == currentUser.id }
                                    .sumOf { s -> s.amount }
                            ),
                            completedCount = verified.size,
                            pendingCount = settlements.count { s -> s.status.isInFlight }
                        )
                    )
                }
            }
        }
    }

    /**
     * Saves profile edits.
     *
     * Only the last four digits of a bank account are kept: the app never needs the full
     * number (payments go out over UPI), and storing it would put a complete account number in
     * an on-device database for no functional benefit.
     */
    fun updateUserProfile(
        name: String,
        email: String,
        phone: String?,
        upiId: String?,
        bankAccountLast4: String?,
        currency: String
    ) {
        val currentUser = _uiState.value.user ?: return

        val nameError = Validators.validateName(name)
        val emailError = Validators.validateEmail(email)
        val phoneError = Validators.validateOptionalPhone(phone.orEmpty())
        val upiError = if (upiId.isNullOrBlank() || UpiPayment.isValidVpa(upiId)) {
            null
        } else {
            "Enter a valid UPI ID, e.g. name@bank"
        }
        val bankError = when {
            bankAccountLast4.isNullOrBlank() -> null
            bankAccountLast4.length != 4 || bankAccountLast4.any { !it.isDigit() } ->
                "Enter the last 4 digits of your account number"
            else -> null
        }

        val firstError = nameError ?: emailError ?: phoneError ?: upiError ?: bankError
        if (firstError != null) {
            viewModelScope.launch { _events.emit(ProfileEvent.Error(firstError)) }
            return
        }

        viewModelScope.launch {
            try {
                val normalisedEmail = email.trim().lowercase()
                if (normalisedEmail != currentUser.email.lowercase()) {
                    val clash = userRepository.findByEmail(normalisedEmail)
                    if (clash != null && clash.id != currentUser.id) {
                        _events.emit(
                            ProfileEvent.Error("Another profile on this device already uses that email.")
                        )
                        return@launch
                    }
                }

                val updated = currentUser.copy(
                    name = name.trim(),
                    email = normalisedEmail,
                    phoneNumber = phone?.trim()?.takeIf { it.isNotBlank() },
                    upiId = upiId?.trim()?.takeIf { it.isNotBlank() },
                    bankAccount = bankAccountLast4?.takeIf { it.isNotBlank() },
                    defaultCurrency = currency
                )
                userRepository.updateUser(updated)
                _uiState.update { it.copy(user = updated) }
                _events.emit(ProfileEvent.Message("Profile updated"))
            } catch (e: Exception) {
                _events.emit(ProfileEvent.Error("Could not save your profile: ${e.message ?: "unknown error"}"))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            sessionManager.clearSession()
        }
    }
}
