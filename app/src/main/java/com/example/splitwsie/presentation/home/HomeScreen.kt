package com.example.splitwsie.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.model.Expense
import androidx.compose.material.icons.filled.Search
import com.example.splitwsie.domain.model.ExpenseCategory
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.presentation.components.SettleCard
import com.example.splitwsie.presentation.components.SettlePrimaryButton
import com.example.splitwsie.presentation.components.SettleSecondaryButton
import com.example.splitwsie.presentation.components.ActivityFeedItem
import com.example.splitwsie.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToGroupDetails: (String) -> Unit,
    onNavigateToAddExpense: (String?) -> Unit,
    onNavigateToSettleUp: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToFriends: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    var showSelectGroupForExpenseDialog by remember { mutableStateOf(false) }
    var localSearchQuery by remember { mutableStateOf(uiState.searchQuery) }
    var isSearchVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is HomeEvent.Error -> event.message
                is HomeEvent.Info -> event.message
                is HomeEvent.GroupJoined -> "Joined ${event.groupName}"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showJoinDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { code ->
                viewModel.joinGroup(code)
                showJoinDialog = false
            }
        )
    }

    if (showSelectGroupForExpenseDialog) {
        SelectGroupDialog(
            groups = uiState.groups,
            onDismiss = { showSelectGroupForExpenseDialog = false },
            onGroupSelected = { groupId ->
                showSelectGroupForExpenseDialog = false
                onNavigateToAddExpense(groupId)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SettleBackground
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SettlePrimary)
            }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    PremiumHomeHeader(
                        userName = uiState.userName,
                        pendingCount = uiState.pendingPaymentCount,
                        onSearchClick = { isSearchVisible = !isSearchVisible },
                        onNotificationClick = onNavigateToSettleUp,
                        onAvatarClick = onNavigateToProfile
                    )
                }
                
                item {
                    AnimatedVisibility(
                        visible = isSearchVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = { 
                                localSearchQuery = it
                                viewModel.onSearchQueryChanged(it) 
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            placeholder = { Text("Search groups and activities...", color = SettleTextSecondary) },
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
                    }
                }

                uiState.error?.let { error ->
                    item {
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
                }

                item {
                    PremiumBalanceCard(
                        netBalance = uiState.netBalance,
                        pendingCount = uiState.pendingPaymentCount
                    )
                }

                item {
                    SmallSummaryBoxes(
                        youOwe = uiState.youOwe,
                        youAreOwed = uiState.youAreOwed,
                        activeGroupsCount = uiState.groups.size
                    )
                }

                item {
                    QuickActionsSection(
                        onAddExpense = { showSelectGroupForExpenseDialog = true },
                        onAnalytics = onNavigateToAnalytics
                    )
                }

                if (uiState.groups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Your Groups",
                            style = MaterialTheme.typography.titleMedium,
                            color = SettleTextPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            uiState.groups.forEach { summary ->
                                GroupCard(
                                    summary = summary,
                                    onClick = { onNavigateToGroupDetails(summary.group.id) }
                                )
                            }
                        }
                    }
                }

                if (uiState.recentActivities.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.labelMedium,
                            color = SettleTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            uiState.recentActivities.forEach { activity ->
                                val currentUser = uiState.usersMap[uiState.currentUserId]
                                ActivityFeedItem(
                                    activity = activity,
                                    currentUser = currentUser,
                                    usersMap = uiState.usersMap
                                )
                            }
                        }
                    }
                }

                if (uiState.groups.isEmpty() && uiState.recentActivities.isEmpty()) {
                    item { EmptyState(onCreateGroup = onNavigateToCreateGroup) }
                }

                item { Spacer(modifier = Modifier.height(120.dp)) }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // The screen draws edge to edge, so the footer must clear the gesture bar
                    // itself; without this it sat underneath the system navigation.
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
            ) {
                FloatingGlassmorphismFooter(
                    onJoinGroup = { showJoinDialog = true },
                    onCreateGroup = onNavigateToCreateGroup,
                    onSettleUp = onNavigateToSettleUp,
                    onFriends = onNavigateToFriends
                )
            }
        }
    }
}

@Composable
private fun FloatingGlassmorphismFooter(
    onJoinGroup: () -> Unit,
    onCreateGroup: () -> Unit,
    onSettleUp: () -> Unit,
    onFriends: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = SettlePositive.copy(alpha = 0.9f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FooterAction(icon = Icons.Default.GroupAdd, label = "Join Group", onClick = onJoinGroup)
            FooterAction(icon = Icons.Default.Add, label = "New Group", onClick = onCreateGroup)
            FooterAction(icon = Icons.Default.Payment, label = "Settle Up", onClick = onSettleUp)
            FooterAction(icon = Icons.Default.Group, label = "Friends", onClick = onFriends)
        }
    }
}

@Composable
private fun FooterAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            // 48dp keeps every footer target at the minimum recommended touch size.
            .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun PremiumHomeHeader(
    userName: String,
    pendingCount: Int,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning,"
            in 12..16 -> "Good afternoon,"
            else -> "Good evening,"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SettleSurfaceVariant)
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.take(1).uppercase().ifEmpty { "U" },
                    style = MaterialTheme.typography.titleMedium,
                    color = SettlePrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(greeting, style = MaterialTheme.typography.labelMedium, color = SettleTextSecondary)
                Text(
                    text = userName.ifEmpty { "User" },
                    style = MaterialTheme.typography.titleMedium,
                    color = SettleTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = SettleTextPrimary)
            }
            // Previously a bell that always said "No new notifications". It now reflects real
            // state: unresolved payments, and a route to act on them.
            BadgedBox(
                badge = {
                    if (pendingCount > 0) {
                        Badge(containerColor = SettlePending) { Text(pendingCount.toString()) }
                    }
                }
            ) {
                IconButton(onClick = onNotificationClick) {
                    Icon(
                    Icons.Default.NotificationsNone,
                    contentDescription = if (pendingCount > 0) {
                        "$pendingCount payments need attention"
                    } else {
                        "Settle up"
                    },
                    tint = SettleTextPrimary
                )
            }
        }
    }
}
}

@Composable
private fun PremiumBalanceCard(netBalance: Double, pendingCount: Int) {
    val balanceColor = if (netBalance >= 0) SettlePositive else SettleNegative

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SettlePrimary,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp)
                    .clip(RoundedCornerShape(75.dp))
                    .background(Color.White.copy(alpha = 0.03f))
            )

            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = "OVERALL BALANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (netBalance >= 0) "+${Money.format(netBalance)}"
                    else "-${Money.format(-netBalance)}",
                    style = MaterialTheme.typography.displaySmall,
                    color = balanceColor,
                    fontWeight = FontWeight.Bold
                )
                if (pendingCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$pendingCount payment${if (pendingCount == 1) "" else "s"} awaiting " +
                            "confirmation, not counted here",
                        style = MaterialTheme.typography.labelSmall,
                        color = SettlePending
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallSummaryBoxes(youOwe: Double, youAreOwed: Double, activeGroupsCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryBox(
            title = "YOU OWE",
            amount = Money.format(youOwe),
            accentColor = SettleNegative,
            modifier = Modifier.weight(1f)
        )
        SummaryBox(
            title = "YOU ARE OWED",
            amount = Money.format(youAreOwed),
            accentColor = SettlePositive,
            modifier = Modifier.weight(1f)
        )
        SummaryBox(
            title = "GROUPS",
            amount = activeGroupsCount.toString(),
            accentColor = SettleTextSecondary,
            modifier = Modifier.weight(0.8f)
        )
    }
}

@Composable
private fun SummaryBox(title: String, amount: String, accentColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SettleSurface,
        border = BorderStroke(1.dp, SettleDivider)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = SettleTextSecondary,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                color = SettleTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QuickActionsSection(onAddExpense: () -> Unit, onAnalytics: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettlePrimaryButton(
            text = "+ Add Expense",
            onClick = onAddExpense,
            modifier = Modifier.weight(1f)
        )
        SettleSecondaryButton(
            text = "📊 Analytics",
            onClick = onAnalytics,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GroupCard(summary: GroupSummary, onClick: () -> Unit) {
    val balance = summary.myBalance
    SettleCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SettleSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = summary.group.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = SettlePrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    summary.group.name.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = SettleTextPrimary
                )
                Text(
                    "${summary.group.members.size} members",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettleTextSecondary
                )
            }
            // Per-group position, so the user can see where they stand without opening it.
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when {
                        Money.isZero(balance) -> "settled"
                        balance > 0 -> "owed"
                        else -> "you owe"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SettleTextSecondary
                )
                Text(
                    if (Money.isZero(balance)) "—" else Money.format(kotlin.math.abs(balance)),
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        Money.isZero(balance) -> SettleTextSecondary
                        balance > 0 -> SettlePositive
                        else -> SettleNegative
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SettleTextSecondary)
        }
    }
}


@Composable
private fun EmptyState(onCreateGroup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(SettleSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = SettleTextSecondary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("No groups yet", style = MaterialTheme.typography.titleLarge, color = SettleTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Create a group for your next trip,\ndinner, event or shared expense.",
            style = MaterialTheme.typography.bodyMedium,
            color = SettleTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        SettleSecondaryButton(
            text = "Create Group",
            onClick = onCreateGroup,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}

@Composable
private fun JoinGroupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SettleSurface,
        title = { Text("Join a group", style = MaterialTheme.typography.titleLarge, color = SettleTextPrimary) },
        text = {
            Column {
                Text(
                    "Enter the 6-character group code shared with you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettleTextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim().uppercase().take(40) },
                    label = { Text("Group code") },
                    placeholder = { Text("e.g. K7M2QP") },
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
                onClick = { onConfirm(code.trim()) },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SettlePrimary)
            ) { Text("Join") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SettleTextSecondary) }
        }
    )
}

@Composable
private fun SelectGroupDialog(
    groups: List<GroupSummary>,
    onDismiss: () -> Unit,
    onGroupSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SettleSurface,
        title = { Text("Select group", style = MaterialTheme.typography.titleLarge, color = SettleTextPrimary) },
        text = {
            if (groups.isEmpty()) {
                Text(
                    "Create or join a group first to add an expense.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettleTextSecondary
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEach { summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onGroupSelected(summary.group.id) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                    .background(SettleSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = summary.group.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SettlePrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                summary.group.name.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = SettleTextPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (groups.isEmpty()) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SettlePrimary)
                ) { Text("OK") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel", color = SettleTextSecondary) }
            }
        }
    )
}
