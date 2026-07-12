# Image Input Design Spec

Date: 2026-05-26

## Summary

Add multi-image input support to the OpenCode Android client chat. Users can select 1-5 images from the system photo picker, preview them as thumbnails above the input bar, remove individual images before sending, and send them alongside text to the OpenCode server.

## Requirements

- Select 1-5 images via Android `PickMultipleVisualMedia(maxItems = 5)` photo picker (multi-select in one shot)
- Preview selected images as thumbnails above the input bar (方案 A: above input field)
- Remove individual images via red X button on each thumbnail
- Send images with optional text; image-only sends supported
- Client-side compression before base64 encoding
- No new dangerous permissions needed (PickMultipleVisualMedia handles it internally)

## Server API Format

OpenCode server (`prompt_async` endpoint) uses a discriminated union on `type` in `parts`:

```json
{
  "parts": [
    { "type": "text", "text": "看看这张图" },
    { "type": "file", "mime": "image/jpeg", "url": "data:image/jpeg;base64,...", "filename": "photo.jpg" }
  ],
  "agent": "build",
  "model": { "providerID": "...", "modelID": "..." }
}
```

Source: `packages/opencode/src/session/message-v2.ts` lines 396-420 (TextPartInput, FilePartInput).

Server identifies images via `mime.startsWith("image/")` in `packages/opencode/src/util/media.ts`.

## Architecture

### Data Model: Sealed PartInput

Replace the flat `PartInput(type, text)` with a sealed interface:

```kotlin
@Serializable
sealed interface PartInput {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : PartInput

    @Serializable
    @SerialName("file")
    data class File(
        val mime: String,
        val url: String,
        val filename: String? = null
    ) : PartInput
}
```

kotlinx.serialization default class discriminator is `"type"`, matching the server. No custom serializer needed. Existing Json config (`encodeDefaults = true`, `explicitNulls = false`) is compatible.

**ProGuard/R8 note:** kotlinx.serialization compiler plugin generates proguard rules automatically for sealed interfaces. Verify in release/minified build that `PartInput` discriminator is preserved. If issues arise, add keep rule:
```
-keep class com.yage.opencode_client.data.api.PromptRequest$PartInput { *; }
-keep class * implements com.yage.opencode_client.data.api.PromptRequest$PartInput { *; }
```

### State: UI/Payload Split

**AppState (lightweight, participates in recomposition):**

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

**AppState addition:**

```kotlin
val pendingImages: List<PendingImageUi> = emptyList()
```

**ViewModel private storage (not in state):**

```kotlin
private val imageDataUris = mutableMapOf<String, String>()  // id -> dataUri
private val compressionJobs = mutableMapOf<String, Job>()     // id -> compression coroutine
```

This keeps ~3.5MB of base64 strings out of state emissions.

### Image Compression

New `ImageAttachmentCompressor` utility class:

```kotlin
@Singleton
class ImageAttachmentCompressor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class CompressedImage(
        val dataUri: String,
        val filename: String?,
        val byteSize: Int,
        val thumbnail: android.graphics.Bitmap
    )

    suspend fun compress(uri: Uri): CompressedImage
}
```

Compression pipeline (each step opens its own `ContentResolver.openInputStream` — streams are consumed and cannot be reused):
1. `openInputStream(uri).use {}` for bounds decode with `inJustDecodeBounds`
2. `BitmapFactory.Options.inJustDecodeBounds` to get dimensions
3. Read EXIF orientation via `ExifInterface`
4. Compute `inSampleSize` so decoded bitmap is near 1024px long edge
5. Decode with sample size on `Dispatchers.IO`
6. Apply EXIF rotation matrix
7. Scale to exactly max 1024px long edge
8. Flatten alpha to white background (transparent PNG → JPEG)
9. Compress JPEG quality 75%
10. Base64 encode with `Base64.NO_WRAP`
11. Build 128px thumbnail separately
12. Build data URI: `"data:image/jpeg;base64," + base64String`
13. All streams closed with `use {}`

Post-compression size cap: 2MB per image base64 string length. Exceeded → set error on that image.

New dependency: `androidx.exifinterface:exifinterface`

### ViewModel Methods

**addImage(uri: Uri):**
1. Guard: no active session → return. Already 5 images → return.
2. Generate UUID id
3. Add `PendingImageUi(id, isProcessing=true)` to state
4. Capture `currentSessionId`
5. Launch coroutine, store in `compressionJobs`:
   - `compress(uri)` on IO
   - `catch (e: CancellationException) { throw e }` — do not catch cancellation as failure
   - `catch (e: Exception)` → update state with error
   - `finally { compressionJobs.remove(id) }`
   - On success: race guard (check session id + image id still valid) → store dataUri → update state
6. Completed jobs auto-removed via `finally`

**removeImage(id: String):**
1. Cancel compression job if running
2. Remove from `compressionJobs`, `imageDataUris`, state

**sendMessage() modification:**
1. Guard: no active session → return
2. Get `text`, `images`
3. If text empty AND images empty → return
4. If any image `isProcessing` or `error != null` → return (block send)
5. If any image has no payload in `imageDataUris` → return
6. Build `imageParts`: map images to `PartInput.File(mime="image/jpeg", url=dataUri, filename)`
7. Call `launchSendMessage(... imageParts=imageParts ...)`
8. On success: clear draft text, clear pending images from state, clear sent image IDs from `imageDataUris`, cancel and clear related jobs
9. On failure: preserve images for retry

### Session Switch Cleanup

In `MainViewModel.selectSession()`:
1. Cancel all compression jobs
2. Clear `compressionJobs`
3. Clear `imageDataUris`
4. Clear `pendingImages` from state
5. Then proceed with existing `selectSessionState()` logic

### Repository Change

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
    if (text.isNotEmpty()) parts.add(PromptRequest.PartInput.Text(text = text))
    parts.addAll(imageParts)
    require(parts.isNotEmpty()) { "Message must contain text or images" }
    val request = PromptRequest(parts = parts, agent = agent, model = ...)
    // ... existing API call with effectiveDirectory(directory) ...
}
```

Existing `directory` behavior preserved via `effectiveDirectory(directory)`.

### launchSendMessage Signature

Add `imageParts: List<PromptRequest.PartInput.File> = emptyList()` parameter with default. Pass through to `repository.sendMessage()`.

### UI Changes

**ChatInputBar.kt:**
- Add `🖼️` button (`Icons.Default.AddPhotoAlternate`) before mic button
- New callback: `onPickImage: () -> Unit`
- New parameter: `pendingImages: List<PendingImageUi>`
- New callback: `onRemoveImage: (String) -> Unit`
- Show preview row above text field when images exist: horizontal scrollable thumbnails (64dp) with red X overlay
- Send enabled when: `(text.isNotBlank() || pendingImages.isNotEmpty()) && pendingImages.none { it.isProcessing || it.error != null }`
- All dp values use `.uiScaled()`
- Works with existing `useVerticalActions` layout

**ChatScreen.kt:**
- Add `rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5))`
- Result is `List<Uri>` (possibly empty); iterate and call `viewModel.addImage(uri)` for each
- Wire `onPickImage` callback to launcher
- Pass `pendingImages` and `onRemoveImage` to `ChatInputBar`

### Send-Enabled Logic

```
canSend = (text.isNotBlank() || images.isNotEmpty())
       && images.none { it.isProcessing || it.error != null }
```

Text-only with no pending images: always works.
Messages with pending images: all images must be ready (no processing, no errors).

## Files Changed

| File | Change |
|------|--------|
| `data/api/OpenCodeApi.kt` | Replace flat PartInput with sealed interface |
| `data/repository/OpenCodeRepository.kt` | Update sendMessage signature, build mixed parts |
| `data/image/ImageAttachmentCompressor.kt` | New file: compression utility |
| `ui/MainViewModel.kt` | Add pendingImages to AppState, addImage/removeImage, update sendMessage, cleanup on session switch |
| `ui/MainViewModelSessionActions.kt` | Update launchSendMessage signature |
| `ui/chat/ChatInputBar.kt` | Add image button, preview row, remove callback |
| `ui/chat/ChatScreen.kt` | Add PickVisualMedia launcher, wire callbacks |
| `di/AppModule.kt` | Bind ImageAttachmentCompressor |
| `app/build.gradle.kts` | Add exifinterface dependency |
| `app/src/main/res/values/strings.xml` | New string resources |

### String Resources

- `image_add_cd` — "Select image" (content description for image picker button)
- `image_remove_cd` — "Remove image" (content description for X button on thumbnail)
- `image_max_reached` — "Maximum 5 images allowed"
- `image_compression_failed` — "Failed to process image: %s"
- `image_too_large` — "Image too large after compression (%.1f MB)"

### ViewModel Constructor Change

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    internal val repository: OpenCodeRepository,
    private val settingsManager: SettingsManager,
    private val audioRecorderManager: AudioRecorderManager,
    private val imageCompressor: ImageAttachmentCompressor  // NEW
) : ViewModel() {
```

### Sent Image Display

File-type parts in user messages are out of scope for this feature. Follow-up ticket to render file parts as "📎 photo.jpg" text in chat history.

## Tests Required

- PromptRequest serialization: text-only, image-only, text+image
- Repository sends correct JSON for all three part combinations
- ViewModel allows image-only send
- ViewModel rejects empty text with no images
- ViewModel blocks send while images processing
- ViewModel blocks send when image has error
- Successful send clears pending images and payloads
- Failed send preserves images for retry
- Session switch clears images, payloads, cancels jobs
- Late compression completion after session switch discarded
- Compression cancellation does not produce error state
- Remove during compression cancels job cleanly
