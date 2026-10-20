package com.example.splitwsie.presentation.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.splitwsie.domain.model.Comment
import com.example.splitwsie.domain.model.ExpenseCategory
import com.example.splitwsie.domain.model.User
import com.example.splitwsie.domain.util.Money
import com.example.splitwsie.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailsScreen(
    expenseId: String,
    onNavigateBack: () -> Unit,
    onEditExpense: (String, String?) -> Unit,
    viewModel: ExpenseDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var commentText by remember { mutableStateOf("") }
    var showReceipt by remember { mutableStateOf(false) }

    LaunchedEffect(expenseId) {
        viewModel.loadExpense(expenseId)
    }

    if (showReceipt && uiState.expense?.receiptImageUrl != null) {
        AlertDialog(
            onDismissRequest = { showReceipt = false },
            confirmButton = {
                TextButton(onClick = { showReceipt = false }) { Text("Close") }
            },
            text = {
                AsyncImage(
                    model = uiState.expense?.receiptImageUrl,
                    contentDescription = "Receipt",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 500.dp),
                    contentScale = ContentScale.Fit
                )
            },
            containerColor = SettleSurface
        )
    }

    Scaffold(
        containerColor = SettleBackground,
        topBar = {
            TopAppBar(
                title = { Text("Expense Details", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { 
                        uiState.expense?.let { onEditExpense(it.groupId ?: "", it.id) }
                    }) {
                        Text("Edit", color = SettlePrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettleBackground)
            )
        },
        bottomBar = {
            if (uiState.expense != null) {
                Surface(
                    color = SettleSurface,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .windowInsetsPadding(WindowInsets.ime),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = { Text("Add a comment...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SettlePrimary,
                                unfocusedBorderColor = SettleDivider
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                viewModel.addComment(expenseId, commentText)
                                commentText = ""
                            })
                        )
                        IconButton(
                            onClick = {
                                viewModel.addComment(expenseId, commentText)
                                commentText = ""
                            },
                            enabled = commentText.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = SettlePrimary)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SettlePrimary)
            }
            return@Scaffold
        }

        val expense = uiState.expense
        if (expense == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Expense not found", color = SettleTextSecondary)
            }
            return@Scaffold
        }

        val category = ExpenseCategory.fromName(expense.category)
        val paidBy = uiState.groupMembers.find { it.id == expense.paidById }?.name ?: "Someone"
        val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp) // space for bottom bar
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(SettleSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(category.emoji, style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(expense.title, style = MaterialTheme.typography.headlineSmall, color = SettleTextPrimary)
                    Text(Money.format(expense.amount), style = MaterialTheme.typography.displaySmall, color = SettleTextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Paid by $paidBy on ${dateFormat.format(expense.date)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SettleTextSecondary
                    )
                    
                    if (expense.receiptImageUrl != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { showReceipt = true }) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Receipt")
                        }
                    }
                }
                HorizontalDivider(color = SettleDivider)
            }

            item {
                Text(
                    "Splits",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
                    color = SettleTextPrimary
                )
            }

            items(expense.splits, key = { "split_${it.userId}" }) { split ->
                val memberName = uiState.groupMembers.find { it.id == split.userId }?.name ?: "Someone"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(memberName, style = MaterialTheme.typography.bodyLarge, color = SettleTextPrimary)
                    Text(Money.format(split.amountOwed), style = MaterialTheme.typography.bodyLarge, color = SettleTextPrimary)
                }
            }

            item {
                HorizontalDivider(color = SettleDivider, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Comments",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 16.dp),
                    color = SettleTextPrimary
                )
                if (uiState.comments.isEmpty()) {
                    Text(
                        "No comments yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SettleTextSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            items(uiState.comments, key = { it.id }) { comment ->
                val commenter = uiState.groupMembers.find { it.id == comment.userId }?.name ?: "Someone"
                val isMe = comment.userId == uiState.currentUserId
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    if (!isMe) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(SettleSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(commenter.take(1).uppercase(), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                        if (!isMe) {
                            Text(commenter, style = MaterialTheme.typography.labelSmall, color = SettleTextSecondary)
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isMe) SettlePrimary else SettleSurface,
                            border = if (isMe) null else androidx.compose.foundation.BorderStroke(1.dp, SettleDivider)
                        ) {
                            Text(
                                text = comment.text,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (isMe) SettleOnPrimary else SettleTextPrimary
                            )
                        }
                        Text(
                            timeFormat.format(comment.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = SettleTextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
