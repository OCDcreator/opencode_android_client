# Part Rendering & Sub-Agent Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all missing part type rendering in the chat UI and add sub-agent session navigation.

**Architecture:** The server sends 12 part types via SSE/HTTP API. The client currently renders 4 (text, reasoning, tool, patch). We add rendering for the remaining visible types (subtask, step-start, step-finish, agent, file, compaction) and improve SSE streaming to use the full `part.text` from `message.part.updated` instead of triggering API refreshes. Sub-agent navigation uses the server's existing `GET /session/:id/children` endpoint.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, Retrofit, SSE

---

## Background: Server Part Types vs Client Status

| Server Part Type | Data Available | Client Before Plan | Plan Phase |
|---|---|---|---|
| `text` | Markdown text | ✅ Rendered | — |
| `reasoning` | Thinking text | ✅ Rendered (expandable) | — |
| `tool` | name, input, output, status, todos, files | ✅ Rendered (expandable) | ✅ Already fixed (input/output in expanded) |
| `patch` | file paths, hash | ⚠️ Only if files with extensions | — |
| `step-start` | snapshot | ❌ Silent drop | Phase 2 |
| `step-finish` | reason, cost, tokens | ❌ Silent drop | Phase 2 |
| `subtask` | prompt, description, agent | ❌ Not in model | Phase 2 |
| `agent` | name | ❌ Not in model | Phase 2 |
| `file` | mime, url, filename | ❌ Not in model | Phase 2 |
| `compaction` | auto, overflow | ❌ Silent drop | Phase 3 |
| `retry` | attempt, error | ❌ Silent drop | Phase 3 |
| `snapshot` | snapshot data | ❌ Silent drop | Skip (internal) |

## SSE Streaming Issue (Critical Context)

The server's `message.part.updated` SSE event contains:
```json
{ "sessionID": "...", "part": { "id", "messageID", "type", "text": "full accumulated text", ... }, "time": 123 }
```

The client's `parseMessagePartDeltaEvent()` reads `event.payload.getString("delta")` — but **`delta` does NOT exist** in `message.part.updated` events. It always returns null, so every `message.part.updated` triggers a full API message refresh instead of using the text already present in `part.text`.

The `message.part.delta` events exist server-side but are `BusEvent` (in-process only) — they are **never sent to SSE**. So we must use `part.text` from `message.part.updated`.

---

## Phase 1: SSE Streaming Fix — Use `part.text` Instead of API Refresh

**Why first:** This is a fundamental plumbing fix. Every subsequent phase builds on correct SSE data flow. Without it, streaming UX depends on repeated API calls.

### Task 1.1: Fix `parseMessagePartDeltaEvent` to read `part.text`

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/MainViewModelSupport.kt:86-99`

- [ ] **Step 1: Rewrite `parseMessagePartDeltaEvent` to extract `part.text` from `message.part.updated`**

The server's `message.part.updated` event `properties` structure is:
```json
{ "sessionID": "...", "part": { "id": "...", "messageID": "...", "type": "text", "text": "full text", ... }, "time": 123 }
```

Replace the current function:

```kotlin
internal fun parseMessagePartDeltaEvent(event: SSEEvent): MessagePartDeltaEvent? {
    val sessionId = event.payload.getString("sessionID") ?: return null
    val partObj = event.payload.getJsonObject("part")
    val messageId = (partObj?.get("messageID") as? JsonPrimitive)?.content
    val partId = (partObj?.get("id") as? JsonPrimitive)?.content
    val partType = (partObj?.get("type") as? JsonPrimitive)?.content ?: "text"
    val text = (partObj?.get("text") as? JsonPrimitive)?.content
    return MessagePartDeltaEvent(
        sessionId = sessionId,
        messageId = messageId,
        partId = partId,
        partType = partType,
        delta = text  // Full accumulated text from part, not an incremental delta
    )
}
```

Key change: `delta = text` reads `part.text` which is the server's accumulated text for this part.

- [ ] **Step 2: Update `handleIncomingSseEvent` to use text as full replacement, not append**

**File:** `app/src/main/java/com/yage/opencode_client/ui/MainViewModelSyncActions.kt:107-143`

The current code appends `deltaEvent.delta` to existing streaming text. Since `part.text` is the full accumulated text (not an increment), we must **replace** instead of append:

```kotlin
"message.part.updated" -> {
    val deltaEvent = parseMessagePartDeltaEvent(event) ?: return
    if (deltaEvent.sessionId == state.value.currentSessionId) {
        if (
            deltaEvent.messageId != null &&
            deltaEvent.partId != null &&
            !deltaEvent.delta.isNullOrBlank()
        ) {
            val key = "${deltaEvent.messageId}:${deltaEvent.partId}"
            // part.text is the full accumulated text — replace, don't append
            state.update {
                it.copy(
                    streamingPartTexts = it.streamingPartTexts + (key to deltaEvent.delta),
                    streamingReasoningPart = reasoningPartOrNull(
                        partType = deltaEvent.partType,
                        partId = deltaEvent.partId,
                        messageId = deltaEvent.messageId,
                        sessionId = deltaEvent.sessionId
                    ) ?: it.streamingReasoningPart
                )
            }
        } else {
            // Part completed or non-text part — clear streaming and refresh
            StreamDebugLogger.logStreamCompleted(deltaEvent.sessionId, "message.part.completed")
            state.update {
                it.copy(streamingPartTexts = emptyMap(), streamingReasoningPart = null)
            }
            StreamDebugLogger.logMessageRefreshScheduled(deltaEvent.sessionId, "message.part.completed", false)
            onRefreshMessages(deltaEvent.sessionId, false)
        }
    }
}
```

The change is on the line: `it.streamingPartTexts + (key to deltaEvent.delta)` — this now **replaces** the key's value with the full text instead of appending.

- [ ] **Step 3: Build and verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/MainViewModelSupport.kt app/src/main/java/com/yage/opencode_client/ui/MainViewModelSyncActions.kt
git commit -m "fix: use part.text from message.part.updated for SSE streaming instead of non-existent delta field"
```

---

## Phase 2: Missing Part Type Rendering

**Why second:** After SSE streaming is correct, we render the part types that the server actually sends. This gives users visibility into subtasks, steps, agent switches, and file attachments.

### Task 2.1: Extend Part data model for new types

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/data/model/Message.kt:88-144`

- [ ] **Step 1: Add subtask-specific fields to Part data class**

Add these fields to the `Part` data class (after the existing fields, before the closing brace of Part):

```kotlin
@Serializable
data class Part(
    val id: String,
    @SerialName("messageID") val messageId: String? = null,
    @SerialName("sessionID") val sessionId: String? = null,
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    @SerialName("callID") val callId: String? = null,
    val state: PartState? = null,
    val metadata: PartMetadata? = null,
    @Serializable(with = PartFilesSerializer::class) val files: List<FileChange>? = null,
    // --- New fields for subtask parts ---
    val prompt: String? = null,
    val description: String? = null,
    val agent: String? = null,
    val command: String? = null,
    // --- New fields for step-finish parts ---
    val reason: String? = null,
    val cost: Double? = null,
    val tokens: PartTokenInfo? = null,
    // --- New fields for file parts ---
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null
) {
```

Add the new type predicates (after existing `isStepFinish`):

```kotlin
    val isSubtask: Boolean get() = type == "subtask"
    val isAgent: Boolean get() = type == "agent"
    val isFile: Boolean get() = type == "file"
    val isCompaction: Boolean get() = type == "compaction"
```

Add the `PartTokenInfo` data class (after `PartMetadata`):

```kotlin
@Serializable
data class PartTokenInfo(
    val total: Int? = null,
    val input: Int? = null,
    val output: Int? = null,
    val reasoning: Int? = null
)
```

All new fields have defaults (`null`), so existing JSON deserialization is unaffected. `kotlinx.serialization` with `ignoreUnknownKeys = true` handles any extra server fields gracefully.

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/data/model/Message.kt
git commit -m "feat: extend Part model with subtask, agent, file, step-finish fields"
```

### Task 2.2: Add composable components for new part types

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/chat/ChatMessageContent.kt`

- [ ] **Step 1: Add SubtaskCard composable**

Add after the `ToolCard` composable, before `PatchCard`:

```kotlin
@Composable
private fun SubtaskCard(
    agentName: String,
    description: String?,
    prompt: String?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Card(
        modifier = modifier.padding(vertical = 4.dp.uiScaled()),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp.uiScaled())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp.uiScaled()),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                Text(
                    text = description ?: "Sub-agent task",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp.uiScaled())
                ) {
                    Text(
                        text = "@$agentName",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp.uiScaled(), vertical = 2.dp.uiScaled()),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (!prompt.isNullOrBlank()) {
                Spacer(modifier = Modifier.size(6.dp.uiScaled()))
                Text(
                    text = prompt.take(200) + if (prompt.length > 200) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
        }
    }
}
```

- [ ] **Step 2: Add StepDivider composable**

Add after `SubtaskCard`:

```kotlin
@Composable
private fun StepDivider(
    reason: String?,
    cost: Double?,
    tokens: PartTokenInfo?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp.uiScaled()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Divider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp
        )
        if (cost != null && cost > 0) {
            Text(
                text = String.format("$%.4f", cost),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp.uiScaled())
            )
        }
        if (tokens != null) {
            val total = tokens.total ?: (tokens.input ?: 0) + (tokens.output ?: 0) + (tokens.reasoning ?: 0)
            if (total > 0) {
                Text(
                    text = "${total}t",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        androidx.compose.foundation.layout.Divider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp
        )
    }
}
```

- [ ] **Step 3: Add FileAttachmentCard composable**

Add after `StepDivider`:

```kotlin
@Composable
private fun FileAttachmentCard(
    filename: String?,
    mime: String?,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Surface(
        modifier = modifier.padding(vertical = 2.dp.uiScaled()),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp.uiScaled())
    ) {
        Row(
            modifier = Modifier.padding(8.dp.uiScaled()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp.uiScaled()),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
            Column {
                Text(
                    text = filename ?: "Attachment",
                    style = MaterialTheme.typography.bodySmall
                )
                if (mime != null) {
                    Text(
                        text = mime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
```

Note: Requires adding `import androidx.compose.material.icons.filled.AttachFile` to imports.

- [ ] **Step 4: Build and verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/chat/ChatMessageContent.kt
git commit -m "feat: add SubtaskCard, StepDivider, FileAttachmentCard composables"
```

### Task 2.3: Wire new part types into PartView dispatch

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/chat/ChatMessageContent.kt:323-345`

- [ ] **Step 1: Update PartView's `when` block to handle all types**

Replace the entire `when` block in `PartView`:

```kotlin
when {
    part.isText -> TextPart(
        text = streamingTextOverride ?: part.text ?: "",
        isUser = isUser,
        modifier = modifier,
        repository = repository,
        workspaceDirectory = workspaceDirectory
    )
    part.isReasoning -> ReasoningCard(streamingTextOverride ?: part.text ?: "", part.toolReason, false, modifier)
    part.isTool -> ToolCard(
        toolName = part.tool ?: "",
        status = part.stateDisplay,
        reason = part.toolReason,
        inputSummary = part.toolInputSummary,
        output = part.toolOutput,
        filePaths = part.filePathsForNavigationFiltered,
        todos = part.toolTodos,
        onFileClick = onFileClick,
        modifier = modifier
    )
    part.isPatch && part.filePathsForNavigationFiltered.isNotEmpty() -> PatchCard(part.filePathsForNavigationFiltered, onFileClick, modifier)
    part.isSubtask -> SubtaskCard(
        agentName = part.agent ?: "unknown",
        description = part.description,
        prompt = part.prompt,
        modifier = modifier
    )
    part.isStepFinish -> StepDivider(
        reason = part.reason,
        cost = part.cost,
        tokens = part.tokens,
        modifier = modifier
    )
    part.isFile -> FileAttachmentCard(
        filename = part.filename,
        mime = part.mime,
        modifier = modifier
    )
    part.isAgent -> {
        // Agent switch indicator — lightweight inline chip, no card
        Surface(
            modifier = modifier.padding(vertical = 2.dp.uiScaled()),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(4.dp.uiScaled())
        ) {
            Text(
                text = "→ @${part.agentName ?: "unknown"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp.uiScaled(), vertical = 4.dp.uiScaled())
            )
        }
    }
    part.isCompaction -> {
        // Context compaction indicator
        Text(
            text = "Context compressed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = modifier.padding(vertical = 2.dp.uiScaled())
        )
    }
    // step-start, retry, snapshot: intentionally not rendered
}
```

Add `val agentName: String? get() = agent` to the `Part` class if it doesn't already exist (the `agent` field added in Task 2.1 serves this purpose — we use `part.agent` directly).

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/chat/ChatMessageContent.kt
git commit -m "feat: render subtask, step-finish, file, agent, compaction parts in chat UI"
```

---

## Phase 3: Sub-Agent Session Navigation

**Why third:** After all part types render correctly (including `subtask` cards), we add the ability to navigate into sub-agent sessions. This requires new API endpoints, SSE event filtering for child sessions, and a navigation UI.

### Task 3.1: Add children API endpoint

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/data/api/OpenCodeApi.kt`
- Modify: `app/src/main/java/com/yage/opencode_client/data/repository/OpenCodeRepository.kt`

- [ ] **Step 1: Add `getSessionChildren` to OpenCodeApi**

Add to `OpenCodeApi` interface after `getSession`:

```kotlin
@GET("session/{id}/children")
suspend fun getSessionChildren(
    @Path("id") sessionId: String,
    @Query("directory") directory: String? = null
): List<Session>
```

- [ ] **Step 2: Add `getChildrenSessions` to OpenCodeRepository**

Add repository method:

```kotlin
suspend fun getChildrenSessions(sessionId: String): Result<List<Session>> = runCatching {
    val dir = effectiveDirectory()
    api.getSessionChildren(sessionId, dir)
}
```

- [ ] **Step 3: Build and verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/data/api/OpenCodeApi.kt app/src/main/java/com/yage/opencode_client/data/repository/OpenCodeRepository.kt
git commit -m "feat: add session children API endpoint for sub-agent navigation"
```

### Task 3.2: Add sub-session state to AppState and ViewModel

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/yage/opencode_client/ui/MainViewModelSessionActions.kt`

- [ ] **Step 1: Add sub-session fields to AppState**

Add to `AppState` data class:

```kotlin
// Sub-agent session navigation
val subSessionStack: List<String> = emptyList(),  // Stack of parent session IDs for back navigation
val currentSubSessionId: String? = null,           // Currently viewing sub-session (null = main session)
val childSessions: Map<String, List<Session>> = emptyMap(),  // Cached children per session
```

Add computed property:

```kotlin
val effectiveViewSessionId: String get() = currentSubSessionId ?: currentSessionId ?: ""
```

- [ ] **Step 2: Add ViewModel actions for sub-session navigation**

In `MainViewModelSessionActions.kt`, add:

```kotlin
fun loadChildSessions(sessionId: String) {
    viewModelScope.launch {
        repository.getChildrenSessions(sessionId)
            .onSuccess { children ->
                _state.update { it.copy(childSessions = it.childSessions + (sessionId to children)) }
            }
    }
}

fun navigateToSubSession(subSessionId: String) {
    val current = _state.value.currentSessionId ?: return
    _state.update { it.copy(
        subSessionStack = it.subSessionStack + current,
        currentSubSessionId = subSessionId
    )}
    loadMessages(subSessionId)
}

fun navigateBackFromSubSession() {
    val stack = _state.value.subSessionStack
    if (stack.isEmpty()) return
    val parentId = stack.last()
    _state.update { it.copy(
        subSessionStack = stack.dropLast(1),
        currentSubSessionId = if (stack.size > 1) parentId else null
    )}
    loadMessages(parentId)
}
```

- [ ] **Step 3: Build and verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/MainViewModel.kt app/src/main/java/com/yage/opencode_client/ui/MainViewModelSessionActions.kt
git commit -m "feat: add sub-session navigation state and actions"
```

### Task 3.3: Add "View sub-agent session" UI on subtask/tool cards

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/chat/ChatMessageContent.kt`

- [ ] **Step 1: Add `onNavigateToSubSession` callback propagation**

Add parameter to `ChatMessageList`, `MessageRow`, and `PartView`:

```kotlin
onNavigateToSubSession: (String) -> Unit = {}
```

Thread this callback through all three functions.

- [ ] **Step 2: Add navigation button to SubtaskCard**

When the user clicks on a SubtaskCard, look up the child session with matching agent name and navigate to it. The subtask card needs an `onClick` that triggers sub-session discovery.

Update `SubtaskCard` to accept `onNavigateToSubSession`:

```kotlin
@Composable
private fun SubtaskCard(
    agentName: String,
    description: String?,
    prompt: String?,
    onNavigateToSubSession: () -> Unit = {},
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Card(
        modifier = modifier.padding(vertical = 4.dp.uiScaled())
            .clickable { onNavigateToSubSession() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        // ... same content as before, but add a "View details →" trailing indicator
        // In the Row, after the agent chip, add:
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "View sub-agent session",
            modifier = Modifier.size(16.dp.uiScaled()),
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
}
```

Requires adding `import androidx.compose.foundation.clickable` and `import androidx.compose.material.icons.filled.AttachFile`.

- [ ] **Step 3: Build and verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/chat/ChatMessageContent.kt
git commit -m "feat: add sub-agent session navigation UI on subtask cards"
```

### Task 3.4: Add sub-session back navigation bar

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/chat/ChatScreen.kt`

- [ ] **Step 1: Add sub-session indicator bar above chat list**

When `currentSubSessionId != null`, show a dismissible bar above the message list:

```kotlin
if (state.currentSubSessionId != null) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sub-agent session", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.navigateBackFromSubSession() }) {
                Text("Back to main")
            }
        }
    }
}
```

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/chat/ChatScreen.kt
git commit -m "feat: add sub-session back navigation bar"
```

---

## Phase 4: Polish & SSE Event Completeness

### Task 4.1: Handle `message.part.removed` SSE event

**Files:**
- Modify: `app/src/main/java/com/yage/opencode_client/ui/MainViewModelSyncActions.kt`

- [ ] **Step 1: Add handler for `message.part.removed`**

Add a new case in `handleIncomingSseEvent`'s `when` block:

```kotlin
"message.part.removed" -> {
    val sessionId = event.payload.getString("sessionID")
    if (sessionId != null && sessionId == state.value.currentSessionId) {
        onRefreshMessages(sessionId, false)
    }
}
```

- [ ] **Step 2: Build, commit**

```bash
git add app/src/main/java/com/yage/opencode_client/ui/MainViewModelSyncActions.kt
git commit -m "feat: handle message.part.removed SSE event"
```

### Task 4.2: Build debug APK and manual verification

- [ ] **Step 1: Build debug APK**

Run: `./gradlew assembleDebug`

- [ ] **Step 2: Manual test checklist**

- [ ] Text messages render correctly with Markdown
- [ ] Reasoning card expands/collapses with content
- [ ] Tool card expands to show input summary + output + file paths
- [ ] Subtask card shows agent name + description
- [ ] Step divider shows cost and token count
- [ ] File attachment shows filename and mime type
- [ ] Agent switch indicator shows agent name
- [ ] SSE streaming is smooth (text updates without flicker)
- [ ] Sub-agent session can be navigated into and back
