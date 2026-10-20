package com.example.splitwsie.presentation.group

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.model.Debt
import com.example.splitwsie.domain.model.Expense
import com.example.splitwsie.domain.model.ExpenseCategory
import com.example.splitwsie.domain.model.ExpenseFilter
import com.example.splitwsie.domain.model.Settlement
import com.example.splitwsie.domain.model.SettlementStatus
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.model.GroupCategory
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.presentation.components.SettlePrimaryButton
import com.example.splitwsie.presentation.components.SettleStatusBadge
import com.example.splitwsie.domain.model.ActivityItem
import com.example.splitwsie.presentation.components.ActivityFeedItem
import com.example.splitwsie.presentation.components.FilterBottomSheet
import com.example.splitwsie.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAddExpense: (String, String?) -> Unit,
    onNavigateToPayment: (groupId: String, toUserId: String, expenseId: String?) -> Unit,
    onNavigateToSettings: (String) -> Unit,
    onGroupDeleted: () -> Unit,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.detailsState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val tabs = listOf("Expenses", "Balances", "Activity", "Members")

    LaunchedEffect(groupId) { viewModel.loadGroupDetails(groupId) }

    LaunchedEffect(Unit) {
        viewModel.detailsEvent.collect { event ->
            when (event) {
                is GroupDetailsEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is GroupDetailsEvent.Error -> snackbarHostState.showSnackbar(event.text)
                is GroupDetailsEvent.ExportSummary -> {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.text)
                    }
                    context.startActivity(Intent.createChooser(share, "Export Group Summary"))
                }
                GroupDetailsEvent.GroupDeleted -> onGroupDeleted()
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete this group?",
            body = "All of its expenses and payment records will be permanently removed. " +
                "This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteGroup(groupId)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showLeaveDialog) {
        ConfirmDialog(
            title = "Leave this group?",
            body = "You will stop seeing its expenses. You can rejoin with the group code.",
            confirmLabel = "Leave",
            onConfirm = {
                showLeaveDialog = false
                viewModel.leaveGroup(groupId)
            },
            onDismiss = { showLeaveDialog = false }
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            currentFilter = uiState.expenseFilter,
            members = uiState.members,
            onDismiss = { showFilterSheet = false },
            onFilterApplied = { filter ->
                viewModel.updateExpenseFilter(filter)
                showFilterSheet = false
            }
        )
    }

    Scaffold(
        containerColor = SettleBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.group?.name?.uppercase() ?: "LOADING",
                        style = MaterialTheme.typography.labelLarge,
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
                    uiState.group?.inviteCode?.let { code ->
                        IconButton(
                            onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Join \"${uiState.group?.name}\" on SETTLE with code $code"
                                    )
                                }
                                context.startActivity(Intent.createChooser(share, "Share group code"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share group code", tint = SettleTextPrimary)
                        }
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = SettleTextPrimary)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SettleSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Leave group", color = SettleTextPrimary) },
                            onClick = {
                                showMenu = false
                                showLeaveDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export group summary", color = SettleTextPrimary) },
                            onClick = {
                                showMenu = false
                                viewModel.exportGroupSummary()
                            }
                        )
                        if (uiState.isOwner) {
                            DropdownMenuItem(
                                text = { Text("Group settings", color = SettleTextPrimary) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToSettings(groupId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete group", color = SettleNegative) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettleBackground)
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0 && uiState.group != null) {
                FloatingActionButton(
                    onClick = { onNavigateToAddExpense(groupId, null) },
                    containerColor = SettlePrimary,
                    contentColor = SettleOnPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add expense")
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = SettlePrimary) }
            return@Scaffold
        }

        if (uiState.group == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    uiState.error ?: "This group is unavailable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettleTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GroupDetailsHeader(uiState = uiState)

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = SettleBackground,
                contentColor = SettlePrimary,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = SettlePrimary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTabIndex == index) SettlePrimary else SettleTextSecondary
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> ExpensesTab(
                    expenses = uiState.expenses,
                    currentUserId = uiState.currentUserId,
                    members = uiState.members,
                    filter = uiState.expenseFilter,
                    onUpdateFilter = viewModel::updateExpenseFilter,
                    onShowFilterSheet = { showFilterSheet = true },
                    onExpenseClick = { expenseId -> onNavigateToAddExpense(groupId, expenseId) }
                )

                1 -> BalancesTab(
                    debts = uiState.debts,
                    currentUserId = uiState.currentUserId,
                    members = uiState.members,
                    onPay = { toUserId -> onNavigateToPayment(groupId, toUserId, null) }
                )

                2 -> ActivityTab(
                    activities = uiState.activities,
                    currentUserId = uiState.currentUserId,
                    users = uiState.usersMap
                )

                3 -> MembersTab(
                    members = uiState.members,
                    currentUserId = uiState.currentUserId,
                    isOwner = uiState.isOwner,
                    inviteCode = uiState.group?.inviteCode,
                    onAddMember = { name, email -> viewModel.addNewMember(groupId, name, email) },
                    onRemoveMember = { userId -> viewModel.removeMember(groupId, userId) }
                )
            }
        }
    }
}

@Composable
private fun GroupDetailsHeader(uiState: GroupDetailsUiState) {
    val balance = uiState.currentUserBalance
    val balanceColor = when {
        balance > 0 -> SettlePositive
        balance < 0 -> SettleNegative
        else -> SettleTextPrimary
    }
    val balanceText = when {
        Money.isZero(balance) -> "All settled up"
        balance > 0 -> "You are owed"
        else -> "You owe"
    }

    val category = GroupCategory.fromName(uiState.group?.category ?: "Other")

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(category.emoji, style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = Money.format(kotlin.math.abs(balance)),
                style = MaterialTheme.typography.displayMedium,
                color = balanceColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(balanceText, style = MaterialTheme.typography.titleMedium, color = SettleTextSecondary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${Money.format(uiState.totalExpenses)} total expenses · " +
                "${uiState.members.size} members",
            style = MaterialTheme.typography.bodySmall,
            color = SettleTextSecondary
        )
    }
}

@Composable
private fun EmptyTabMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = SettleTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ExpensesTab(
    expenses: List<Expense>,
    currentUserId: String,
    members: List<User>,
    filter: ExpenseFilter,
    onUpdateFilter: (ExpenseFilter) -> Unit,
    onShowFilterSheet: () -> Unit,
    onExpenseClick: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    var localQuery by remember { mutableStateOf(filter.query) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = localQuery,
                onValueChange = { 
                    localQuery = it
                    onUpdateFilter(filter.copy(query = it)) 
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search expenses...", color = SettleTextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SettleTextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SettleSurfaceVariant,
                    unfocusedContainerColor = SettleSurfaceVariant,
                    focusedBorderColor = SettlePrimary,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onShowFilterSheet,
                modifier = Modifier.size(48.dp)
                    .background(if (filter.isActive) SettlePrimary else SettleSurfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filter",
                    tint = if (filter.isActive) SettleOnPrimary else SettleTextPrimary
                )
            }
        }
        
        if (expenses.isEmpty()) {
            EmptyTabMessage(if (filter.isActive) "No expenses match your filters." else "No expenses yet.\nTap + to add the first one.")
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(expenses, key = { it.id }) { expense ->
            val isYou = expense.paidById == currentUserId
            val paidByName = members.find { it.id == expense.paidById }?.name ?: "Unknown member"
            val myShare = expense.splits.find { it.userId == currentUserId }?.amountOwed ?: 0.0
            val category = ExpenseCategory.fromName(expense.category)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onExpenseClick(expense.id) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                        .background(SettleSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(category.emoji, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        expense.title.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = SettleTextPrimary
                    )
                    Text(
                        "Paid by ${if (isYou) "you" else paidByName} · ${expense.splits.size} people",
                        style = MaterialTheme.typography.bodySmall,
                        color = SettleTextSecondary
                    )
                    when {
                        // Per-expense position, so the row explains itself without needing the
                        // Balances tab. Settling happens against the net debt, not per expense.
                        isYou && expense.splits.any { it.userId != currentUserId } -> Text(
                            "You lent ${Money.format(expense.amount - myShare)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SettlePositive
                        )
                        !isYou && myShare > 0 -> Text(
                            "Your share ${Money.format(myShare)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SettleNegative
                        )
                        else -> Unit
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Money.format(expense.amount),
                        style = MaterialTheme.typography.titleMedium,
                        color = SettleTextPrimary
                    )
                    Text(
                        dateFormat.format(expense.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = SettleTextSecondary
                    )
                }
            }
            HorizontalDivider(color = SettleDivider)
        }
    }
}
}

@Composable
private fun BalancesTab(
    debts: List<Debt>,
    currentUserId: String,
    members: List<User>,
    onPay: (String) -> Unit
) {
    if (debts.isEmpty()) {
        EmptyTabMessage("Everyone is settled up.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(debts, key = { "${it.fromUserId}:${it.toUserId}" }) { debt ->
            val iOwe = debt.fromUserId == currentUserId
            val owesMe = debt.toUserId == currentUserId
            val fromName = members.find { it.id == debt.fromUserId }?.name ?: "Unknown member"
            val toName = members.find { it.id == debt.toUserId }?.name ?: "Unknown member"

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            iOwe -> "You owe $toName"
                            owesMe -> "$fromName owes you"
                            else -> "$fromName owes $toName"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = SettleTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        Money.format(debt.amount),
                        style = MaterialTheme.typography.titleLarge,
                        color = when {
                            iOwe -> SettleNegative
                            owesMe -> SettlePositive
                            else -> SettleTextPrimary
                        }
                    )
                }

                // Only the debtor can settle their own debt. The button is not shown to anyone
                // else, and the settlement engine refuses it even if it somehow is.
                if (iOwe) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettlePrimaryButton(
                        text = "Pay & settle",
                        onClick = { onPay(debt.toUserId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(color = SettleDivider, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun ActivityTab(
    activities: List<ActivityItem>,
    currentUserId: String,
    users: Map<String, User>
) {
    if (activities.isEmpty()) {
        EmptyTabMessage("No activity in this group yet.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(activities, key = { it.id }) { activity ->
            ActivityFeedItem(
                activity = activity,
                currentUser = users[currentUserId],
                usersMap = users
            )
        }
    }
}

@Composable
private fun MembersTab(
    members: List<User>,
    currentUserId: String,
    isOwner: Boolean,
    inviteCode: String?,
    onAddMember: (String, String) -> Unit,
    onRemoveMember: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AddMemberDialog(
            onDismiss = { showDialog = false },
            onConfirm = { name, email ->
                onAddMember(name, email)
                showDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (inviteCode != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SettleSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "GROUP CODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SettleTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            inviteCode,
                            style = MaterialTheme.typography.headlineSmall,
                            color = SettleTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Share this code so others can join the group.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SettleTextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            OutlinedButton(
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SettlePrimary),
                border = BorderStroke(1.dp, SettleDivider)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add member", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(members, key = { it.id }) { member ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                        .background(SettleSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = SettlePrimary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        member.name + if (member.id == currentUserId) " (you)" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = SettleTextPrimary
                    )
                    Text(member.email, style = MaterialTheme.typography.bodySmall, color = SettleTextSecondary)
                    Text(
                        member.upiId?.let { "UPI $it" } ?: "No UPI ID",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (member.upiId != null) SettlePositive else SettleTextSecondary
                    )
                }
                if (isOwner && member.id != currentUserId) {
                    IconButton(onClick = { onRemoveMember(member.id) }) {
                        Icon(
                            Icons.Default.PersonRemove,
                            contentDescription = "Remove ${member.name}",
                            tint = SettleTextSecondary
                        )
                    }
                }
            }
            HorizontalDivider(color = SettleDivider)
        }
    }
}

@Composable
private fun AddMemberDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SettleSurface,
        title = { Text("Add member", style = MaterialTheme.typography.titleLarge, color = SettleTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "If this email already belongs to a profile on this device, that profile " +
                        "is added instead of a duplicate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettleTextSecondary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SettlePrimary,
                        unfocusedBorderColor = SettleDivider
                    )
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim().take(120) },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SettlePrimary,
                        unfocusedBorderColor = SettleDivider
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, email) },
                enabled = name.isNotBlank() && email.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SettlePrimary)
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SettleTextSecondary) }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SettleSurface,
        title = { Text(title, color = SettleTextPrimary) },
        text = { Text(body, color = SettleTextSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SettleNegative)
            ) { Text(confirmLabel, color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SettleTextSecondary) }
        }
    )
}
