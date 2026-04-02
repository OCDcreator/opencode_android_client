package com.yage.opencode_client.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yage.opencode_client.R
import com.yage.opencode_client.data.model.AgentInfo
import com.yage.opencode_client.data.model.ConfigProvider
import com.yage.opencode_client.data.model.Session
import com.yage.opencode_client.data.model.SessionStatus
import com.yage.opencode_client.ui.AppState
import com.yage.opencode_client.ui.session.SessionList
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.yage.opencode_client.ui.theme.uiScaled
import java.util.Locale

internal data class ChatTopBarState(
    val sessions: List<Session>,
    val currentSessionId: String?,
    val sessionStatuses: Map<String, SessionStatus>,
    val hasMoreSessions: Boolean,
    val isLoadingMoreSessions: Boolean,
    val expandedSessionIds: Set<String> = emptySet(),
    val agents: List<AgentInfo>,
    val selectedAgent: String,
    val availableModels: List<AppState.ModelOption>,
    val selectedModelIndex: Int,
    val providers: List<ConfigProvider> = emptyList(),
    val contextUsage: AppState.ContextUsage?,
    val showSettingsButton: Boolean = true,
    val showNewSessionInTopBar: Boolean = true,
    val showSessionListInTopBar: Boolean = true
)

internal data class ChatTopBarActions(
    val onSelectSession: (String) -> Unit,
    val onCreateSession: () -> Unit,
    val onDeleteSession: (String) -> Unit,
    val onLoadMoreSessions: () -> Unit,
    val onToggleSessionExpanded: (String) -> Unit = {},
    val onSelectAgent: (String) -> Unit,
    val onSelectModel: (Int) -> Unit,
    val onNavigateToSettings: () -> Unit = {},
    val onRenameSession: (String) -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    state: ChatTopBarState,
    actions: ChatTopBarActions,
    modifier: Modifier = Modifier
) {
    val currentSession = state.sessions.find { it.id == state.currentSessionId }
    var showSessionSheet by remember { mutableStateOf(false) }
    var showAgentMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp.uiScaled()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp.uiScaled(), vertical = 8.dp.uiScaled())
        ) {
            val titleText = currentSession?.title
                ?: currentSession?.directory?.split("/")?.lastOrNull()
                ?: stringResource(R.string.opencode)
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp.uiScaled()))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.showSessionListInTopBar) {
                        IconButton(
                            onClick = { showSessionSheet = true },
                            modifier = Modifier.size(36.dp.uiScaled())
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = stringResource(R.string.sessions_cd),
                                modifier = Modifier.size(20.dp.uiScaled())
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp.uiScaled()))
                    }

                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.size(36.dp.uiScaled())
                    ) {
                        Icon(
                            Icons.Default.Edit,
                                contentDescription = stringResource(R.string.rename_session_cd),
                            modifier = Modifier.size(20.dp.uiScaled())
                        )
                    }

                    if (state.showNewSessionInTopBar) {
                        Spacer(modifier = Modifier.width(4.dp.uiScaled()))
                        IconButton(
                            onClick = actions.onCreateSession,
                            modifier = Modifier.size(36.dp.uiScaled())
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.new_session_cd),
                                modifier = Modifier.size(20.dp.uiScaled())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp.uiScaled())
                ) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        Surface(
                            onClick = { showModelMenu = true },
                            shape = RoundedCornerShape(50.dp.uiScaled()),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp.uiScaled(), vertical = 6.dp.uiScaled())
                            ) {
                                Text(
                                    text = state.availableModels.getOrNull(state.selectedModelIndex)?.shortName
                                        ?: stringResource(R.string.model),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.switch_model_cd),
                                    modifier = Modifier.size(14.dp.uiScaled()),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        if (showModelMenu) {
                            ModelPickerPopup(
                                models = state.availableModels,
                                providers = state.providers,
                                selectedIndex = state.selectedModelIndex,
                                onSelect = { index ->
                                    actions.onSelectModel(index)
                                    showModelMenu = false
                                },
                                onDismiss = { showModelMenu = false }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        Surface(
                            onClick = { showAgentMenu = true },
                            shape = RoundedCornerShape(50.dp.uiScaled()),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp.uiScaled(), vertical = 6.dp.uiScaled())
                            ) {
                                Text(
                                    text = state.selectedAgent,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.switch_agent_cd),
                                    modifier = Modifier.size(14.dp.uiScaled()),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showAgentMenu,
                            onDismissRequest = { showAgentMenu = false }
                        ) {
                            if (state.agents.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.no_agents),
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    },
                                    onClick = { }
                                )
                            }
                            state.agents.forEach { agent ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            agent.name,
                                            color = if (agent.name == state.selectedAgent)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        actions.onSelectAgent(agent.name)
                                        showAgentMenu = false
                                    }
                                )
                            }
                        }
                    }

                    state.contextUsage?.let { usage ->
                        ContextUsageRing(usage = usage)
                    }

                    if (state.showSettingsButton) {
                        IconButton(
                            onClick = actions.onNavigateToSettings,
                            modifier = Modifier.size(36.dp.uiScaled())
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                modifier = Modifier.size(20.dp.uiScaled())
                            )
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    if (showSessionSheet) {
        ModalBottomSheet(onDismissRequest = { showSessionSheet = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatUiTuning.sessionSheetHeight.uiScaled())
            ) {
                SessionList(
                    sessions = state.sessions,
                    currentSessionId = state.currentSessionId,
                    sessionStatuses = state.sessionStatuses,
                    hasMoreSessions = state.hasMoreSessions,
                    isLoadingMoreSessions = state.isLoadingMoreSessions,
                    expandedSessionIds = state.expandedSessionIds,
                    onSelectSession = {
                        actions.onSelectSession(it)
                        showSessionSheet = false
                    },
                    onCreateSession = {
                        actions.onCreateSession()
                        showSessionSheet = false
                    },
                    onDeleteSession = {
                        actions.onDeleteSession(it)
                        showSessionSheet = false
                    },
                    onLoadMoreSessions = actions.onLoadMoreSessions,
                    onToggleSessionExpanded = actions.onToggleSessionExpanded,
                    onOpenSettings = null
                )
            }
        }
    }

    if (showRenameDialog) {
        var renameText by remember(currentSession?.id) {
            mutableStateOf(
                currentSession?.title
                    ?: currentSession?.directory?.split("/")?.lastOrNull()
                    ?: ""
            )
        }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_session_dialog)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.session_title_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            actions.onRenameSession(renameText.trim())
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
internal fun ContextUsageRing(usage: AppState.ContextUsage) {
    val ringColor = when {
        usage.percentage >= 0.9f -> MaterialTheme.colorScheme.error
        usage.percentage >= 0.7f -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier.size(ChatUiTuning.contextRingOuterSize.uiScaled()),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(ChatUiTuning.contextRingInnerSize.uiScaled()),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            strokeWidth = 3.dp
        )
        CircularProgressIndicator(
            progress = { usage.percentage },
            modifier = Modifier.size(ChatUiTuning.contextRingInnerSize.uiScaled()),
            color = ringColor,
            strokeWidth = 3.dp
        )
    }
}

@Composable
internal fun ChatEmptyState(
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Chat,
                contentDescription = null,
                modifier = Modifier.size(64.dp.uiScaled()),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp.uiScaled()))
            Text(
                if (isConnected) stringResource(R.string.select_or_create_session) else stringResource(R.string.connect_to_server),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp.uiScaled()))
            if (!isConnected) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.padding(horizontal = 24.dp.uiScaled(), vertical = 12.dp.uiScaled())
                ) {
                    Text(stringResource(R.string.connect))
                }
            }
        }
    }
}

private data class GroupedModel(
    val providerName: String,
    val model: AppState.ModelOption,
    val index: Int
)

@Composable
private fun ModelPickerPopup(
    models: List<AppState.ModelOption>,
    providers: List<ConfigProvider>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }
    val searchHint = stringResource(R.string.search_models)
    val noModelsText = stringResource(R.string.no_models)
    val focusRequester = remember { FocusRequester() }

    val providerNameMap = remember(providers) {
        providers.associate { it.id to (it.name ?: it.id) }
    }

    val grouped = remember(models, providers, filterQuery) {
        val query = filterQuery.lowercase(Locale.getDefault())
        models.mapIndexed { index, model ->
            val pName = providerNameMap[model.providerId] ?: model.providerId
            GroupedModel(pName, model, index)
        }.filter {
            query.isEmpty() ||
                it.model.displayName.lowercase(Locale.getDefault()).contains(query) ||
                it.model.modelId.lowercase(Locale.getDefault()).contains(query) ||
                it.providerName.lowercase(Locale.getDefault()).contains(query)
        }.groupBy { it.providerName }
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, with(LocalDensity.current) { 4.dp.uiScaled().roundToPx() }),
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .fillMaxHeight(0.65f),
            shape = RoundedCornerShape(16.dp.uiScaled()),
            tonalElevation = 4.dp.uiScaled(),
            shadowElevation = 8.dp.uiScaled(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp.uiScaled(), vertical = 12.dp.uiScaled())
                ) {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                searchHint,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp.uiScaled()),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp.uiScaled()),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (grouped.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp.uiScaled()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            noModelsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        grouped.forEach { (providerName, items) ->
                            item(key = "header_$providerName") {
                                Text(
                                    text = providerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .padding(horizontal = 20.dp.uiScaled(), vertical = 8.dp.uiScaled())
                                )
                            }
                            items(items = items, key = { "model_${it.index}" }) { item ->
                                val selected = item.index == selectedIndex
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (selected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else
                                        Color.Transparent,
                                    shape = RoundedCornerShape(8.dp.uiScaled())
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelect(item.index) }
                                            .padding(horizontal = 20.dp.uiScaled(), vertical = 12.dp.uiScaled())
                                    ) {
                                        Text(
                                            text = item.model.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selected)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (selected) {
                                            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp.uiScaled()),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
