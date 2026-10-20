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
fun GroupCreationScreen(
    onNavigateBack: () -> Unit,
    onGroupCreated: (String) -> Unit,
    viewModel: GroupViewModel = hiltViewModel()
) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(GroupCategory.TRIP) }
    val isCreating by viewModel.isCreating.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // React to creation success/failure events
    LaunchedEffect(Unit) {
        viewModel.creationEvent.collect { event ->
            when (event) {
                is GroupCreationEvent.Success -> onGroupCreated(event.groupId)
                is GroupCreationEvent.Error -> scope.launch {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        containerColor = SettleBackground,
        topBar = {
            TopAppBar(
                title = { },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "Create a group",
                    style = MaterialTheme.typography.displayLarge,
                    color = SettleTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Let's start settling.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SettleTextSecondary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    placeholder = { Text("e.g. Lonavala Trip") },
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
                
                // Using a grid or a wrapped row for categories
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
            }

            Spacer(modifier = Modifier.weight(1f))

            SettlePrimaryButton(
                text = "Create Group",
                onClick = { viewModel.createGroup(groupName, groupDescription, selectedCategory.name) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                enabled = groupName.isNotBlank(),
                isLoading = isCreating
            )
        }
    }
}
