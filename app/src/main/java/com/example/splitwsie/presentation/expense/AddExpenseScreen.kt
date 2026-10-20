package com.example.splitwsie.presentation.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.splitwsie.presentation.util.ImageUtils
import kotlinx.coroutines.launch
import com.example.splitwsie.domain.model.ExpenseCategory
import com.example.splitwsie.domain.model.SplitType
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.domain.util.Validators
import com.example.splitwsie.presentation.components.SettlePrimaryButton
import com.example.splitwsie.presentation.components.SettleSegmentedControl
import com.example.splitwsie.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String?,
    friendId: String? = null,
    expenseId: String? = null,
    onNavigateBack: () -> Unit,
    onExpenseAdded: () -> Unit,
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var receiptImageUrl by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ExpenseCategory.GENERAL) }
    var splitType by rememberSaveable { mutableStateOf(SplitType.EQUAL) }
    var selectedMemberIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var paidById by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // Set once the edit payload has been applied, so the "select everyone by default" effect
    // cannot later overwrite the split the user is editing.
    var editPrefilled by rememberSaveable { mutableStateOf(false) }
    var repeatInterval by rememberSaveable { mutableStateOf(com.example.splitwsie.domain.model.RepeatInterval.NONE) }
    var showRepeatMenu by rememberSaveable { mutableStateOf(false) }

    val exactAmounts = remember { mutableStateMapOf<String, String>() }
    val percentages = remember { mutableStateMapOf<String, String>() }
    val shares = remember { mutableStateMapOf<String, String>() }

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = expenseId != null
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val savedUri = ImageUtils.saveImageToInternalStorage(context, uri)
                if (savedUri != null) {
                    receiptImageUrl = savedUri.toString()
                }
            }
        }
    }

    LaunchedEffect(groupId, friendId) { viewModel.loadMembers(groupId, friendId) }

    LaunchedEffect(expenseId) {
        if (expenseId != null) viewModel.loadExpenseForEdit(expenseId)
    }

    LaunchedEffect(uiState.currentUserId) {
        if (paidById == null && uiState.currentUserId.isNotBlank()) {
            paidById = uiState.currentUserId
        }
    }

    LaunchedEffect(uiState.expenseToEdit) {
        uiState.expenseToEdit?.let { expense ->
            title = expense.title
            amountText = "%.2f".format(expense.amount)
            notes = expense.notes.orEmpty()
            receiptImageUrl = expense.receiptImageUrl.orEmpty()
            category = ExpenseCategory.fromName(expense.category)
            paidById = expense.paidById
            splitType = expense.splits.firstOrNull()?.splitType ?: SplitType.EQUAL
            selectedMemberIds = expense.splits.map { it.userId }.toSet()
            repeatInterval = expense.repeatInterval

            exactAmounts.clear()
            percentages.clear()
            shares.clear()
            expense.splits.forEach { split ->
                exactAmounts[split.userId] = "%.2f".format(split.amountOwed)
                split.percentage?.let { percentages[split.userId] = "%.2f".format(it) }
                split.shares?.let { shares[split.userId] = "%.2f".format(it) }
            }
            editPrefilled = true
        }
    }

    LaunchedEffect(uiState.members, editPrefilled) {
        // Only default to "everyone" for a brand-new expense.
        if (!isEditing && uiState.members.isNotEmpty() && selectedMemberIds.isEmpty()) {
            selectedMemberIds = uiState.members.map { it.id }.toSet()
        }
    }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) onExpenseAdded()
    }

    LaunchedEffect(uiState.deletedSuccessfully) {
        if (uiState.deletedSuccessfully) onExpenseAdded()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showDeleteDialog && expenseId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SettleSurface,
            title = { Text("Delete this expense?", color = SettleTextPrimary) },
            text = {
                Text(
                    "Everyone's balance in this group will be recalculated without it. " +
                        "This cannot be undone.",
                    color = SettleTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteExpense(expenseId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SettleNegative)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = SettleTextSecondary)
                }
            }
        )
    }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val selectedMembers = uiState.members.filter { it.id in selectedMemberIds }
    val splitSummary = remember(amount, splitType, selectedMemberIds, exactAmounts.toMap(), percentages.toMap(), shares.toMap()) {
        summariseSplit(amount, splitType, selectedMembers.map { it.id }, exactAmounts, percentages, shares)
    }

    Scaffold(
        containerColor = SettleBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) "Edit expense" else "Add expense",
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
                actions = {
                    if (isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete expense",
                                tint = SettleNegative
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettleBackground)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("How much?", style = MaterialTheme.typography.labelLarge, color = SettleTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = Validators.sanitiseDecimalInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.displayMedium.copy(textAlign = TextAlign.Center),
                        placeholder = {
                            Text(
                                "0.00",
                                style = MaterialTheme.typography.displayMedium.copy(textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth(),
                                color = SettleDivider
                            )
                        },
                        prefix = {
                            Text("₹", style = MaterialTheme.typography.headlineSmall, color = SettleTextPrimary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = SettleSurface,
                            unfocusedContainerColor = SettleSurface
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(Validators.MAX_TITLE_LENGTH) },
                    label = { Text("What was this for?") },
                    placeholder = { Text("e.g. Dinner, Taxi") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SettlePrimary,
                        unfocusedBorderColor = SettleDivider
                    )
                )
            }

            item {
                Text("Category", style = MaterialTheme.typography.labelLarge, color = SettleTextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExpenseCategory.entries) { option ->
                        CategoryChip(
                            category = option,
                            selected = option == category,
                            onClick = { category = option }
                        )
                    }
                }
            }

            if (uiState.members.isNotEmpty()) {
                item {
                    PaidByPicker(
                        members = uiState.members,
                        paidById = paidById,
                        onSelected = { paidById = it }
                    )
                }
            }

            item {
                Text("Split by", style = MaterialTheme.typography.labelLarge, color = SettleTextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                SettleSegmentedControl(
                    options = SplitType.entries,
                    selectedOption = splitType,
                    onOptionSelected = { splitType = it },
                    optionLabel = { it.displayName }
                )
            }

            if (uiState.members.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Split between",
                            style = MaterialTheme.typography.labelLarge,
                            color = SettleTextSecondary
                        )
                        TextButton(
                            onClick = {
                                selectedMemberIds =
                                    if (selectedMemberIds.size == uiState.members.size) emptySet()
                                    else uiState.members.map { it.id }.toSet()
                            }
                        ) {
                            Text(
                                if (selectedMemberIds.size == uiState.members.size) "Clear all" else "Select all",
                                style = MaterialTheme.typography.labelMedium,
                                color = SettlePrimary
                            )
                        }
                    }
                }

                items(uiState.members, key = { it.id }) { member ->
                    MemberSplitRow(
                        member = member,
                        isSelected = selectedMemberIds.contains(member.id),
                        onToggle = { checked ->
                            selectedMemberIds = if (checked) {
                                selectedMemberIds + member.id
                            } else {
                                selectedMemberIds - member.id
                            }
                        },
                        splitType = splitType,
                        totalAmount = amount,
                        selectedCount = selectedMemberIds.size,
                        selectedIndex = selectedMembers.indexOfFirst { it.id == member.id },
                        exactAmount = exactAmounts[member.id].orEmpty(),
                        onExactAmountChange = {
                            exactAmounts[member.id] = Validators.sanitiseDecimalInput(it)
                        },
                        percentage = percentages[member.id].orEmpty(),
                        onPercentageChange = {
                            percentages[member.id] = Validators.sanitiseDecimalInput(it)
                        },
                        shareCount = shares[member.id].orEmpty(),
                        onShareCountChange = {
                            shares[member.id] = Validators.sanitiseDecimalInput(it)
                        }
                    )
                }

                item {
                    SplitSummary(summary = splitSummary)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Repeat", style = MaterialTheme.typography.labelLarge, color = SettleTextSecondary)

                    Box {
                        TextButton(onClick = { showRepeatMenu = true }) {
                            Text(repeatInterval.displayName, color = SettlePrimary)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SettlePrimary)
                        }
                        DropdownMenu(
                            expanded = showRepeatMenu,
                            onDismissRequest = { showRepeatMenu = false },
                            modifier = Modifier.background(SettleSurface)
                        ) {
                            com.example.splitwsie.domain.model.RepeatInterval.entries.forEach { interval ->
                                DropdownMenuItem(
                                    text = { Text(interval.displayName, color = SettleTextPrimary) },
                                    onClick = {
                                        repeatInterval = interval
                                        showRepeatMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(Validators.MAX_NOTES_LENGTH) },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    supportingText = {
                        Text("${notes.length}/${Validators.MAX_NOTES_LENGTH}", color = SettleTextSecondary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SettlePrimary,
                        unfocusedBorderColor = SettleDivider
                    )
                )
            }

            item {
                Text("Receipt", style = MaterialTheme.typography.labelLarge, color = SettleTextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                if (receiptImageUrl.isNotBlank()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = receiptImageUrl,
                            contentDescription = "Receipt Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { imagePickerLauncher.launch("image/*") }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Receipt attached",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SettlePrimary
                            )
                            TextButton(onClick = { receiptImageUrl = "" }) {
                                Text("Remove", color = SettleNegative)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Attach Receipt Image")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettlePrimaryButton(
                    text = if (isEditing) "Save changes" else "Save expense",
                    onClick = {
                        viewModel.addExpense(
                            groupId = groupId,
                            expenseId = expenseId,
                            title = title,
                            amountText = amountText,
                            category = category,
                            notes = notes,
                            splitType = splitType,
                            selectedMemberIds = selectedMemberIds.toList(),
                            exactAmounts = exactAmounts.mapValues { it.value.toDoubleOrNull() ?: 0.0 },
                            percentages = percentages.mapValues { it.value.toDoubleOrNull() ?: 0.0 },
                            shares = shares.mapValues { it.value.toDoubleOrNull() ?: 0.0 },
                            paidById = paidById,
                            receiptImageUrl = receiptImageUrl,
                            repeatInterval = repeatInterval
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    // Disabled until the split actually balances, so an invalid expense cannot
                    // be submitted and rejected after the fact.
                    enabled = title.isNotBlank() &&
                        amountText.isNotBlank() &&
                        selectedMemberIds.isNotEmpty() &&
                        splitSummary.isBalanced,
                    isLoading = uiState.isSaving || uiState.isDeleting
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(category: ExpenseCategory, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SettlePrimary else SettleSurface,
        border = BorderStroke(1.dp, if (selected) SettlePrimary else SettleDivider),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(category.emoji, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                category.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) SettleOnPrimary else SettleTextPrimary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaidByPicker(members: List<User>, paidById: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedMember = members.find { it.id == paidById }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedMember?.name ?: "Select who paid",
            onValueChange = {},
            readOnly = true,
            label = { Text("Paid by") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SettlePrimary,
                unfocusedBorderColor = SettleDivider
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SettleSurface)
        ) {
            members.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.name, color = SettleTextPrimary) },
                    onClick = {
                        onSelected(member.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MemberSplitRow(
    member: User,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    splitType: SplitType,
    totalAmount: Double,
    selectedCount: Int,
    selectedIndex: Int,
    exactAmount: String,
    onExactAmountChange: (String) -> Unit,
    percentage: String,
    onPercentageChange: (String) -> Unit,
    shareCount: String,
    onShareCountChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SettleSurface else SettleBackground)
            .clickable { onToggle(!isSelected) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = SettlePrimary, uncheckedColor = SettleDivider)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(SettleSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                member.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = SettlePrimary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            member.name,
            style = MaterialTheme.typography.bodyLarge,
            color = SettleTextPrimary,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            when (splitType) {
                SplitType.EQUAL -> {
                    // Shows the exact paise this member is charged, including the extra paisa
                    // the remainder distribution assigns to the first few members.
                    val share = if (selectedCount > 0 && selectedIndex >= 0) {
                        Money.fromPaise(
                            Money.splitEqually(Money.toPaise(totalAmount), selectedCount)
                                .getOrElse(selectedIndex) { 0L }
                        )
                    } else {
                        0.0
                    }
                    Text(
                        "₹%.2f".format(share),
                        style = MaterialTheme.typography.titleMedium,
                        color = SettleTextPrimary
                    )
                }

                SplitType.EXACT -> SplitInputField(
                    value = exactAmount,
                    onValueChange = onExactAmountChange,
                    prefix = "₹",
                    suffix = null,
                    placeholder = "0.00"
                )

                SplitType.PERCENTAGE -> SplitInputField(
                    value = percentage,
                    onValueChange = onPercentageChange,
                    prefix = null,
                    suffix = "%",
                    placeholder = "0"
                )

                SplitType.SHARES -> SplitInputField(
                    value = shareCount,
                    onValueChange = onShareCountChange,
                    prefix = "x",
                    suffix = null,
                    placeholder = "1"
                )
            }
        }
    }
}

@Composable
private fun SplitInputField(
    value: String,
    onValueChange: (String) -> Unit,
    prefix: String?,
    suffix: String?,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.width(110.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        placeholder = { Text(placeholder) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SettlePrimary,
            unfocusedBorderColor = SettleDivider
        )
    )
}

/** Live feedback on whether the split adds up, shown before the user tries to save. */
private data class SplitSummaryState(
    val isBalanced: Boolean,
    val message: String,
    val isError: Boolean
)

private fun summariseSplit(
    amount: Double,
    splitType: SplitType,
    memberIds: List<String>,
    exactAmounts: Map<String, String>,
    percentages: Map<String, String>,
    shares: Map<String, String>
): SplitSummaryState {
    if (memberIds.isEmpty()) {
        return SplitSummaryState(false, "Select at least one person", isError = true)
    }
    if (amount <= 0) {
        return SplitSummaryState(false, "Enter an amount", isError = false)
    }

    return when (splitType) {
        SplitType.EQUAL -> SplitSummaryState(
            isBalanced = true,
            message = "Split ${memberIds.size} ways",
            isError = false
        )

        SplitType.EXACT -> {
            val totalPaise = memberIds.sumOf { Money.toPaise(exactAmounts[it]?.toDoubleOrNull() ?: 0.0) }
            val target = Money.toPaise(amount)
            val diff = target - totalPaise
            when {
                diff == 0L -> SplitSummaryState(true, "Adds up exactly", isError = false)
                diff > 0 -> SplitSummaryState(
                    false,
                    "${Money.format(Money.fromPaise(diff))} left to assign",
                    isError = true
                )
                else -> SplitSummaryState(
                    false,
                    "${Money.format(Money.fromPaise(-diff))} over the total",
                    isError = true
                )
            }
        }

        SplitType.PERCENTAGE -> {
            val total = memberIds.sumOf { percentages[it]?.toDoubleOrNull() ?: 0.0 }
            val diff = 100.0 - total
            when {
                kotlin.math.abs(diff) <= 0.01 -> SplitSummaryState(true, "Adds up to 100%", isError = false)
                diff > 0 -> SplitSummaryState(false, "%.2f%% left to assign".format(diff), isError = true)
                else -> SplitSummaryState(false, "%.2f%% over 100%%".format(-diff), isError = true)
            }
        }

        SplitType.SHARES -> {
            val total = memberIds.sumOf { shares[it]?.toDoubleOrNull() ?: 0.0 }
            if (total > 0) {
                val perShare = amount / total
                SplitSummaryState(true, "%.1f shares (₹%.2f/share)".format(total, perShare), isError = false)
            } else {
                SplitSummaryState(false, "Enter at least 1 share", isError = true)
            }
        }
    }
}

@Composable
private fun SplitSummary(summary: SplitSummaryState) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (summary.isError) SettleNegativeContainer else SettleSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            summary.message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (summary.isError) SettleNegative else SettleTextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(12.dp)
        )
    }
}
