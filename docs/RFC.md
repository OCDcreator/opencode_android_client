# RFC-001: OpenCode Android Client 技术方案

> Request for Comments · Accepted · Mar 2026

## 元数据

| 字段 | 值 |
|------|------|
| **RFC 编号** | RFC-001 |
| **标题** | OpenCode Android Client 技术方案 |
| **状态** | Accepted (Implemented) |
| **创建日期** | 2026-02 |
| **最后更新** | 2026-03-14 |
| **PRD 引用** | [PRD.md](PRD.md) |

---

## 摘要

本 RFC 提出 OpenCode Android Client 的技术实现方案。核心是：在 Android 8.0+ 上构建一个基于 Jetpack Compose 的原生客户端，通过 HTTP REST + SSE 与 OpenCode Server 通信，并通过 AI Builder WebSocket API 提供语音转写能力，实现远程监控、消息发送、文档审查等能力。

---

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Android Client (Jetpack Compose)              │
├─────────────────────────────────────────────────────────────────┤
│  UI Layer              │  ViewModel Layer      │  Data Layer                 │
│  ─────────             │  ────────────         │  ──────────                 │
│  ChatScreen            │  MainViewModel        │  OpenCodeApi               │
│  FilesScreen           │                       │  SSEClient                 │
│  SettingsScreen        │                       │  OpenCodeRepository        │
│  Components            │                       │  AIBuildersAudioClient     │
│                        │                       │  AudioRecorderManager      │
│                        │                       │  SettingsManager           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ OkHttp (REST + SSE + WebSocket)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│          OpenCode Server + AI Builder Speech Services            │
│  GET /global/event  │  POST /session/:id/prompt_async  │  ...     │
│  POST /v1/audio/realtime/sessions  │  WS /v1/audio/realtime/ws    │
└─────────────────────────────────────────────────────────────────┘
```

**分层说明**：
- **UI Layer**：Jetpack Compose 声明式 UI
- **ViewModel Layer**：持有 UI 状态，处理业务逻辑
- **Data Layer**：网络请求、数据持久化

---

## 2. 技术选型

| 层面 | 选择 | 理由 |
|------|------|------|
| 语言 | Kotlin | Android 官方推荐，协程支持好 |
| UI | Jetpack Compose | 声明式，与 SwiftUI 概念相似，未来方向 |
| 状态 | ViewModel + StateFlow | 官方推荐，生命周期感知 |
| 网络 | OkHttp + Retrofit | 业界标准，SSE 支持好 |
| 序列化 | Kotlinx Serialization | Kotlin 原生，性能好 |
| 依赖注入 | Hilt | 官方推荐，Dagger 封装 |
| Markdown | multiplatform-markdown-renderer-m3 | 已落地，Compose 兼容性好 |
| SSH（可选） | Apache Mina SSHD 或 JSch | 成熟，支持端口转发 |
| 安全存储 | EncryptedSharedPreferences + Keystore | Android 官方方案 |

### 2.1 HTTP 连接配置

Android 9+ 默认禁止明文流量。`network_security_config.xml` 设置 base-config cleartextTrafficPermitted="false"，仅对 localhost、127.0.0.1、10.0.2.2、ts.net（Tailscale MagicDNS）开放 HTTP，其余强制 HTTPS。Android 不支持 IP 段匹配，局域网 IP 需使用 HTTPS 或 Tailscale。

### 2.2 SSH 库选型（可选）

| 库 | 语言 | 维护状态 | 推荐度 |
|----|------|----------|--------|
| **Apache Mina SSHD** | Java | 活跃 | ★★★★★ |
| JSch | Java | 维护模式 | ★★★★ |
| sshj | Java | 活跃 | ★★★★ |

**推荐 Apache Mina SSHD**：功能完整，支持端口转发，文档齐全。

---

## 3. 网络层设计

### 3.1 REST API

```kotlin
interface OpenCodeApi {
    @GET("/health")
    suspend fun getHealth(): HealthResponse
    
    @GET("/session")
    suspend fun getSessions(): List<Session>
    
    @GET("/session/{id}/message")
    suspend fun getMessages(
        @Path("id") sessionId: String,
        @Query("limit") limit: Int = 6
    ): List<Message>
    
    @POST("/session/{id}/prompt_async")
    suspend fun sendMessage(
        @Path("id") sessionId: String,
        @Body request: PromptRequest
    )
    
    @POST("/session/{id}/permissions/{permissionId}")
    suspend fun handlePermission(
        @Path("id") sessionId: String,
        @Path("permissionId") permissionId: String,
        @Body request: PermissionRequest
    )
    
    @POST("/session/{id}/abort")
    suspend fun abortSession(@Path("id") sessionId: String)
    
    @GET("/file")
    suspend fun getFileTree(@Query("path") path: String?): FileNode
    
    @GET("/file/content")
    suspend fun getFileContent(@Query("path") path: String): FileContent
    
    @GET("/agent")
    suspend fun getAgents(): List<AgentInfo>
}
```

### 3.2 SSE 连接

使用 OkHttp 的 `EventSource`：

```kotlin
class SSEClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String
) {
    private var eventSource: EventSource? = null
    
    fun connect(onEvent: (SSEEvent) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/global/event")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()
            
        eventSource = EventSources.createFactory(okHttpClient)
            .newEventSource(request, object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val event = Json.decodeFromString<SSEEvent>(data)
                    onEvent(event)
                }
            })
    }
    
    fun disconnect() {
        eventSource?.cancel()
    }
}
```

### 3.3 错误处理与重连

- 网络错误：Toast 提示，不 crash
- SSE 断开：指数退避重连，上限 30s
- 服务器不可达：显示 Disconnected 状态

### 3.4 语音转写链路

- 录音端使用 `AudioRecorderManager`：`MediaRecorder` 先录制 M4A，停止后解码为 PCM 24kHz mono
- 转写端使用 `AIBuildersAudioClient`：先 `POST /v1/audio/realtime/sessions` 创建会话，再通过 WebSocket 发送 PCM chunk 并接收 partial / final transcript
- 输入框合并策略使用 `mergedSpeechInput(prefix, transcript)`：保留原输入，在转写结果前后只做必要空格拼接
- 连接测试使用 AI Builder API，成功状态按 `baseURL + token` 的签名缓存，避免每次进入页面都强制重测

---

## 4. 状态管理

### 4.1 AppState

```kotlin
data class AppState(
    val isConnected: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val currentSessionId: String? = null,
    val messages: List<MessageWithParts> = emptyList(),
    val selectedModelIndex: Int = 0,
    val selectedAgentName: String = "build",
    val agents: List<AgentInfo> = emptyList(),
    val inputText: String = "",
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val speechError: String? = null,
    val aiBuilderConnectionOK: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: OpenCodeRepository,
    private val settingsManager: SettingsManager,
    private val audioRecorderManager: AudioRecorderManager
) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    
    fun handleSSEEvent(event: SSEEvent) {
        when (event.payload.type) {
            "session.created" -> { /* 更新 sessions */ }
            "session.updated" -> { /* 替换 session 对象（含 title） */ }
            "message.created" -> { /* 追加消息 */ }
            "message.part.updated" -> { /* 流式更新 */ }
            // ...
        }
    }
}
```

### 4.2 数据模型

```kotlin
@Serializable
data class Session(
    val id: String,
    val directory: String,
    val model: String? = null,
    val createdAt: String? = null
)

@Serializable
data class Message(
    val id: String,
    val role: String,
    val parts: List<Part>
)

@Serializable
data class Part(
    val id: String,
    val type: String,
    val text: String? = null,
    val toolName: String? = null,
    val toolInput: JsonObject? = null,
    val toolOutput: String? = null,
    val state: PartState? = null
)

@Serializable
data class AgentInfo(
    val name: String,
    val description: String? = null,
    val mode: String? = null,
    val hidden: Boolean? = null
)
```

### 4.3 草稿持久化（Phase 5，对齐 iOS）

**背景**：当前 `AppState.inputText` 是全局 String，切换 session 时不保存/不恢复，发送后清空。iOS 端用 `draftInputsBySessionID: [String: String]` 字典按 session 存储草稿，持久化到 UserDefaults。

**数据存储**：

```kotlin
// SettingsManager 新增
private val draftKey = "draft_inputs_by_session"

fun getDraftText(sessionId: String): String {
    val json = prefs.getString(draftKey, "{}") ?: "{}"
    val map = Json.decodeFromString<Map<String, String>>(json)
    return map[sessionId] ?: ""
}

fun setDraftText(sessionId: String, text: String) {
    val json = prefs.getString(draftKey, "{}") ?: "{}"
    val map = Json.decodeFromString<Map<String, String>>(json).toMutableMap()
    if (text.isBlank()) map.remove(sessionId) else map[sessionId] = text
    prefs.edit { putString(draftKey, Json.encodeToString(map)) }
}
```

**状态流转**：
1. `selectSession(newId)` 时：先调 `setDraftText(oldId, currentInputText)` 保存旧草稿，再调 `getDraftText(newId)` 加载新草稿到 `inputText`
2. `setInputText(text)` 时：同步调 `setDraftText(currentSessionId, text)` 持久化（每次击键都写，利用 EncryptedSharedPreferences 的内存缓存，实际 I/O 开销可控）
3. `sendMessage()` 成功后：调 `setDraftText(sessionId, "")` 清空

**性能考虑**：EncryptedSharedPreferences 在内存中维护缓存，`getString`/`putString` 的 hot path 不涉及磁盘 I/O。JSON 编解码 Map 规模通常 < 50 条，开销可忽略。如果未来 session 数过多，可按 LRU 淘汰旧草稿。

### 4.4 Model/Agent 按 Session 记忆（Phase 5，对齐 iOS）

**背景**：当前 `selectedModelIndex` 和 `selectedAgentName` 全局存储在 SettingsManager。切换 session 时通过 last assistant message 推断，但用户手动切了模型还没发消息就切走的场景会丢失选择。iOS 用 `selectedModelIDBySessionID` 字典做显式 per-session 持久化。

**数据存储**：

```kotlin
// SettingsManager 新增
private val modelBySessionKey = "selected_model_by_session"
private val agentBySessionKey = "selected_agent_by_session"

// modelID 格式："{providerID}/{modelID}"，与 iOS 的 ModelPreset.id 对齐
fun getModelForSession(sessionId: String): String? {
    val json = prefs.getString(modelBySessionKey, "{}") ?: "{}"
    return Json.decodeFromString<Map<String, String>>(json)[sessionId]
}

fun setModelForSession(sessionId: String, modelId: String) {
    val json = prefs.getString(modelBySessionKey, "{}") ?: "{}"
    val map = Json.decodeFromString<Map<String, String>>(json).toMutableMap()
    map[sessionId] = modelId
    prefs.edit { putString(modelBySessionKey, Json.encodeToString(map)) }
}

// Agent 同理
fun getAgentForSession(sessionId: String): String? { /* 同上模式 */ }
fun setAgentForSession(sessionId: String, agentName: String) { /* 同上模式 */ }
```

**恢复优先级**（切换到 session X 时）：
1. 查 `getModelForSession(X)` → 有值则直接恢复
2. 无值 → 从 X 的最后一条 assistant message 推断（当前已有此逻辑）
3. 推断不到 → 保持当前全局 selectedModelIndex 不变

**写入时机**：
- `selectModel(index)` 时：若 `currentSessionId != null`，同时调 `setModelForSession(sessionId, modelId)`
- `selectAgent(name)` 时：同理

**全局默认值保留**：SettingsManager 中原有的全局 `selectedModelIndex` 和 `selectedAgentName` 继续保留，作为新 session 或无 per-session 记录时的 fallback。

---

## 5. UI 设计

### 5.1 导航结构

```kotlin
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    
    NavHost(navController, startDestination = "chat") {
        composable("chat") { ChatScreen() }
        composable("files") { FilesScreen() }
        composable("settings") { SettingsScreen() }
    }
}

// 手机：底部 Tab
@Composable
fun PhoneLayout() {
    Scaffold(
        bottomBar = { BottomNavigationBar() }
    ) { padding ->
        NavHost(/* ... */, modifier = Modifier.padding(padding))
    }
}

// 平板：三栏布局
@Composable
fun TabletLayout() {
    Row {
        WorkspacePanel(Modifier.weight(1f))
        PreviewPanel(Modifier.weight(1.5f))
        ChatPanel(Modifier.weight(1.5f))
    }
}
```

当前实现里，`MainViewModel` 仍然是状态入口，但连接初始化、session/message 同步、SSE/polling、语音转写编排已经拆到同包 helper 文件；这样保留统一状态入口，同时把副作用逻辑按职责分散到更小的单元。

### 5.2 消息渲染

```kotlin
@Composable
fun MessageRow(message: Message) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (message.role == "user") 
                    MaterialTheme.colors.surface.copy(alpha = 0.5f)
                else 
                    MaterialTheme.colors.background
            )
            .padding(16.dp)
    ) {
        message.parts.forEach { part ->
            when (part.type) {
                "text" -> MarkdownText(part.text ?: "")
                "reasoning" -> ReasoningCard(part)
                "tool" -> ToolCard(part)
                "patch" -> PatchCard(part)
            }
        }
    }
}
```

### 5.3 流式显示

```kotlin
@Composable
fun StreamingText(text: String, isStreaming: Boolean) {
    var displayedText by remember(text) { mutableStateOf("") }
    
    LaunchedEffect(text) {
        displayedText = text
    }
    
    Text(displayedText)
}
```

### 5.4 Chat Toolbar 重排（Phase 5，对齐 iOS）

**背景**：当前 Android 的 ChatTopBar 使用 Material 3 `TopAppBar`，session 标题以 `titleSmall` 放在左侧，所有操作按钮（Context ring、Model、Agent、Session list、Settings）堆在右侧 `actions` 区域。iOS 端采用自定义 HStack，左右分区清晰。Phase 5 将 Android 布局对齐 iOS。

**目标布局**：

```
┌─────────────────────────────────────────────────┐
│  Session Title (titleMedium, bold)              │  ← 大标题行
├─────────────────────────────────────────────────┤
│  [☰] [✏] [+]          [Model ▾] [Agent ▾] [◔]  │  ← 按钮行
└─────────────────────────────────────────────────┘
```

**实现方案**：将 `TopAppBar` 替换为自定义 `Column`，分两行：

**第一行：Session 标题**
- 使用 `MaterialTheme.typography.titleMedium`（替代原 `titleSmall`）
- `fontWeight = FontWeight.Bold`
- 单行省略（`maxLines = 1, overflow = TextOverflow.Ellipsis`）
- 左对齐，水平 padding 16.dp

**第二行：操作按钮 HStack**
- 左侧 `Row`（spacing 8.dp）：
  1. Session List（`Icons.Default.List`）：点击打开 ModalBottomSheet
  2. Rename（`Icons.Default.Edit`）：点击弹出 `AlertDialog`，TextField 输入新标题，确认后调用 `updateSessionTitle()`
  3. New Session（`Icons.Default.Add`）：点击创建新 session
- `Spacer(Modifier.weight(1f))`
- 右侧 `Row`（spacing 4.dp）：
  1. Model 下拉（保持现有 `DropdownMenu` 实现）
  2. Agent 下拉（保持现有 `DropdownMenu` 实现）
  3. Context Usage ring（保持现有实现）
  4. Settings 齿轮（仅平板，通过 `showSettingsButton` 控制）

**Rename 对话框**：
```kotlin
@Composable
fun RenameSessionDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Session") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

**影响范围**：
- `ChatTopBar.kt`：主要改动文件，替换 TopAppBar 为 Column + 两行 Row
- `ChatScreen.kt`：新增 `onRenameSession` 回调参数
- `MainViewModel.kt`：`updateSessionTitle()` 已存在，无需改动
- `ChatUiTuning.kt`：可能新增标题字号、按钮间距等常量

### 5.5 Chat 自动跟随策略

- Chat 列表使用 `reverseLayout = true`，底部为索引 0
- 当列表当前停留在底部时，新消息、tool call、streaming delta 到来后自动滚动到索引 0，适合 monitor session
- 当用户主动滚离底部查看历史内容时，自动跟随暂停，避免打断阅读
- 输入栏右侧操作按钮根据输入框高度在横排 / 竖排之间切换；该阈值已收口到 `ChatUiTuning`
- 录音中允许继续发送已有文本，转写中仍阻止重复录音

### 5.5 文件预览模式

- Markdown：直接渲染为视觉化预览
- Text：使用等宽字体原样显示
- Image：按扩展名识别，服务端返回的 base64 内容解码为位图后显示
- preview 路由判断已经下沉到纯 helper，便于独立测试 Markdown / Image / Binary / Text 四类分支
- 图片预览默认 fit-to-screen，支持双击缩放、拖动平移、系统分享
- Android 分享通过 `FileProvider + ACTION_SEND` 实现，对外仅暴露 cache 中的临时文件 URI

---

## 6. 安全设计

### 6.1 凭证存储

```kotlin
class CredentialManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveServerCredentials(url: String, username: String, password: String) {
        sharedPreferences.edit {
            putString("server_url", url)
            putString("auth_username", username)
            putString("auth_password", password)
            putString("ai_builder_base_url", "https://space.ai-builders.com/backend")
            putString("ai_builder_token", "")
        }
    }
}
```

语音相关配置也走 `EncryptedSharedPreferences`：AI Builder Base URL、Token、Custom Prompt、Terminology、上次成功连接签名与时间戳都保存在本地加密存储中。

### 6.2 语音权限与输入行为

- `RECORD_AUDIO` 采用运行时权限请求，未授权时在 Chat 页直接提示
- 录音中允许继续发送当前已输入文本，避免语音输入阻塞文字输入流
- 转写中保留 loading 态，不允许重复点麦克风，避免并发转写状态冲突

### 6.3 SSH 密钥管理（可选）

```kotlin
class SSHKeyManager(context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        )
        keyPairGenerator.initialize(
            KeyGenParameterSpec.Builder("ssh_key", KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return keyPairGenerator.generateKeyPair()
    }
}
```

---

## 7. 项目结构

```
app/
├── src/main/
│   ├── java/com/yage/opencode_client/
│   │   ├── OpenCodeApp.kt            # Application 类
│   │   ├── MainActivity.kt           # 入口 Activity
│   │   ├── data/
│   │   │   ├── api/
│   │   │   │   ├── OpenCodeApi.kt    # Retrofit 接口
│   │   │   │   └── SSEClient.kt      # SSE 客户端
│   │   │   ├── model/                # 数据模型
│   │   │   └── repository/           # 数据仓库
│   │   ├── ui/
│   │   │   ├── chat/
│   │   │   │   └── ChatScreen.kt
│   │   │   ├── files/
│   │   │   │   ├── FilesScreen.kt
│   │   │   │   └── FilePreviewUtils.kt
│   │   │   ├── settings/
│   │   │   └── theme/                # 颜色、字体、Markdown typography
│   │   ├── di/                       # Hilt 模块
│   │   └── util/                     # 工具类
│   ├── res/
│   │   ├── xml/network_security_config.xml
│   │   ├── xml/file_paths.xml
│   │   └── ...
│   └── AndroidManifest.xml
├── build.gradle.kts
└── proguard-rules.pro
```

---

## 8. 依赖配置

当前实现以 version catalog 管理依赖，核心依赖如下：

| 类别 | 当前依赖 |
|------|----------|
| Compose | Compose BOM + Material 3 + activity-compose |
| Lifecycle | lifecycle-runtime-compose + viewmodel-compose |
| Network | OkHttp + OkHttp SSE + Retrofit |
| Serialization | kotlinx-serialization-json |
| DI | Hilt + KSP |
| Security | EncryptedSharedPreferences |
| Markdown | multiplatform-markdown-renderer + m3 adapter |

图片预览与分享暂未引入第三方图片库，直接使用 Android 平台位图解码、Compose 手势系统与 `FileProvider`。

测试方面，当前同时保留两层护栏：

- JVM 单元测试：覆盖 ViewModel 状态机、repository 协议行为、preview/helper 纯函数、音频参数与转写辅助逻辑
- connected Android tests：覆盖关键 Compose 交互，同时对依赖真实服务的 smoke integration 采用“未配置或不可达即 skip”的策略，避免环境噪音污染回归结果

---

## 9. 实现规划

| Phase | 范围 | 预计周期 |
|-------|------|----------|
| 1 | 项目搭建、网络层、SSE、Session、消息发送、流式渲染 | 已完成 |
| 2 | Part 渲染、权限审批、主题、语音输入 | 已完成 |
| 3 | 文件树、Markdown / 图片预览、Diff、平板布局 | 已完成 |
| 5 | UX 对齐 iOS：Chat toolbar 重排（§5.4）、Session Rename UI、草稿持久化（§4.3）、Model/Agent per-session（§4.4） | 2-3 天 |
| 4 | SSH Tunnel（可选） | 1 周 |

---

## 10. 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| Compose 学习曲线 | 团队培训，参考 iOS SwiftUI 经验 |
| SSE 兼容性 | 使用成熟的 OkHttp SSE 库 |
| 平板适配复杂度 | 先完成手机版，平板作为 Phase 3 |
| SSH 库稳定性 | 充分测试，提供降级方案（公网 HTTPS） |

---

## 参考

- [OpenCode Web API](../../../adhoc_jobs/opencode_ios_client/docs/OpenCode_Web_API.md)
- [Android Network Security Config](https://developer.android.com/training/articles/security-config)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
