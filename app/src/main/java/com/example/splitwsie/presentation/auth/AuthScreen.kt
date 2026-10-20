package com.example.splitwsie.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.presentation.components.SettlePrimaryButton
import com.example.splitwsie.presentation.theme.*

/**
 * Onboarding.
 *
 * This app stores everything on the device, so this is device-local profile setup, not
 * authentication against a server -- the copy says so rather than implying accounts are
 * protected by a credential they are not.
 */
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SettlePrimary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(72.dp))
        Text(
            text = "SETTLE",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Track it. Pay it. Settle it.",
            style = MaterialTheme.typography.bodyLarge,
            color = SettleTextSecondary
        )
        Spacer(modifier = Modifier.height(40.dp))

        if (uiState.showCreateForm) {
            CreateProfileForm(
                state = uiState,
                onNameChanged = viewModel::onNameChanged,
                onEmailChanged = viewModel::onEmailChanged,
                onPhoneChanged = viewModel::onPhoneChanged,
                onUpiChanged = viewModel::onUpiChanged,
                onSubmit = { viewModel.createProfile(onAuthSuccess) },
                onBackToPicker = viewModel::showProfilePicker.takeIf {
                    uiState.existingProfiles.isNotEmpty()
                }
            )
        } else {
            ProfilePicker(
                profiles = uiState.existingProfiles,
                isSubmitting = uiState.isSubmitting,
                onSelect = { viewModel.signInAs(it, onAuthSuccess) },
                onCreateNew = viewModel::showCreateForm
            )
        }

        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium, color = SettleNegative)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Your profile, groups and payment records are stored only on this device.",
            style = MaterialTheme.typography.labelSmall,
            color = SettleTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProfilePicker(
    profiles: List<User>,
    isSubmitting: Boolean,
    onSelect: (User) -> Unit,
    onCreateNew: () -> Unit
) {
    Text(
        "Continue as",
        style = MaterialTheme.typography.titleMedium,
        color = SettleTextPrimary,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))

    profiles.forEach { profile ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable(enabled = !isSubmitting) { onSelect(profile) },
            shape = RoundedCornerShape(16.dp),
            color = SettleSurface,
            border = BorderStroke(1.dp, SettleDivider)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(SettleSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        profile.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = SettlePrimary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall, color = SettleTextPrimary)
                    Text(profile.email, style = MaterialTheme.typography.bodySmall, color = SettleTextSecondary)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SettleTextSecondary)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onCreateNew, enabled = !isSubmitting) {
        Text("Create a new profile", color = SettlePrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CreateProfileForm(
    state: AuthUiState,
    onNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onUpiChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackToPicker: (() -> Unit)?
) {
    Text(
        "Set up your profile",
        style = MaterialTheme.typography.titleMedium,
        color = SettleTextPrimary,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))

    ValidatedField(
        value = state.name,
        onValueChange = onNameChanged,
        label = "Full name",
        placeholder = "e.g. Priya Sharma",
        error = state.nameError,
        imeAction = ImeAction.Next
    )
    Spacer(modifier = Modifier.height(12.dp))

    ValidatedField(
        value = state.email,
        onValueChange = onEmailChanged,
        label = "Email",
        placeholder = "you@example.com",
        error = state.emailError,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next
    )
    Spacer(modifier = Modifier.height(12.dp))

    ValidatedField(
        value = state.phone,
        onValueChange = onPhoneChanged,
        label = "Phone (optional)",
        placeholder = "+919876543210",
        error = state.phoneError,
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Next
    )
    Spacer(modifier = Modifier.height(12.dp))

    ValidatedField(
        value = state.upiId,
        onValueChange = onUpiChanged,
        label = "UPI ID (optional)",
        placeholder = "name@bank",
        error = state.upiError,
        supporting = "Needed so others in your groups can pay you by UPI.",
        imeAction = ImeAction.Done
    )

    Spacer(modifier = Modifier.height(28.dp))
    SettlePrimaryButton(
        text = "Get Started",
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canSubmit,
        isLoading = state.isSubmitting
    )

    if (onBackToPicker != null) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onBackToPicker, enabled = !state.isSubmitting) {
            Text("Use an existing profile", color = SettleTextSecondary)
        }
    }
}

@Composable
private fun ValidatedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String?,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        supportingText = when {
            error != null -> {
                { Text(error, color = SettleNegative) }
            }
            supporting != null -> {
                { Text(supporting, color = SettleTextSecondary) }
            }
            else -> null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SettlePrimary,
            unfocusedBorderColor = SettleDivider,
            errorBorderColor = SettleNegative
        )
    )
}
