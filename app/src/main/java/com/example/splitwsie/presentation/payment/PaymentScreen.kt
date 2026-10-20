package com.example.splitwsie.presentation.payment

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.model.PaymentMethod
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.presentation.components.SettlePrimaryButton
import com.example.splitwsie.presentation.components.SettleSecondaryButton
import com.example.splitwsie.presentation.components.SettleSegmentedControl
import com.example.splitwsie.presentation.theme.*
import com.example.splitwsie.domain.util.Money
import java.text.SimpleDateFormat
import java.util.Locale

private const val UPI_RESPONSE_EXTRA = "response"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    groupId: String,
    toUserId: String,
    expenseId: String? = null,
    onNavigateBack: () -> Unit,
    onPaymentSuccess: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Held here (not in the ViewModel) because it identifies the launch we are awaiting a
    // result for; the ViewModel already persisted the settlement itself.
    var pendingSettlementId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val settlementId = pendingSettlementId
        if (settlementId != null) {
            pendingSettlementId = null
            viewModel.onUpiResult(
                settlementId = settlementId,
                resultOk = activityResult.resultCode == Activity.RESULT_OK,
                response = activityResult.data?.getStringExtra(UPI_RESPONSE_EXTRA)
            )
        }
    }

    val razorpayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val settlementId = pendingSettlementId
        if (settlementId != null) {
            pendingSettlementId = null
            val success = activityResult.data?.getBooleanExtra(RazorpayCheckoutActivity.RESULT_SUCCESS, false) ?: false
            val paymentId = activityResult.data?.getStringExtra(RazorpayCheckoutActivity.RESULT_PAYMENT_ID)
            val signature = activityResult.data?.getStringExtra(RazorpayCheckoutActivity.RESULT_SIGNATURE)
            val errorMsg = activityResult.data?.getStringExtra(RazorpayCheckoutActivity.RESULT_ERROR_MSG)

            viewModel.onRazorpayResult(settlementId, success, paymentId, signature, errorMsg)
        }
    }

    LaunchedEffect(groupId, toUserId, expenseId) {
        viewModel.load(groupId, toUserId, expenseId)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PaymentEffect.LaunchUpiApp -> {
                    pendingSettlementId = effect.settlementId
                    try {
                        upiLauncher.launch(effect.intent)
                    } catch (e: ActivityNotFoundException) {
                        pendingSettlementId = null
                        viewModel.onUpiUnavailable(effect.settlementId)
                    }
                }
                is PaymentEffect.LaunchRazorpay -> {
                    pendingSettlementId = effect.settlementId
                    val intent = android.content.Intent(context, RazorpayCheckoutActivity::class.java).apply {
                        putExtra(RazorpayCheckoutActivity.EXTRA_ORDER_ID, effect.orderId)
                        putExtra(RazorpayCheckoutActivity.EXTRA_KEY_ID, effect.keyId)
                        putExtra(RazorpayCheckoutActivity.EXTRA_AMOUNT, effect.amount)
                        putExtra(RazorpayCheckoutActivity.EXTRA_CURRENCY, effect.currency)
                    }
                    razorpayLauncher.launch(intent)
                }
                is PaymentEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                PaymentEffect.Settled -> Unit
            }
        }
    }

    // The back affordance is hidden only while an external app owns the screen; every other
    // state stays escapable so the user is never trapped.
    val canNavigateBack = uiState.phase != PaymentPhase.AWAITING_PROVIDER

    Scaffold(
        containerColor = SettleBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = SettleTextPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettleBackground)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            when (uiState.phase) {
                PaymentPhase.LOADING -> LoadingState()

                PaymentPhase.REVIEW -> ReviewState(
                    state = uiState,
                    onAmountChanged = viewModel::onAmountChanged,
                    onMethodSelected = viewModel::onMethodSelected,
                    onReferenceChanged = viewModel::onManualReferenceChanged,
                    onPayFull = viewModel::payFullAmount,
                    onSubmit = viewModel::submit,
                    onResolveUnresolved = viewModel::resolveUnresolved
                )

                PaymentPhase.AWAITING_PROVIDER -> AwaitingProviderState()

                PaymentPhase.CREATING_ORDER -> ProcessingState(
                    title = "Preparing payment...",
                    subtitle = "Securely generating order ID"
                )

                PaymentPhase.CHECKOUT_OPENED -> ProcessingState(
                    title = "Opening secure payment...",
                    subtitle = "Complete the payment in the Razorpay window"
                )

                PaymentPhase.VERIFYING -> ProcessingState(
                    title = "Verifying payment...",
                    subtitle = "Securely confirming payment with the server"
                )

                PaymentPhase.SUCCESS -> OutcomeState(
                    icon = { Icon(Icons.Default.Check, null, tint = SettlePositive, modifier = Modifier.size(40.dp)) },
                    containerColor = SettlePositiveContainer,
                    title = "Payment recorded",
                    subtitle = "${Money.format(uiState.settlement?.amount ?: uiState.amount)} to ${uiState.payeeName}",
                    state = uiState,
                    primaryLabel = "Done",
                    onPrimary = onPaymentSuccess,
                    secondaryLabel = null,
                    onSecondary = null
                )

                PaymentPhase.PENDING -> OutcomeState(
                    icon = {
                        Icon(
                            Icons.Default.HourglassEmpty,
                            null,
                            tint = SettlePending,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    containerColor = SettlePendingContainer,
                    title = "Payment submitted",
                    subtitle = "Your bank is still processing this. The balance will not change " +
                        "until it is confirmed.",
                    state = uiState,
                    primaryLabel = "Done",
                    onPrimary = onPaymentSuccess,
                    secondaryLabel = null,
                    onSecondary = null
                )

                PaymentPhase.FAILED -> OutcomeState(
                    icon = { Icon(Icons.Default.Close, null, tint = SettleNegative, modifier = Modifier.size(40.dp)) },
                    containerColor = SettleNegativeContainer,
                    title = if (uiState.settlement?.status == SettlementStatus.CANCELLED) {
                        "Payment cancelled"
                    } else {
                        "Payment failed"
                    },
                    subtitle = uiState.statusNote ?: "The payment did not go through. Nothing was charged.",
                    state = uiState,
                    primaryLabel = "Try again",
                    onPrimary = viewModel::retry,
                    secondaryLabel = "Back",
                    onSecondary = onNavigateBack
                )

                PaymentPhase.BLOCKED -> BlockedState(
                    message = uiState.error ?: "This payment is not available.",
                    onBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SettlePrimary)
    }
}

@Composable
private fun AwaitingProviderState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = SettlePrimary, strokeWidth = 3.dp, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(32.dp))
        Text("Waiting for your UPI app", style = MaterialTheme.typography.titleLarge, color = SettleTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Complete the payment in the app that just opened. " +
                "You will come back here automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = SettleTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BlockedState(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(SettleSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, null, tint = SettlePositive, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = SettleTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        SettlePrimaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReviewState(
    state: PaymentUiState,
    onAmountChanged: (String) -> Unit,
    onMethodSelected: (PaymentMethod) -> Unit,
    onReferenceChanged: (String) -> Unit,
    onPayFull: () -> Unit,
    onSubmit: () -> Unit,
    onResolveUnresolved: (String, Boolean) -> Unit
) {
    val unresolved = state.unresolvedSettlement
    if (unresolved != null) {
        UnresolvedPaymentDialog(
            amount = unresolved.amount,
            payeeName = state.payeeName,
            onConfirmPaid = { onResolveUnresolved(unresolved.id, true) },
            onDiscard = { onResolveUnresolved(unresolved.id, false) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(SettleSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                state.payeeName.take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.headlineMedium,
                color = SettlePrimary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Paying ${state.payeeName}", style = MaterialTheme.typography.bodyLarge, color = SettleTextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "You owe ${Money.format(state.outstanding)}",
            style = MaterialTheme.typography.bodySmall,
            color = SettleTextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = state.amountInput,
            onValueChange = onAmountChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.displayMedium.copy(textAlign = TextAlign.Center),
            prefix = { Text("₹", style = MaterialTheme.typography.headlineSmall, color = SettleTextPrimary) },
            isError = state.amountInput.isNotBlank() && !state.isAmountValid,
            supportingText = {
                if (state.amountInput.isNotBlank() && !state.isAmountValid) {
                    Text("Enter an amount up to ${Money.format(state.outstanding)}", color = SettleNegative)
                } else {
                    Text("Pay any amount up to what you owe", color = SettleTextSecondary)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SettlePrimary,
                unfocusedBorderColor = SettleDivider,
                focusedContainerColor = SettleSurface,
                unfocusedContainerColor = SettleSurface
            ),
            shape = RoundedCornerShape(16.dp)
        )

        if (Money.toPaise(state.amount) != Money.toPaise(state.outstanding)) {
            TextButton(onClick = onPayFull) {
                Text("Pay the full ${Money.format(state.outstanding)}", color = SettlePrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "How are you paying?",
            style = MaterialTheme.typography.labelLarge,
            color = SettleTextSecondary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettleSegmentedControl(
            options = state.availableMethods,
            selectedOption = state.selectedMethod,
            onOptionSelected = onMethodSelected,
            optionLabel = { it.displayName }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SettleSurface,
            border = BorderStroke(1.dp, SettleDivider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (state.selectedMethod) {
                    PaymentMethod.UPI_INTENT -> UpiMethodDetails(state)
                    PaymentMethod.CASH -> MethodNote(
                        "Records that ${state.payeeName} was paid in cash. " +
                            "No money moves through the app."
                    )
                    PaymentMethod.BANK_TRANSFER -> MethodNote(
                        "Records a transfer you made yourself in your banking app. " +
                            "No money moves through the app."
                    )
                    PaymentMethod.SANDBOX -> MethodNote(
                        "Simulated payment for testing. No money is transferred."
                    )
                    PaymentMethod.RAZORPAY -> MethodNote(
                        "Secure checkout powered by Razorpay. Complete payment in app."
                    )
                }
            }
        }

        if (state.selectedMethod == PaymentMethod.CASH ||
            state.selectedMethod == PaymentMethod.BANK_TRANSFER
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.manualReference,
                onValueChange = onReferenceChanged,
                label = { Text("Reference (optional)") },
                placeholder = { Text("e.g. UTR or a short note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SettlePrimary,
                    unfocusedBorderColor = SettleDivider
                )
            )
        }

        if (state.relatedExpenses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "What this covers",
                style = MaterialTheme.typography.labelLarge,
                color = SettleTextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            state.relatedExpenses.take(10).forEach { expense ->
                val myShare = expense.splits
                    .filter { it.userId != expense.paidById }
                    .sumOf { it.amountOwed }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(expense.title, style = MaterialTheme.typography.bodyMedium, color = SettleTextSecondary)
                    Text(
                        Money.format(
                            expense.splits.firstOrNull { it.userId != expense.paidById }?.amountOwed ?: myShare
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SettleTextPrimary
                    )
                }
            }
        }

        state.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SettleNegativeContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettleNegative,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val payLabel = when (state.selectedMethod) {
            PaymentMethod.UPI_INTENT -> "Pay ${Money.format(state.amount)}"
            PaymentMethod.SANDBOX -> "Simulate ${Money.format(state.amount)}"
            else -> "Record ${Money.format(state.amount)} as paid"
        }

        SettlePrimaryButton(
            text = payLabel,
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isAmountValid &&
                unresolved == null &&
                (state.selectedMethod != PaymentMethod.UPI_INTENT || state.canUseUpi),
            isLoading = state.isSubmitting
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun UpiMethodDetails(state: PaymentUiState) {
    if (state.canUseUpi) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Paying to", style = MaterialTheme.typography.bodyMedium, color = SettleTextSecondary)
            Text(
                state.payeeVpa.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = SettleTextPrimary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        MethodNote(
            "Opens your UPI app to transfer the money directly to ${state.payeeName}. " +
                "SETTLE does not hold or process the funds."
        )
        if (state.isDeviceReportedOnly) {
            Spacer(modifier = Modifier.height(8.dp))
            MethodNote(
                "The result is reported by your UPI app and is not independently verified " +
                    "against the bank."
            )
        }
    } else {
        MethodNote(
            "${state.payeeName} has not added a UPI ID yet, so UPI is unavailable. " +
                "Choose cash or bank transfer to record a payment you made another way."
        )
    }
}

@Composable
private fun MethodNote(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = SettleTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = SettleTextSecondary)
    }
}

@Composable
private fun OutcomeState(
    icon: @Composable () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    state: PaymentUiState,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(containerColor),
            contentAlignment = Alignment.Center
        ) { icon() }

        Spacer(modifier = Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = SettleTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = SettleTextSecondary,
            textAlign = TextAlign.Center
        )

        val settlement = state.settlement
        if (settlement != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SettleSurface,
                border = BorderStroke(1.dp, SettleDivider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    ReceiptRow("Amount", Money.format(settlement.amount))
                    ReceiptRow("Method", settlement.method.displayName)
                    ReceiptRow(
                        "Status",
                        settlement.status.name.lowercase().replace('_', ' ')
                            .replaceFirstChar { it.uppercase() }
                    )
                    settlement.providerTxnId?.let { ReceiptRow("Transaction", it) }
                    settlement.providerRefId?.let { ReceiptRow("Reference", it) }
                    ReceiptRow("Time", dateFormat.format(settlement.updatedAt))
                }
            }

            // The assurance level is stated on the receipt itself rather than only in a
            // settings screen, so nobody reads "Payment recorded" as a bank confirmation.
            state.statusNote?.let { note ->
                Spacer(modifier = Modifier.height(12.dp))
                MethodNote(note)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        SettlePrimaryButton(text = primaryLabel, onClick = onPrimary, modifier = Modifier.fillMaxWidth())

        if (secondaryLabel != null && onSecondary != null) {
            Spacer(modifier = Modifier.height(12.dp))
            SettleSecondaryButton(
                text = secondaryLabel,
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SettleTextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = SettleTextPrimary,
            textAlign = TextAlign.End
        )
    }
}

/**
 * Shown when a previous payment was interrupted (app killed, callback never delivered).
 * The user is the only one who knows whether the money left their account, so we ask rather
 * than guessing -- guessing either loses a real payment or wrongly clears a real debt.
 */
@Composable
private fun UnresolvedPaymentDialog(
    amount: Double,
    payeeName: String,
    onConfirmPaid: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        containerColor = SettleSurface,
        title = { Text("Unfinished payment", color = SettleTextPrimary) },
        text = {
            Text(
                "A payment of ${Money.format(amount)} to $payeeName was started but never " +
                    "confirmed. Check your bank or UPI app, then tell us what happened.",
                color = SettleTextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmPaid,
                colors = ButtonDefaults.buttonColors(containerColor = SettlePrimary)
            ) {
                Text("It went through", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("It did not", color = SettleTextSecondary)
            }
        }
    )
}

@Composable
private fun ProcessingState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = SettlePrimary)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = SettleTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = SettleTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
