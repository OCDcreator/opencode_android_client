# Image Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add multi-image selection, preview, and sending support to the OpenCode Android client chat.

**Architecture:** Sealed `PartInput` (Text/File) replaces flat data class. UI state (`PendingImageUi`) lives in `AppState`; base64 payloads in ViewModel private map. New `ImageAttachmentCompressor` handles decode/scale/compress/base64. Repository builds mixed parts list. UI adds photo picker button and thumbnail preview row above input bar.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt DI, kotlinx.serialization, Retrofit, `PickMultipleVisualMedia`, `ExifInterface`

**Spec:** `docs/superpowers/specs/2026-05-26-image-input-design.md`

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `gradle/libs.versions.toml` | Add exifinterface version |
| Modify | `app/build.gradle.kts` | Add exifinterface dependency |
| Modify | `app/src/main/res/values/strings.xml` | Add image-related string resources |
| Modify | `app/src/main/java/com/yage/opencode_client/data/api/OpenCodeApi.kt` | Replace flat PartInput with sealed interface |
| Create | `app/src/main/java/com/yage/opencode_client/data/image/ImageAttachmentCompressor.kt` | Image decode, scale, compress, base64 |
| Modify | `app/src/main/java/com/yage/opencode_client/data/repository/OpenCodeRepository.kt` | Update sendMessage for image parts |
| Modify | `app/src/main/java/com/yage/opencode_client/ui/MainViewModel.kt` | Add pendingImages to AppState, addImage/removeImage, update sendMessage, session cleanup |
| Modify | `app/src/main/java/com/yage/opencode_client/ui/MainViewModelSessionActions.kt` | Update launchSendMessage signature |
| Modify | `app/src/main/java/com/yage/opencode_client/ui/chat/ChatInputBar.kt` | Add image button, preview row, remove callback |
| Modify | `app/src/main/java/com/yage/opencode_client/ui/chat/ChatScreen.kt` | Add PickMultipleVisualMedia launcher, wire callbacks |
| Modify | `app/proguard-rules.pro` | Add keep rules for sealed PartInput |

---

### Task 1: Add Dependencies and String Resources

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add exifinterface version to libs.versions.toml**

Add to `[versions]` section after line 26:
```toml
exifinterface = "1.3.7"
```

Add to `[libraries]` section after the `androidx-security-crypto` line:
```toml
androidx-exifinterface = { group = "androidx.exifinterface", name = "exifinterface", version.ref = "exifinterface" }
```

- [ ] **Step 2: Add dependency to build.gradle.kts**

Add after line 108 (`implementation(libs.androidx.compose.material3.windowsizeclass)`):
```kotlin
implementation(libs.androidx.exifinterface)
```

- [ ] **Step 3: Add string resources to strings.xml**

Add before the closing `</resources>` tag:
```xml
<!-- Image Input -->
<string name="image_add_cd">Select image</string>
<string name="image_remove_cd">Remove image</string>
<string name="image_max_reached">Maximum 5 images allowed</string>
<string name="image_compression_failed">Failed to process image: %1$s</string>
<string name="image_too_large">Image too large after compression</string>
```

- [ ] **Step 4: Verify build syncs**

Run: `./gradlew assembleDebug --dry-run`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/res/values/strings.xml
git commit -m "feat(image): add exifinterface dependency and string resources"
```

---

### Task 2: Sealed PartInput + ProGuard Rules

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/data/api/OpenCodeApi.kt`
- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: Replace flat PartInput with sealed interface in OpenCodeApi.kt**

Replace the existing `PartInput` data class (lines 154-159):
```kotlin
    @kotlinx.serialization.Serializable
    data class PartInput(
        val type: String = "text",
        val text: String
    )
```

With sealed interface:
```kotlin
    @kotlinx.serialization.Serializable
    sealed interface PartInput {
        @kotlinx.serialization.Serializable
        @kotlinx.serialization.SerialName("text")
        data class Text(val text: String) : PartInput

        @kotlinx.serialization.Serializable
        @kotlinx.serialization.SerialName("file")
        data class File(
            val mime: String,
            val url: String,
            val filename: String? = null
        ) : PartInput
    }
```

- [ ] **Step 2: Update the single PartInput construction in OpenCodeRepository.kt**

In `OpenCodeRepository.kt` line 129, change:
```kotlin
parts = listOf(PromptRequest.PartInput(text = text)),
```
To:
```kotlin
parts = listOf(PromptRequest.PartInput.Text(text = text)),
```

- [ ] **Step 3: Add ProGuard keep rules**

Append to `app/proguard-rules.pro`:
```
# Keep sealed PartInput discriminator for kotlinx.serialization
-keep class com.yage.opencode_client.data.api.PromptRequest$PartInput { *; }
-keep class * implements com.yage.opencode_client.data.api.PromptRequest$PartInput { *; }
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/data/api/OpenCodeApi.kt app/src/main/java/com/yage/opencode_client/data/repository/OpenCodeRepository.kt app/proguard-rules.pro
git commit -m "feat(image): replace flat PartInput with sealed Text/File interface"
```

---

### Task 3: ImageAttachmentCompressor

**Files:**
- Create: `app/src/main/java/com/yage/opencode_client/data/image/ImageAttachmentCompressor.kt`

- [ ] **Step 1: Create the compressor utility**

```kotlin
package com.yage.opencode_client.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageAttachmentCompressor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class CompressedImage(
        val id: String,
        val dataUri: String,
        val filename: String?,
        val byteSize: Int,
        val thumbnail: Bitmap
    )

    suspend fun compress(uri: ContentResolver, imageUri: android.net.Uri): CompressedImage =
        withContext(Dispatchers.IO) {
            val filename = queryFilename(uri, imageUri)

            // Step 1: Decode bounds
            val bounds = decodeBounds(uri, imageUri)

            // Step 2: Read EXIF orientation
            val orientation = readExifOrientation(uri, imageUri)

            // Step 3: Calculate sample size
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            val sampleSize = if (longEdge > TARGET_LONG_EDGE) {
                Integer.highestOneBit(longEdge / TARGET_LONG_EDGE)
            } else 1

            // Step 4: Decode sampled bitmap
            val sampled = decodeSampled(uri, imageUri, sampleSize)

            // Step 5: Apply EXIF rotation
            val rotated = applyRotation(sampled, orientation)
            if (rotated !== sampled) sampled.recycle()

            // Step 6: Scale to exact target
            val scaled = scaleToTarget(rotated)
            if (scaled !== rotated) rotated.recycle()

            // Step 7: Flatten alpha to white (PNG with transparency → JPEG)
            val flattened = flattenAlpha(scaled)
            if (flattened !== scaled) scaled.recycle()

            // Step 8: Compress to JPEG
            val jpegBytes = compressJpeg(flattened, JPEG_QUALITY)

            // Step 9: Build thumbnail
            val thumbnail = createThumbnail(flattened)
            flattened.recycle()

            // Step 10: Base64 encode
            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            ensureActive()

            val dataUri = "data:image/jpeg;base64,$base64"
            val byteSize = base64.length

            CompressedImage(
                id = UUID.randomUUID().toString(),
                dataUri = dataUri,
                filename = filename,
                byteSize = byteSize,
                thumbnail = thumbnail
            )
        }

    private fun queryFilename(resolver: ContentResolver, uri: android.net.Uri): String? {
        return resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    private fun decodeBounds(resolver: ContentResolver, uri: android.net.Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return options
    }

    private fun readExifOrientation(resolver: ContentResolver, uri: android.net.Uri): Int {
        return try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun decodeSampled(resolver: ContentResolver, uri: android.net.Uri, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalStateException("Failed to decode image")
    }

    private fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToTarget(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= TARGET_LONG_EDGE) return bitmap
        val scale = TARGET_LONG_EDGE.toFloat() / longEdge.toFloat()
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun flattenAlpha(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return output
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun createThumbnail(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= THUMBNAIL_SIZE) return bitmap
        val scale = THUMBNAIL_SIZE.toFloat() / longEdge.toFloat()
        val w = (bitmap.width * scale).toInt()
        val h = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    companion object {
        private const val TARGET_LONG_EDGE = 1024
        private const val JPEG_QUALITY = 75
        private const val THUMBNAIL_SIZE = 128
        private const val MAX_BASE64_BYTES = 2 * 1024 * 1024  // 2MB
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/data/image/ImageAttachmentCompressor.kt
git commit -m "feat(image): add ImageAttachmentCompressor utility"
```

---

### Task 4: Update Repository for Image Parts

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/data/repository/OpenCodeRepository.kt`

- [ ] **Step 1: Update sendMessage signature and body**

Replace the existing `sendMessage` method (lines 121-138):
```kotlin
    suspend fun sendMessage(
        sessionId: String,
        text: String,
        agent: String = "build",
        model: Message.ModelInfo? = null,
        directory: String? = null
    ): Result<Unit> = runCatching {
        val request = PromptRequest(
            parts = listOf(PromptRequest.PartInput.Text(text = text)),
            agent = agent,
            model = model?.let { PromptRequest.ModelInput(it.providerId, it.modelId) }
        )
        val response = api.promptAsync(sessionId, effectiveDirectory(directory), request)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string() ?: response.message()
            throw Exception("Send failed ${response.code()}: $errorBody")
        }
    }
```

With:
```kotlin
    suspend fun sendMessage(
        sessionId: String,
        text: String,
        agent: String = "build",
        model: Message.ModelInfo? = null,
        directory: String? = null,
        imageParts: List<PromptRequest.PartInput.File> = emptyList()
    ): Result<Unit> = runCatching {
        val parts = mutableListOf<PromptRequest.PartInput>()
        if (text.isNotEmpty()) {
            parts.add(PromptRequest.PartInput.Text(text = text))
        }
        parts.addAll(imageParts)
        require(parts.isNotEmpty()) { "Message must contain text or images" }
        val request = PromptRequest(
            parts = parts,
            agent = agent,
            model = model?.let { PromptRequest.ModelInput(it.providerId, it.modelId) }
        )
        val response = api.promptAsync(sessionId, effectiveDirectory(directory), request)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string() ?: response.message()
            throw Exception("Send failed ${response.code()}: $errorBody")
        }
    }
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/data/repository/OpenCodeRepository.kt
git commit -m "feat(image): update repository sendMessage to accept image parts"
```

---

### Task 5: Update AppState and ViewModel

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/yage/opencode_client/ui/MainViewModelSessionActions.kt`

- [ ] **Step 1: Add PendingImageUi to AppState and pendingImages field**

In `MainViewModel.kt`, add the data class and field to `AppState` (after `data class AppState(` on line 34, add the field near `inputText` around line 55):

Add before `AppState`:
```kotlin
data class PendingImageUi(
    val id: String,
    val filename: String? = null,
    val thumbnail: android.graphics.Bitmap? = null,
    val byteSize: Int = 0,
    val isProcessing: Boolean = false,
    val error: String? = null
)
```

Add inside `AppState` after `val inputText: String = "",`:
```kotlin
    val pendingImages: List<PendingImageUi> = emptyList(),
```

- [ ] **Step 2: Update MainViewModel constructor to inject ImageAttachmentCompressor**

Replace:
```kotlin
class MainViewModel @Inject constructor(
    internal val repository: OpenCodeRepository,
    private val settingsManager: SettingsManager,
    private val audioRecorderManager: AudioRecorderManager
) : ViewModel() {
```

With:
```kotlin
class MainViewModel @Inject constructor(
    internal val repository: OpenCodeRepository,
    private val settingsManager: SettingsManager,
    private val audioRecorderManager: AudioRecorderManager,
    private val imageCompressor: com.yage.opencode_client.data.image.ImageAttachmentCompressor
) : ViewModel() {
```

- [ ] **Step 3: Add private storage and methods to MainViewModel**

Add after `private var lastHealthCheckTime = 0L`:
```kotlin
    private val imageDataUris = mutableMapOf<String, String>()
    private val compressionJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
```

Add after `clearSpeechError()` method:
```kotlin
    fun addImage(uri: android.net.Uri) {
        val sessionId = _state.value.currentSessionId ?: return
        if (_state.value.pendingImages.size >= 5) {
            _state.update { it.copy(error = "Maximum 5 images allowed") }
            return
        }
        val id = java.util.UUID.randomUUID().toString()
        _state.update { it.copy(pendingImages = it.pendingImages + PendingImageUi(id, isProcessing = true)) }
        val capturedSessionId = sessionId
        val job = viewModelScope.launch {
            try {
                val result = imageCompressor.compress(context.contentResolver, uri)
                if (_state.value.currentSessionId != capturedSessionId) return@launch
                if (!_state.value.pendingImages.any { it.id == id }) return@launch
                if (result.byteSize > 2 * 1024 * 1024) {
                    _state.update { s ->
                        s.copy(pendingImages = s.pendingImages.map {
                            if (it.id == id) it.copy(isProcessing = false, error = "Image too large after compression")
                            else it
                        })
                    }
                    return@launch
                }
                imageDataUris[id] = result.dataUri
                _state.update { s ->
                    s.copy(pendingImages = s.pendingImages.map {
                        if (it.id == id) it.copy(
                            isProcessing = false,
                            thumbnail = result.thumbnail,
                            byteSize = result.byteSize,
                            filename = result.filename
                        )
                        else it
                    })
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value.currentSessionId != capturedSessionId) return@launch
                _state.update { s ->
                    s.copy(pendingImages = s.pendingImages.map {
                        if (it.id == id) it.copy(isProcessing = false, error = "Failed to process image: ${e.message}")
                        else it
                    })
                }
            } finally {
                compressionJobs.remove(id)
            }
        }
        compressionJobs[id] = job
    }

    fun removeImage(id: String) {
        compressionJobs[id]?.cancel()
        compressionJobs.remove(id)
        imageDataUris.remove(id)
        _state.update { it.copy(pendingImages = it.pendingImages.filter { img -> img.id != id }) }
    }
```

**Important:** `addImage` uses `context.contentResolver`. The ViewModel doesn't inject Context. Instead, change the `addImage` signature to accept the result directly from the Composable level. Replace the above `addImage` with:

```kotlin
    fun addImage(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        val sessionId = _state.value.currentSessionId ?: return
        if (_state.value.pendingImages.size >= 5) {
            _state.update { it.copy(error = "Maximum 5 images allowed") }
            return
        }
        val id = java.util.UUID.randomUUID().toString()
        _state.update { it.copy(pendingImages = it.pendingImages + PendingImageUi(id, isProcessing = true)) }
        val capturedSessionId = sessionId
        val job = viewModelScope.launch {
            try {
                val result = imageCompressor.compress(contentResolver, uri)
                if (_state.value.currentSessionId != capturedSessionId) return@launch
                if (!_state.value.pendingImages.any { it.id == id }) return@launch
                if (result.byteSize > 2 * 1024 * 1024) {
                    _state.update { s ->
                        s.copy(pendingImages = s.pendingImages.map {
                            if (it.id == id) it.copy(isProcessing = false, error = "Image too large after compression")
                            else it
                        })
                    }
                    return@launch
                }
                imageDataUris[id] = result.dataUri
                _state.update { s ->
                    s.copy(pendingImages = s.pendingImages.map {
                        if (it.id == id) it.copy(
                            isProcessing = false,
                            thumbnail = result.thumbnail,
                            byteSize = result.byteSize,
                            filename = result.filename
                        )
                        else it
                    })
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value.currentSessionId != capturedSessionId) return@launch
                _state.update { s ->
                    s.copy(pendingImages = s.pendingImages.map {
                        if (it.id == id) it.copy(isProcessing = false, error = "Failed to process image: ${e.message}")
                        else it
                    })
                }
            } finally {
                compressionJobs.remove(id)
            }
        }
        compressionJobs[id] = job
    }
```

- [ ] **Step 4: Update sendMessage() to handle images**

Replace the existing `sendMessage()` method in MainViewModel:
```kotlin
    fun sendMessage() {
        val sessionId = _state.value.currentSessionId ?: return
        val text = _state.value.inputText.trim()
        val images = _state.value.pendingImages

        if (text.isEmpty() && images.isEmpty()) return
        if (images.any { it.isProcessing || it.error != null }) return
        if (images.any { !imageDataUris.containsKey(it.id) }) return

        val agent = _state.value.selectedAgentName
        val model = buildSelectedModel(_state.value)

        val imageParts = images.map { ui ->
            PromptRequest.PartInput.File(
                mime = "image/jpeg",
                url = imageDataUris[ui.id]!!,
                filename = ui.filename
            )
        }

        StreamDebugLogger.logSendRequested(
            sessionId = sessionId,
            textLength = text.length,
            agent = agent,
            model = model
        )

        launchSendMessage(
            scope = viewModelScope,
            repository = repository,
            state = _state,
            sessionId = sessionId,
            text = text,
            agent = agent,
            model = model,
            imageParts = imageParts,
            onRefreshMessages = ::loadMessagesWithRetry,
            onSuccess = {
                settingsManager.setDraftText(sessionId, "")
                val sentIds = images.map { it.id }.toSet()
                sentIds.forEach { id ->
                    imageDataUris.remove(id)
                    compressionJobs[id]?.cancel()
                    compressionJobs.remove(id)
                }
                _state.update { it.copy(pendingImages = emptyList()) }
            }
        )
    }
```

- [ ] **Step 5: Update selectSession() to clear images**

In MainViewModel, replace the existing `selectSession()`:
```kotlin
    fun selectSession(sessionId: String) {
        compressionJobs.values.forEach { it.cancel() }
        compressionJobs.clear()
        imageDataUris.clear()
        _state.update { it.copy(pendingImages = emptyList()) }
        selectSessionState(_state, settingsManager, sessionId)
        loadMessages(sessionId)
        loadSessionStatus()
    }
```

- [ ] **Step 6: Update launchSendMessage signature in MainViewModelSessionActions.kt**

Replace the existing `launchSendMessage` function (lines 421-457):
```kotlin
internal fun launchSendMessage(
    scope: CoroutineScope,
    repository: OpenCodeRepository,
    state: MutableStateFlow<AppState>,
    sessionId: String,
    text: String,
    agent: String,
    model: Message.ModelInfo?,
    imageParts: List<PromptRequest.PartInput.File> = emptyList(),
    onRefreshMessages: (String, Boolean) -> Unit,
    onSuccess: (() -> Unit)? = null
) {
    scope.launch {
        repository.sendMessage(sessionId, text, agent, model, imageParts = imageParts)
            .onSuccess {
                StreamDebugLogger.logSendAccepted(sessionId)
                state.update {
                    it.copy(
                        inputText = "",
                        error = null,
                        sessionStatuses = it.sessionStatuses + (sessionId to com.yage.opencode_client.data.model.SessionStatus(type = "busy"))
                    )
                }
                onSuccess?.invoke()
                StreamDebugLogger.logMessageRefreshScheduled(sessionId, "send.accepted", true)
                onRefreshMessages(sessionId, true)
                launch {
                    delay(MainViewModelTimings.messageRefreshDelayMs)
                    StreamDebugLogger.logMessageRefreshScheduled(sessionId, "send.delayed_refresh", false)
                    onRefreshMessages(sessionId, false)
                }
            }
            .onFailure { error ->
                StreamDebugLogger.logSendFailed(sessionId, error)
                state.update { it.copy(error = errorMessageOrFallback(error, "Failed to send message")) }
            }
    }
}
```

- [ ] **Step 7: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/MainViewModel.kt app/src/main/java/com/yage/opencode_client/ui/MainViewModelSessionActions.kt
git commit -m "feat(image): update AppState, ViewModel, and launchSendMessage for image support"
```

---

### Task 6: Update ChatInputBar UI

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/chat/ChatInputBar.kt`

- [ ] **Step 1: Update ChatInputBar signature and add preview row**

Replace the existing `ChatInputBar` composable function (lines 46-94) with:

```kotlin
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
            // Image preview row
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
```

- [ ] **Step 2: Add ImagePreviewRow composable**

Add before `ChatInputBar`:
```kotlin
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
                    CircularProgressIndicator(modifier = Modifier.size(24.dp.uiScaled()), strokeWidth = 2.dp)
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
                AndroidThumbnailBitmap(image.thumbnail)
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

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(20.dp.uiScaled()).offset(x = 4.dp, y = (-4).dp)
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

@Composable
private fun AndroidThumbnailBitmap(bitmap: android.graphics.Bitmap) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp.uiScaled())),
        contentScale = ContentScale.Crop
    )
}
```

- [ ] **Step 3: Update ChatInputActions to include image picker button**

Replace the `ChatInputActions` composable to remove `isRecording` and `isTranscribing` from its params (those are only needed in `ChatInputActionButton`), and add `onPickImage`:

```kotlin
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
```

- [ ] **Step 4: Update ChatInputActionButton to include image picker and mic buttons**

Replace `ChatInputActionButton`:
```kotlin
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
    // Image picker button — always visible when not busy
    if (!isBusy) {
        IconButton(onClick = onPickImage) {
            Icon(
                Icons.Default.AddPhotoAlternate,
                contentDescription = stringResource(R.string.image_add_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    // Mic button
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
```

- [ ] **Step 5: Add missing imports to ChatInputBar.kt**

Add at the top of the file:
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/chat/ChatInputBar.kt
git commit -m "feat(image): add image preview row and picker button to ChatInputBar"
```

---

### Task 7: Update ChatScreen to Wire Picker and Callbacks

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/chat/ChatScreen.kt`

- [ ] **Step 1: Add PickMultipleVisualMedia launcher and wire callbacks**

Update the `ChatInputBar` call site in `ChatScreen` (around line 136-161). Add the launcher after the `audioPermissionLauncher`:

```kotlin
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        uris.forEach { uri ->
            viewModel.addImage(uri, context.contentResolver)
        }
    }
```

Replace the existing `ChatInputBar` call:
```kotlin
        if (state.currentSessionId != null) {
            ChatInputBar(
                text = state.inputText,
                isBusy = state.isCurrentSessionBusy,
                isRecording = state.isRecording,
                isTranscribing = state.isTranscribing,
                isSpeechConfigured = state.aiBuilderConnectionOK && aiBuilderToken.isNotEmpty(),
                pendingImages = state.pendingImages,
                onTextChange = viewModel::setInputText,
                onSend = { viewModel.sendMessage() },
                onAbort = { viewModel.abortSession() },
                onToggleRecording = {
                    if (state.isRecording) {
                        viewModel.toggleRecording()
                    } else {
                        val hasRecordAudioPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasRecordAudioPermission) {
                            viewModel.toggleRecording()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onPickImage = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemoveImage = viewModel::removeImage
            )
        }
```

- [ ] **Step 2: Add missing imports**

Add to imports:
```kotlin
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/chat/ChatScreen.kt
git commit -m "feat(image): wire PickMultipleVisualMedia launcher and image callbacks in ChatScreen"
```

---

### Task 8: Full Build Verification

**Files:** None (verification only)

- [ ] **Step 1: Run debug build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: All tests pass (existing tests may need `ImageAttachmentCompressor` mock in ViewModel tests)

- [ ] **Step 3: Commit if any fixes needed**

If compilation or tests required fixes:
```bash
git add -A
git commit -m "fix(image): address compilation/test issues"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** Every section in the spec maps to a task
- [x] **Placeholder scan:** No TBD/TODO/placeholders — all steps contain actual code
- [x] **Type consistency:** `PendingImageUi`, `PartInput.Text/File`, `PromptRequest.PartInput` used consistently across all tasks
- [x] **Import completeness:** All new imports listed for each file
- [x] **Backward compatibility:** Text-only send path unchanged except sealed class migration
