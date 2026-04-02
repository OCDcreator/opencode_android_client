package com.yage.opencode_client.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yage.opencode_client.R
import com.yage.opencode_client.data.model.PermissionRequest
import com.yage.opencode_client.data.model.PermissionResponse
import com.yage.opencode_client.ui.theme.uiScaled

@Composable
internal fun ChatInputBar(
    text: String,
    isBusy: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isSpeechConfigured: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    onToggleRecording: () -> Unit
) {
    val density = LocalDensity.current
    var textFieldHeightPx by remember { mutableIntStateOf(0) }
    val useVerticalActions = with(density) {
        shouldUseVerticalChatActions(textFieldHeightPx.toDp(), ChatUiTuning.inputActionVerticalThreshold.uiScaled())
    }

    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp.uiScaled()),
            verticalAlignment = if (useVerticalActions) Alignment.Bottom else Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).onGloballyPositioned { textFieldHeightPx = it.size.height },
                placeholder = { Text(stringResource(R.string.type_message)) },
                maxLines = 4,
                enabled = true
            )
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
            ChatInputActions(
                isBusy = isBusy,
                isRecording = isRecording,
                isTranscribing = isTranscribing,
                isSpeechConfigured = isSpeechConfigured,
                useVerticalActions = useVerticalActions,
                canSend = text.isNotBlank() && !isTranscribing,
                onAbort = onAbort,
                onToggleRecording = onToggleRecording,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun ChatInputActions(
    isBusy: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isSpeechConfigured: Boolean,
    useVerticalActions: Boolean,
    canSend: Boolean,
    onAbort: () -> Unit,
    onToggleRecording: () -> Unit,
    onSend: () -> Unit
) {
    if (useVerticalActions) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp.uiScaled()), horizontalAlignment = Alignment.CenterHorizontally) {
            ChatInputActionButton(
                isBusy = isBusy,
                isRecording = isRecording,
                isTranscribing = isTranscribing,
                isSpeechConfigured = isSpeechConfigured,
                canSend = canSend,
                onAbort = onAbort,
                onToggleRecording = onToggleRecording,
                onSend = onSend
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp.uiScaled()), verticalAlignment = Alignment.CenterVertically) {
            ChatInputActionButton(
                isBusy = isBusy,
                isRecording = isRecording,
                isTranscribing = isTranscribing,
                isSpeechConfigured = isSpeechConfigured,
                canSend = canSend,
                onAbort = onAbort,
                onToggleRecording = onToggleRecording,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun ChatInputActionButton(
    isBusy: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isSpeechConfigured: Boolean,
    canSend: Boolean,
    onAbort: () -> Unit,
    onToggleRecording: () -> Unit,
    onSend: () -> Unit
) {
    if (isBusy) {
        IconButton(onClick = onAbort, modifier = Modifier.size(40.dp.uiScaled())) {
            Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop_cd), tint = MaterialTheme.colorScheme.error)
        }
    }
    IconButton(onClick = onToggleRecording, enabled = !isTranscribing) {
        if (isTranscribing) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp.uiScaled()), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Default.Mic,
                contentDescription = stringResource(R.string.speech_cd),
                tint = when {
                    isRecording -> Color.Red
                    isSpeechConfigured -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                }
            )
        }
    }
    IconButton(onClick = onSend, enabled = canSend) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send_cd))
    }
}

@Composable
internal fun ChatPermissionCard(
    permission: PermissionRequest,
    onRespond: (PermissionResponse) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp.uiScaled()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp.uiScaled())) {
            Text(
                stringResource(R.string.permission_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.size(8.dp.uiScaled()))
            Text(
                permission.permission ?: stringResource(R.string.unknown_permission),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            permission.metadata?.filepath?.let {
                SelectionContainer {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.size(16.dp.uiScaled()))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onRespond(PermissionResponse.REJECT) }) {
                    Text(stringResource(R.string.reject))
                }
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                TextButton(onClick = { onRespond(PermissionResponse.ONCE) }) {
                    Text(stringResource(R.string.allow_once))
                }
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                Button(onClick = { onRespond(PermissionResponse.ALWAYS) }) {
                    Text(stringResource(R.string.always_allow))
                }
            }
        }
    }
}
