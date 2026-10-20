package com.example.splitwsie.presentation.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.presentation.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaymentHistory: () -> Unit,
    onLogoutSuccess: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is ProfileEvent.Message -> event.text
                is ProfileEvent.Error -> event.text
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = SettleSurface,
            title = { Text("Log out of SETTLE?", color = SettleTextPrimary) },
            text = {
                Text(
                    "Your groups, expenses and payment records stay on this device. " +
                        "You can sign back in as this profile at any time.",
                    color = SettleTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                        onLogoutSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SettleNegative)
                ) { Text("Log out", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = SettleTextSecondary)
                }
            }
        )
    }

    val user = uiState.user
    if (showEditProfileSheet && user != null) {
        ModalBottomSheet(
            onDismissRequest = { showEditProfileSheet = false },
            sheetState = sheetState,
            containerColor = SettleBackground
        ) {
            EditProfileContent(
                user = user,
                supportedCurrencies = uiState.supportedCurrencies,
                onSave = { name, email, phone, upi, bank, currency ->
                    viewModel.updateUserProfile(name, email, phone, upi, bank, currency)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showEditProfileSheet = false
                    }
                },
                onCancel = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showEditProfileSheet = false
                    }
                }
            )
        }
    }

    Scaffold(
        containerColor = SettleBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Profile & account", color = SettleTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SettleTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettleBackground)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = SettlePrimary) }
            return@Scaffold
        }

        if (user == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    uiState.error ?: "Profile unavailable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettleTextSecondary
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ProfileHeader(user = user, onEditClick = { showEditProfileSheet = true })
            }

            item { PaymentSummaryCard(stats = uiState.stats) }

            item {
                SectionHeader("PERSONAL INFORMATION")
                ProfileRow("Name", user.name) { showEditProfileSheet = true }
                ProfileRow("Email", user.email) { showEditProfileSheet = true }
                ProfileRow("Phone", user.phoneNumber ?: "Not added") { showEditProfileSheet = true }
                ProfileRow("Default currency", user.defaultCurrency) { showEditProfileSheet = true }
            }

            item {
                SectionHeader("PAYMENT & BANKING")
                ProfileRow(
                    label = "UPI ID",
                    value = user.upiId ?: "Not configured",
                    onClick = { showEditProfileSheet = true }
                )
                ProfileRow(
                    label = "Bank account",
                    // Only the last four digits are ever stored, so this is the whole value.
                    value = user.bankAccount?.let { "•••• $it" } ?: "Not added",
                    onClick = { showEditProfileSheet = true }
                )
                ProfileRow(
                    label = "Payment history",
                    value = "${uiState.stats.completedCount} completed",
                    onClick = onNavigateToPaymentHistory
                )
            }

            item {
                SectionHeader("PAYMENTS & SECURITY")
                InfoCard(
                    title = if (uiState.isServerVerified) {
                        "Payments are verified by the SETTLE server"
                    } else {
                        "Payments are confirmed on this device"
                    },
                    body = uiState.verificationAssurance
                )
                Spacer(modifier = Modifier.height(12.dp))
                InfoCard(
                    title = "Where your data lives",
                    body = "Your profile, groups, expenses and payment records are stored only " +
                        "on this device. Nothing is uploaded, and nothing syncs to other phones."
                )
                Spacer(modifier = Modifier.height(12.dp))
                InfoCard(
                    title = "How money moves",
                    body = "UPI payments open your own UPI app and transfer money directly to " +
                        "the other person. SETTLE never holds, routes or has access to your funds."
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SettleSurfaceVariant)
                        .clickable { showLogoutDialog = true }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Log out", color = SettleNegative, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: User, onEditClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(40.dp)).background(SettleSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.name.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = SettlePrimary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            user.name,
            style = MaterialTheme.typography.titleLarge,
            color = SettleTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = SettleTextSecondary)
        user.phoneNumber?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SettleTextSecondary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SettleSurface,
            border = BorderStroke(1.dp, SettleDivider),
            modifier = Modifier.clickable(onClick = onEditClick)
        ) {
            Text(
                text = "Edit profile",
                style = MaterialTheme.typography.labelMedium,
                color = SettleTextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PaymentSummaryCard(stats: PaymentStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SettleSurface,
        border = BorderStroke(1.dp, SettleDivider)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatColumn("PAID", Money.format(stats.totalPaid), SettleNegative, Modifier.weight(1f))
                StatColumn("RECEIVED", Money.format(stats.totalReceived), SettlePositive, Modifier.weight(1f))
                StatColumn("PAYMENTS", stats.completedCount.toString(), SettleTextPrimary, Modifier.weight(1f))
            }
            if (stats.pendingCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "${stats.pendingCount} payment${if (stats.pendingCount == 1) "" else "s"} " +
                        "still awaiting confirmation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SettlePending
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SettleTextSecondary, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SettleSurfaceVariant
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = SettleTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = SettleTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = SettleTextSecondary)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = SettleTextSecondary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )
}

@Composable
private fun ProfileRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 48dp minimum touch target.
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = SettleTextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = SettleTextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SettleTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    HorizontalDivider(color = SettleDivider)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileContent(
    user: User,
    supportedCurrencies: List<String>,
    onSave: (String, String, String?, String?, String?, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var email by remember { mutableStateOf(user.email) }
    var phone by remember { mutableStateOf(user.phoneNumber.orEmpty()) }
    var upi by remember { mutableStateOf(user.upiId.orEmpty()) }
    var bankLast4 by remember { mutableStateOf(user.bankAccount.orEmpty()) }
    var currency by remember { mutableStateOf(user.defaultCurrency) }
    var currencyExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "Edit profile",
            style = MaterialTheme.typography.titleLarge,
            color = SettleTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        EditField(name, { name = it.take(60) }, "Full name")
        Spacer(modifier = Modifier.height(16.dp))

        EditField(email, { email = it.trim().take(120) }, "Email address", KeyboardType.Email)
        Spacer(modifier = Modifier.height(16.dp))

        EditField(
            phone,
            { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) },
            "Phone number",
            KeyboardType.Phone
        )
        Spacer(modifier = Modifier.height(16.dp))

        EditField(
            upi,
            { upi = it.trim().take(120) },
            "UPI ID",
            supporting = "Others in your groups pay you at this address."
        )
        Spacer(modifier = Modifier.height(16.dp))

        EditField(
            bankLast4,
            { bankLast4 = it.filter(Char::isDigit).take(4) },
            "Bank account (last 4 digits)",
            KeyboardType.Number,
            supporting = "Only the last 4 digits are stored, so you can recognise the account."
        )
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = currencyExpanded,
            onExpandedChange = { currencyExpanded = !currencyExpanded }
        ) {
            OutlinedTextField(
                value = currency,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default currency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SettlePrimary,
                    unfocusedBorderColor = SettleDivider
                )
            )
            ExposedDropdownMenu(
                expanded = currencyExpanded,
                onDismissRequest = { currencyExpanded = false },
                modifier = Modifier.background(SettleSurface)
            ) {
                supportedCurrencies.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = SettleTextPrimary) },
                        onClick = {
                            currency = option
                            currencyExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SettleTextPrimary),
                border = BorderStroke(1.dp, SettleDivider)
            ) { Text("Cancel") }

            Button(
                onClick = {
                    onSave(
                        name,
                        email,
                        phone.takeIf { it.isNotBlank() },
                        upi.takeIf { it.isNotBlank() },
                        bankLast4.takeIf { it.isNotBlank() },
                        currency
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = name.isNotBlank() && email.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SettlePrimary)
            ) { Text("Save") }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = supporting?.let { { Text(it, color = SettleTextSecondary) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SettlePrimary,
            unfocusedBorderColor = SettleDivider
        )
    )
}
