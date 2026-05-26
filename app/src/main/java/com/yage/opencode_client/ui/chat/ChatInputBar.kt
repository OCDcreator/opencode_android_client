package com.yage.opencode_client.ui.chat

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yage.opencode_client.R
import com.yage.opencode_client.data.model.PermissionRequest
import com.yage.opencode_client.data.model.PermissionResponse
import com.yage.opencode_client.ui.PendingImageUi
import com.yage.opencode_client.ui.theme.uiScaled

@Composable
internal fun ChatInputBar(
    text: String,
    isBusy: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isSpeechConfigured: Boolean,
    pendingImages: List<PendingImageUi>,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    onToggleRecording: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit
) {
    val density = LocalDensity.current
    var textFieldHeightPx by remember { mutableIntStateOf(0) }
    val useVerticalActions = with(density) {
        shouldUseVerticalChatActions(textFieldHeightPx.toDp(), ChatUiTuning.inputActionVerticalThreshold.uiScaled())
    }

    val hasImages = pendingImages.isNotEmpty()
    val anyImageProcessing = pendingImages.any { it.isProcessing || it.error != null }
    val canSend = (text.isNotBlank() || hasImages) && !isTranscribing && !anyImageProcessing

    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        tonalElevation = 0.dp
    ) {
        Column {
            if (hasImages) {
                ImagePreviewRow(
                    images = pendingImages,
                    onRemove = onRemoveImage
                )
            }

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
                    isSpeechConfigured = isSpeechConfigured,
                    useVerticalActions = useVerticalActions,
                    canSend = canSend,
                    onAbort = onAbort,
                    onToggleRecording = onToggleRecording,
                    onPickImage = onPickImage,
                    onSend = onSend
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewRow(
    images: List<PendingImageUi>,
    onRemove: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp.uiScaled(), end = 12.dp.uiScaled(), top = 8.dp.uiScaled()),
        horizontalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
    ) {
        items(items = images, key = { it.id }) { image ->
            ImageThumbnail(
                image = image,
                onRemove = { onRemove(image.id) }
            )
        }
    }
}

@Composable
private fun ImageThumbnail(
    image: PendingImageUi,
    onRemove: () -> Unit
) {
    Box(modifier = Modifier.size(64.dp.uiScaled())) {
        when {
            image.isProcessing -> {
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp.uiScaled())).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp.uiScaled()), strokeWidth = 2.dp.uiScaled())
                }
            }
            image.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp.uiScaled())).background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = image.error,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp.uiScaled())
                    )
                }
            }
            image.thumbnail != null -> {
                val imageBitmap = remember(image.id) { image.thumbnail.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp.uiScaled())),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp.uiScaled())).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(24.dp.uiScaled()))
                }
            }
        }

        // Remove button overlay
        Box(
            modifier = Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(4.dp.roundToPx(), (-4).dp.roundToPx()) }
                .size(20.dp.uiScaled())
                .background(Color(0xCC000000), RoundedCornerShape(50))
        ) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp.uiScaled())
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.image_remove_cd),
                    modifier = Modifier.size(14.dp.uiScaled()),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ChatInputActions(
    isBusy: Boolean,
    isSpeechConfigured: Boolean,
    useVerticalActions: Boolean,
    canSend: Boolean,
    onAbort: () -> Unit,
    onToggleRecording: () -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit
) {
    if (useVerticalActions) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp.uiScaled()), horizontalAlignment = Alignment.CenterHorizontally) {
            ChatInputActionButton(
                isBusy = isBusy,
                isSpeechConfigured = isSpeechConfigured,
                canSend = canSend,
                onAbort = onAbort,
                onToggleRecording = onToggleRecording,
                onPickImage = onPickImage,
                onSend = onSend
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp.uiScaled()), verticalAlignment = Alignment.CenterVertically) {
            ChatInputActionButton(
                isBusy = isBusy,
                isSpeechConfigured = isSpeechConfigured,
                canSend = canSend,
                onAbort = onAbort,
                onToggleRecording = onToggleRecording,
                onPickImage = onPickImage,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun ChatInputActionButton(
    isBusy: Boolean,
    isSpeechConfigured: Boolean,
    canSend: Boolean,
    onAbort: () -> Unit,
    onToggleRecording: () -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit
) {
    if (isBusy) {
        IconButton(onClick = onAbort, modifier = Modifier.size(40.dp.uiScaled())) {
            Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop_cd), tint = MaterialTheme.colorScheme.error)
        }
    }
    if (!isBusy) {
        IconButton(onClick = onPickImage) {
            Icon(
                Icons.Default.AddPhotoAlternate,
                contentDescription = stringResource(R.string.image_add_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    IconButton(onClick = onToggleRecording, enabled = isSpeechConfigured && !isBusy) {
        Icon(
            Icons.Default.Mic,
            contentDescription = stringResource(R.string.speech_cd),
            tint = when {
                !isSpeechConfigured -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
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
