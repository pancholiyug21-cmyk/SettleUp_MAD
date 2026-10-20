package com.example.splitwsie.presentation.settle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.presentation.components.SettleStatusBadge
import com.example.splitwsie.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Real settlement history: actual payments, with their true status.
 *
 * The previous "payment history" in the profile listed expenses and labelled every one
 * "Verified", which described transactions that had never happened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettleUpViewModel = hiltViewModel()
) {
    val state by viewModel.historyState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Scaffold(
        containerColor = SettleBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Payment history",
                        style = MaterialTheme.typography.titleMedium,
                        color = SettleTextPrimary
                    )
                },
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
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = SettlePrimary) }

            state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No payments yet.\nSettlements you make or receive will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettleTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.items, key = { it.settlement.id }) { item ->
                    HistoryRow(item = item, formattedDate = dateFormat.format(item.settlement.date))
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: SettlementHistoryItem, formattedDate: String) {
    val settlement = item.settlement
    val (statusColor, statusContainer) = when (settlement.status) {
        SettlementStatus.VERIFIED -> SettlePositive to SettlePositiveContainer
        SettlementStatus.PENDING_VERIFICATION, SettlementStatus.INITIATED,
        SettlementStatus.ORDER_CREATED, SettlementStatus.CHECKOUT_OPENED,
        SettlementStatus.CLIENT_SUCCESS, SettlementStatus.VERIFYING,
        SettlementStatus.PAYMENT_VERIFIED ->
            SettlePending to SettlePendingContainer
        SettlementStatus.FAILED, SettlementStatus.CANCELLED,
        SettlementStatus.CLIENT_FAILURE ->
            SettleNegative to SettleNegativeContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SettleSurface,
        border = BorderStroke(1.dp, SettleDivider)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(SettleSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (item.outgoing) Icons.AutoMirrored.Filled.CallMade
                        else Icons.AutoMirrored.Filled.CallReceived,
                        contentDescription = null,
                        tint = if (item.outgoing) SettleNegative else SettlePositive,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (item.outgoing) "You paid ${item.counterpartyName}"
                        else "${item.counterpartyName} paid you",
                        style = MaterialTheme.typography.titleSmall,
                        color = SettleTextPrimary
                    )
                    Text(
                        "${item.groupName} · ${settlement.method.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SettleTextSecondary
                    )
                }
                Text(
                    Money.format(settlement.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = SettleTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettleStatusBadge(
                    text = settlement.status.name.replace('_', ' '),
                    statusColor = statusColor,
                    containerColor = statusContainer
                )
                Text(formattedDate, style = MaterialTheme.typography.labelSmall, color = SettleTextSecondary)
            }

            settlement.providerTxnId?.let { txnId ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Txn $txnId",
                    style = MaterialTheme.typography.labelSmall,
                    color = SettleTextSecondary
                )
            }

            // The verification note is shown verbatim so a device-reported confirmation is
            // never mistaken for a bank-verified one.
            settlement.verificationNotes?.let { notes ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = SettleTextSecondary
                )
            }
        }
    }
}
