package com.example.splitwsie.presentation.group

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.splitwsie.domain.model.GroupCategory
import com.example.splitwsie.presentation.components.SettlePrimaryButton
import com.example.splitwsie.presentation.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val state by viewModel.detailsState.collectAsState()
    val group = state.group
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var groupName by remember(group) { mutableStateOf(group?.name ?: "") }
    var groupDescription by remember(group) { mutableStateOf(group?.description ?: "") }
    var selectedCategory by remember(group) { 
        mutableStateOf(GroupCategory.fromName(group?.category ?: "Other")) 
    }
    var simplifyDebts by remember(group) { mutableStateOf(group?.simplifyDebts ?: false) }

    LaunchedEffect(groupId) {
        viewModel.loadGroupDetails(groupId)
    }

    LaunchedEffect(Unit) {
        viewModel.detailsEvent.collect { event ->
            when (event) {
                is GroupDetailsEvent.Message -> {
                    scope.launch { snackbarHostState.showSnackbar(event.text) }
                    onNavigateBack() // Go back when updated successfully
                }
                is GroupDetailsEvent.Error -> {
                    scope.launch { snackbarHostState.showSnackbar(event.text) }
                }
                is GroupDetailsEvent.ExportSummary -> {}
                is GroupDetailsEvent.GroupDeleted -> onNavigateBack()
            }
        }
    }

    Scaffold(
        containerColor = SettleBackground,
        topBar = {
            TopAppBar(
                title = { Text("Group Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SettleTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettleBackground)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator(color = SettlePrimary)
            }
            return@Scaffold
        }

        if (group == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(state.error ?: "Group not found")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SettlePrimary,
                        unfocusedBorderColor = SettleDivider
                    )
                )

                OutlinedTextField(
                    value = groupDescription,
                    onValueChange = { groupDescription = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SettlePrimary,
                        unfocusedBorderColor = SettleDivider
                    )
                )

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleMedium,
                    color = SettleTextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroupCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text("${category.emoji} ${category.displayName}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SettlePrimary.copy(alpha = 0.2f),
                                selectedLabelColor = SettlePrimary
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Simplify Debts", style = MaterialTheme.typography.titleMedium, color = SettleTextPrimary)
                            Text(
                                text = "Minimizes the total number of transactions between members.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = simplifyDebts,
                            onCheckedChange = { simplifyDebts = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SettlePrimary,
                                checkedTrackColor = SettlePrimary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            SettlePrimaryButton(
                text = "Save Changes",
                onClick = { viewModel.updateGroup(groupId, groupName, groupDescription, selectedCategory.name, simplifyDebts) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                enabled = groupName.isNotBlank() && state.isOwner
            )
        }
    }
}
