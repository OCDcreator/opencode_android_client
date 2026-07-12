package com.yage.opencode_client.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.window.Dialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    hideMicIcon: Boolean,
    pendingImages: List<PendingImageUi>,
    agentActivityText: String? = null,
    agentStartedAtMillis: Long? = null,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    onToggleRecording: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit
) {
    val hasImages = pendingImages.isNotEmpty()
    val anyImageProcessing = pendingImages.any { it.isProcessing || it.error != null }
    val canSend = (text.isNotBlank() || hasImages) && !isTranscribing && !anyImageProcessing
    var previewImage by remember { mutableStateOf<PendingImageUi?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(start = 12.dp.uiScaled(), end = 12.dp.uiScaled(), top = 8.dp.uiScaled(), bottom = 8.dp.uiScaled())) {
            if (hasImages) {
                ImagePreviewRow(
                    images = pendingImages,
                    onRemove = onRemoveImage,
                    onPreview = { previewImage = it }
                )
            }

            // Secondary actions row — image + mic above the input field
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBusy) {
                    IconButton(onClick = onAbort, modifier = Modifier.size(40.dp.uiScaled())) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop_cd), tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (!isBusy) {
                    IconButton(onClick = onPickImage, modifier = Modifier.size(40.dp.uiScaled())) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.image_add_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!hideMicIcon) {
                    IconButton(
                        onClick = onToggleRecording,
                        enabled = isSpeechConfigured && !isBusy,
                        modifier = Modifier.size(40.dp.uiScaled())
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = stringResource(R.string.speech_cd),
                            tint = when {
                                isRecording -> MaterialTheme.colorScheme.error
                                !isSpeechConfigured -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Agent activity status row — shows what the agent is doing + elapsed time
            if (isBusy && (agentActivityText != null || agentStartedAtMillis != null)) {
                AgentActivityRow(
                    activityText = agentActivityText,
                    startedAtMillis = agentStartedAtMillis
                )
            }

            // Input row — text field + send button, full width
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.type_message)) },
                    maxLines = 4,
                    enabled = true
                )
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(40.dp.uiScaled())
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send_cd)
                    )
                }
            }
        }

        // Full-screen image preview dialog
        if (previewImage != null) {
            ImagePreviewDialog(
                image = previewImage!!,
                onDismiss = { previewImage = null }
            )
        }
    }
}

@Composable
private fun AgentActivityRow(
    activityText: String?,
    startedAtMillis: Long?
) {
    var nowMillis by remember(startedAtMillis) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis) {
        while (startedAtMillis != null) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp.uiScaled()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (activityText != null) {
            Text(
                text = activityText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (startedAtMillis != null) {
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
            Text(
                text = formatElapsed(nowMillis - startedAtMillis),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatElapsed(elapsedMillis: Long): String {
    val seconds = (elapsedMillis.coerceAtLeast(0L) / 1_000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun ImagePreviewRow(
    images: List<PendingImageUi>,
    onRemove: (String) -> Unit,
    onPreview: (PendingImageUi) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp.uiScaled(), end = 12.dp.uiScaled(), top = 8.dp.uiScaled()),
        horizontalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
    ) {
        items(items = images, key = { it.id }) { image ->
            ImageThumbnail(
                image = image,
                onRemove = { onRemove(image.id) },
                onPreview = { onPreview(image) }
            )
        }
    }
}

@Composable
private fun ImageThumbnail(
    image: PendingImageUi,
    onRemove: () -> Unit,
    onPreview: () -> Unit
) {
    Box(modifier = Modifier.size(64.dp.uiScaled()).clickable(enabled = image.thumbnail != null) { onPreview() }) {
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
private fun ImagePreviewDialog(
    image: PendingImageUi,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (image.dataUri != null && image.dataUri.startsWith("data:image")) {
                    // Decode base64 from data URI
                    val base64Data = image.dataUri.substringAfter(",")
                    val imageBytes = remember(image.id) {
                        android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    }
                    val bitmap = remember(image.id) {
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)?.asImageBitmap()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = image.filename,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(0.7f),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("Failed to load image", color = Color.White)
                    }
                } else if (image.thumbnail != null) {
                    val bitmap = remember(image.id) { image.thumbnail.asImageBitmap() }
                    Image(
                        bitmap = bitmap,
                        contentDescription = image.filename,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .fillMaxHeight(0.7f),
                        contentScale = ContentScale.Fit
                    )
                }
                if (image.filename != null) {
                    Spacer(modifier = Modifier.size(16.dp.uiScaled()))
                    Text(
                        text = image.filename,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
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
