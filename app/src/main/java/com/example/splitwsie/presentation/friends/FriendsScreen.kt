package com.example.splitwsie.presentation.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.presentation.theme.SettlePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel = hiltViewModel(),
    onNavigateToAddExpense: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.searchUsers(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search users by name or email") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SettlePrimary,
                    focusedLabelColor = SettlePrimary,
                    cursorColor = SettlePrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SettlePrimary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.searchQuery.isNotBlank()) {
                        item {
                            Text(
                                text = "Search Results",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        if (state.searchResults.isEmpty()) {
                            item {
                                Text(
                                    "No users found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(state.searchResults) { user ->
                                UserItem(
                                    user = user,
                                    actionIcon = {
                                        IconButton(onClick = { viewModel.sendRequest(user.id) }) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend", tint = SettlePrimary)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        if (state.pendingRequests.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Pending Requests",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(state.pendingRequests) { request ->
                                UserItem(
                                    user = request.requester,
                                    balanceText = null,
                                    actionIcon = {
                                        Row {
                                            IconButton(onClick = { viewModel.acceptRequest(request.requester.id) }) {
                                                Icon(Icons.Default.Check, contentDescription = "Accept", tint = SettlePrimary)
                                            }
                                            IconButton(onClick = { viewModel.rejectRequest(request.requester.id) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        item {
                            Text(
                                text = "My Friends",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        if (state.friends.isEmpty()) {
                            item {
                                Text(
                                    "You have no friends added yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(state.friends) { friendUiModel ->
                                UserItem(
                                    user = friendUiModel.user,
                                    balanceText = buildString {
                                        if (friendUiModel.balance > 0) append("Owes you ₹${friendUiModel.balance}")
                                        else if (friendUiModel.balance < 0) append("You owe ₹${-friendUiModel.balance}")
                                        else append("Settled up")
                                    },
                                    balanceColor = if (friendUiModel.balance > 0) MaterialTheme.colorScheme.primary else if (friendUiModel.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    actionIcon = {
                                        Row {
                                            IconButton(onClick = { onNavigateToAddExpense(friendUiModel.user.id) }) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Add Expense",
                                                    tint = SettlePrimary
                                                )
                                            }
                                            IconButton(onClick = { viewModel.removeFriend(friendUiModel.user.id) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserItem(
    user: User,
    balanceText: String? = null,
    balanceColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionIcon: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (balanceText != null) {
                    Text(text = balanceText, style = MaterialTheme.typography.bodySmall, color = balanceColor, fontWeight = FontWeight.Bold)
                }
            }
            actionIcon()
        }
    }
}
