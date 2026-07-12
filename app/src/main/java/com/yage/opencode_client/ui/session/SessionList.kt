package com.yage.opencode_client.ui.session

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yage.opencode_client.data.model.Session
import com.yage.opencode_client.data.model.SessionStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.res.stringResource
import com.yage.opencode_client.R
import com.yage.opencode_client.ui.theme.uiScaled
import kotlin.math.roundToInt

private enum class SwipeAnchor { Start, End }

@Composable
private fun formatRelativeTime(updatedMs: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        updatedMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

@Composable
private fun sessionStatusLabel(status: SessionStatus?): String? = when {
    status == null -> null
    status.isBusy -> stringResource(R.string.session_status_running)
    status.isRetry -> stringResource(R.string.session_status_retrying)
    status.isIdle -> stringResource(R.string.session_status_idle)
    else -> null
}

@Composable
private fun sessionStatusColor(status: SessionStatus?): androidx.compose.ui.graphics.Color = when {
    status?.isBusy == true -> MaterialTheme.colorScheme.primary
    status?.isRetry == true -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeRevealRow(
    dragState: AnchoredDraggableState<SwipeAnchor>,
    enabled: Boolean,
    onDelete: () -> Unit,
    altBg: Boolean,
    isSelected: Boolean,
    isBusy: Boolean,
    displayName: String,
    updatedTime: Long? = null,
    status: SessionStatus? = null,
    onSelect: () -> Unit,
    depth: Int = 0,
    hasChildren: Boolean = false,
    isCollapsed: Boolean = true,
    onToggleCollapse: (() -> Unit)? = null
) {
    val selectedBackgroundColor = lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primaryContainer,
        0.30f
    )
    val swipeRevealBackgroundColor = lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primaryContainer,
        0.28f
    )
    val rowBackgroundColor = when {
        isSelected -> selectedBackgroundColor
        altBg -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }
    val titleColor = if (isBusy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    // Fling/threshold config moved out of the deprecated AnchoredDraggableState constructor
    // into AnchoredDraggableDefaults.flingBehavior, passed to Modifier.anchoredDraggable.
    // Preserves the previous behavior: 50% positional threshold.
    val dragFlingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = dragState,
        positionalThreshold = { total: Float -> total * 0.5f }
    )

    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(swipeRevealBackgroundColor)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_session_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp.uiScaled())
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(x = -dragState.requireOffset().roundToInt(), y = 0) }
                .anchoredDraggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    reverseDirection = true,
                    flingBehavior = dragFlingBehavior
                )
                .background(rowBackgroundColor)
                .clickable(onClick = onSelect)
                .padding(
                    start = ((12 + depth * 24).dp).uiScaled(),
                    end = 12.dp.uiScaled(),
                    top = 10.dp.uiScaled(),
                    bottom = 10.dp.uiScaled()
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren && onToggleCollapse != null) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(24.dp.uiScaled())
                ) {
                    Icon(
                        if (isCollapsed) Icons.Default.ChevronRight else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isCollapsed) stringResource(R.string.expand) else stringResource(R.string.collapse),
                        modifier = Modifier.size(16.dp.uiScaled())
                    )
                }
            } else if (hasChildren) {
                Spacer(modifier = Modifier.size(24.dp.uiScaled()))
            }
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = titleColor
                )
                val statusLabel = sessionStatusLabel(status)
                if (updatedTime != null || statusLabel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (updatedTime != null) {
                            Text(
                                text = formatRelativeTime(updatedTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (statusLabel != null && updatedTime != null) {
                            Text(
                                text = "  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (statusLabel != null) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = sessionStatusColor(status)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionList(
    sessions: List<Session>,
    currentSessionId: String?,
    sessionStatuses: Map<String, SessionStatus> = emptyMap(),
    hasMoreSessions: Boolean = false,
    isLoadingMoreSessions: Boolean = false,
    expandedSessionIds: Set<String> = emptySet(),
    totalSessionCount: Int = 0,
    showAllSessions: Boolean = false,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onToggleSessionExpanded: (String) -> Unit = {},
    onLoadMoreSessions: () -> Unit = {},
    onShowAllSessions: ((Boolean) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val tree = remember(sessions) { buildSessionTree(sessions) }
    val visibleRows = remember(tree, expandedSessionIds) {
        flattenVisibleTree(tree, expandedSessionIds)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(listState, visibleRows.size, hasMoreSessions, isLoadingMoreSessions) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible != null && hasMoreSessions && !isLoadingMoreSessions && lastVisible >= visibleRows.lastIndex - 2) {
                    onLoadMoreSessions()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp.uiScaled(), vertical = 8.dp.uiScaled()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.sessions_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.weight(1f))
                if (showAllSessions && onShowAllSessions != null) {
                    TextButton(onClick = { onShowAllSessions(false) }) {
                        Text(stringResource(R.string.sessions_show_filtered))
                    }
                }
                TextButton(onClick = onCreateSession) {
                    Text(stringResource(R.string.new_session))
                }
                if (onOpenSettings != null) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("session_list")
        ) {
            // Empty state: no sessions after directory filtering, but the server has some.
            if (visibleRows.isEmpty() && totalSessionCount > 0 && onShowAllSessions != null) {
                item(key = "empty-filtered") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp.uiScaled()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.sessions_empty_filtered),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp.uiScaled()))
                        Text(
                            text = stringResource(R.string.sessions_empty_filtered_hint, totalSessionCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp.uiScaled()))
                        TextButton(onClick = { onShowAllSessions(true) }) {
                            Text(stringResource(R.string.sessions_show_all))
                        }
                    }
                }
            }
            itemsIndexed(visibleRows, key = { _, (node, _) -> node.session.id }) { index, (node, depth) ->
                val session = node.session
                val isSelected = session.id == currentSessionId
                val altBg = index % 2 == 1
                val hasChildren = node.children.isNotEmpty()
                val isExpanded = expandedSessionIds.contains(session.id)
                val density = LocalDensity.current
                val deleteWidthPx = with(density) { 56.dp.uiScaled().toPx() }
                val dragState = remember(deleteWidthPx) {
                    AnchoredDraggableState(
                        initialValue = SwipeAnchor.Start,
                        anchors = DraggableAnchors {
                            SwipeAnchor.Start at 0f
                            SwipeAnchor.End at deleteWidthPx
                        }
                    )
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    SwipeRevealRow(
                        dragState = dragState,
                        enabled = !listState.isScrollInProgress,
                        onDelete = { onDeleteSession(session.id) },
                        altBg = altBg,
                        isSelected = isSelected,
                        isBusy = sessionStatuses[session.id]?.isBusy == true,
                        displayName = session.displayName,
                        updatedTime = session.time?.updated,
                        status = sessionStatuses[session.id],
                        onSelect = { onSelectSession(session.id) },
                        depth = depth,
                        hasChildren = hasChildren,
                        isCollapsed = !isExpanded,
                        onToggleCollapse = if (hasChildren) { { onToggleSessionExpanded(session.id) } } else null
                    )
                    if (index < visibleRows.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp.uiScaled()),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            if (isLoadingMoreSessions) {
                item(key = "load_more_progress") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp.uiScaled()),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp.uiScaled()), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}
