package com.example.splitwsie.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.presentation.components.BarChart
import com.example.splitwsie.presentation.components.ChartColors
import com.example.splitwsie.presentation.components.DonutChart
import com.example.splitwsie.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = SettleBackground,
        topBar = {
            TopAppBar(
                title = { Text("Analytics", fontWeight = FontWeight.SemiBold, color = SettleTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SettleTextPrimary)
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
            ) {
                CircularProgressIndicator(color = SettlePrimary)
            }
            return@Scaffold
        }

        if (uiState.totalSpentAllTime == 0.0) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "You haven't been part of any expenses yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettleTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                AnalyticsHeader(
                    totalSpentThisMonth = uiState.totalSpentThisMonth,
                    totalSpentAllTime = uiState.totalSpentAllTime
                )
            }

            item {
                if (uiState.spendingByCategory.isNotEmpty()) {
                    CategorySpendingSection(categories = uiState.spendingByCategory)
                }
            }

            item {
                if (uiState.spendingByMonth.isNotEmpty()) {
                    MonthlyTrendSection(months = uiState.spendingByMonth)
                }
            }
        }
    }
}

@Composable
private fun AnalyticsHeader(totalSpentThisMonth: Double, totalSpentAllTime: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SettleSurface,
        border = BorderStroke(1.dp, SettleDivider)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "SPENT THIS MONTH",
                style = MaterialTheme.typography.labelMedium,
                color = SettleTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Money.format(totalSpentThisMonth),
                style = MaterialTheme.typography.displayMedium,
                color = SettleTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SettleDivider)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total All-Time Spending", style = MaterialTheme.typography.bodyMedium, color = SettleTextSecondary)
                Text(
                    Money.format(totalSpentAllTime),
                    style = MaterialTheme.typography.titleMedium,
                    color = SettleTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CategorySpendingSection(categories: List<CategorySpending>) {
    Text(
        "Spending by Category",
        style = MaterialTheme.typography.titleMedium,
        color = SettleTextPrimary,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SettleSurface,
        border = BorderStroke(1.dp, SettleDivider)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val chartValues = categories.map { it.totalAmount.toFloat() }
            
            DonutChart(
                values = chartValues,
                colors = ChartColors,
                modifier = Modifier.size(200.dp).padding(16.dp),
                centerText = categories.firstOrNull()?.category?.emoji ?: ""
            )

            Spacer(modifier = Modifier.height(24.dp))

            categories.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp))
                            .background(ChartColors[index % ChartColors.size])
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "${item.category.emoji} ${item.category.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SettleTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Money.format(item.totalAmount),
                            style = MaterialTheme.typography.titleSmall,
                            color = SettleTextPrimary
                        )
                        Text(
                            "${(item.percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SettleTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyTrendSection(months: List<MonthlySpending>) {
    Text(
        "Monthly Trend",
        style = MaterialTheme.typography.titleMedium,
        color = SettleTextPrimary,
        modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SettleSurface,
        border = BorderStroke(1.dp, SettleDivider)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            BarChart(
                labels = months.map { it.monthYear },
                values = months.map { it.totalAmount.toFloat() },
                barColor = SettlePrimary,
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                months.forEach { month ->
                    Text(
                        month.monthYear.take(3), // Just "Jan", "Feb", etc.
                        style = MaterialTheme.typography.labelSmall,
                        color = SettleTextSecondary
                    )
                }
            }
        }
    }
}
