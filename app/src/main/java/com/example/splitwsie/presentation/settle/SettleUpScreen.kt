package com.example.splitwsie.presentation.settle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.presentation.theme.*

/**
 * Lists every debt the signed-in user owes, across all groups, as the entry point for paying.
 *
 * This replaces the previous "Pay" action, which navigated to a payment screen with
 * placeholder ids and a zero amount and could never complete a payment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    onNavigateBack: () -> Unit,
    onSelectDebt: (groupId: String, toUserId: String) -> Unit,
    viewModel: SettleUpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = SettleBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settle up", style = MaterialTheme.typography.titleMedium, color = SettleTextPrimary) },
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
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = SettlePrimary) }

            uiState.error != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    uiState.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettleNegative,
                    textAlign = TextAlign.Center
                )
            }

            uiState.debts.isEmpty() -> AllSettledState(modifier = Modifier.padding(padding))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "You owe ${Money.format(uiState.totalOwed)} in total",
                        style = MaterialTheme.typography.titleLarge,
                        color = SettleTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Choose someone to pay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SettleTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(uiState.debts, key = { "${it.groupId}:${it.toUserId}" }) { debt ->
                    DebtRow(debt = debt, onClick = { onSelectDebt(debt.groupId, debt.toUserId) })
                }
            }
        }
    }
}

@Composable
private fun DebtRow(debt: PayableDebt, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                    debt.toUserName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = SettlePrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(debt.toUserName, style = MaterialTheme.typography.titleSmall, color = SettleTextPrimary)
                Text(
                    debt.groupName + if (debt.toUserUpiId.isNullOrBlank()) " · no UPI ID" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettleTextSecondary
                )
            }
            Text(
                Money.format(debt.amount),
                style = MaterialTheme.typography.titleMedium,
                color = SettleNegative,
                fontWeight = FontWeight.SemiBold
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SettleTextSecondary)
        }
    }
}

@Composable
private fun AllSettledState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(SettlePositiveContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SettlePositive,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("You are all settled up", style = MaterialTheme.typography.titleLarge, color = SettleTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You do not owe anyone right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = SettleTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
