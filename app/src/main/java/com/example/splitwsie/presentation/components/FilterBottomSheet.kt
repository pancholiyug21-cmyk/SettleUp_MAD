package com.example.splitwsie.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.splitwsie.domain.model.ExpenseCategory
import com.example.splitwsie.domain.model.ExpenseFilter
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentFilter: ExpenseFilter,
    members: List<User>,
    onDismiss: () -> Unit,
    onFilterApplied: (ExpenseFilter) -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf(currentFilter.categoryId) }
    var selectedMemberId by remember { mutableStateOf(currentFilter.memberId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SettleSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "Filter Expenses",
                style = MaterialTheme.typography.titleLarge,
                color = SettleTextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                "Category",
                style = MaterialTheme.typography.titleMedium,
                color = SettleTextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SettlePrimary,
                            selectedLabelColor = SettleOnPrimary
                        )
                    )
                }
                items(ExpenseCategory.entries.toTypedArray()) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.name,
                        onClick = { selectedCategoryId = category.name },
                        label = { Text("${category.emoji} ${category.displayName}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SettlePrimary,
                            selectedLabelColor = SettleOnPrimary
                        )
                    )
                }
            }

            Text(
                "Paid By",
                style = MaterialTheme.typography.titleMedium,
                color = SettleTextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedMemberId == null,
                        onClick = { selectedMemberId = null },
                        label = { Text("Anyone") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SettlePrimary,
                            selectedLabelColor = SettleOnPrimary
                        )
                    )
                }
                items(members) { member ->
                    FilterChip(
                        selected = selectedMemberId == member.id,
                        onClick = { selectedMemberId = member.id },
                        label = { Text(member.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SettlePrimary,
                            selectedLabelColor = SettleOnPrimary
                        )
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                SettleSecondaryButton(
                    text = "Reset",
                    onClick = {
                        selectedCategoryId = null
                        selectedMemberId = null
                    },
                    modifier = Modifier.weight(1f)
                )
                SettlePrimaryButton(
                    text = "Apply Filters",
                    onClick = {
                        onFilterApplied(
                            currentFilter.copy(
                                categoryId = selectedCategoryId,
                                memberId = selectedMemberId
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
