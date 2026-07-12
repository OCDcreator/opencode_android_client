# 上游吸收状态文档

> **最后更新**: 2026-07-12
> **Fork**: OCDcreator/opencode_android_client ← grapeot/opencode_android_client
> **当前状态**: 本地领先 44 commits,上游领先 140 commits(merge-base `42fd772`)

---

## 一、已完成吸收的功能(三批共 16 项)

### 第一批(高价值低冲突,已合并)

| Commit | 功能 | 说明 |
|--------|------|------|
| `2c9b663` | TodoItem 自定义序列化器 | 处理服务端 `status`/`completed`/`isCompleted` 三种变体 |
| `4523bb9` | ToolCardClassifier + 单测 | 纯逻辑:文件操作/目录读取/条目解析分类器 |
| `4523bb9` | ui_driver Python 测试工具 | 17 文件 adb/uiautomator 自动化测试框架 |
| `4523bb9` | Markdown Web Preview | WebView + markdown-it + DOMPurify,Web/Native/Source 三态 |
| `4523bb9` | 测试依赖升级 | espresso 3.7.0, junit 1.2.1 |

### 第二批(高价值低冲突,逐项验证)

| Commit | 上游来源 | 功能 |
|--------|----------|------|
| `6144cb6` | `974b265` | **防重复发送** — `sendingSessionIds` 守卫 + `onComplete` 回调 |
| `11e78f7` | `7637ba8`+`a5805c4` | **Web Preview 预热+刷新** — OpenCodeApp WebView 预热 + windowBackground + FilesViewModel refreshPreview |
| `40f9d8a` | `dbdb95c`+ | **CI 弃用警告清零** — AutoMirrored 图标、SSEClient Json 复用、AnchoredDraggableState 迁移、`@param:ApplicationContext`、MediaRecorder API 31+ |
| `8365a9b` | `d1fc737`+`a7ab8ae` | **只读工具灰色 tint** — `readOnlyToolPrefixes` + Build 图标 onSurfaceVariant |
| `fdc7164` | `5e2cd07` | **会话列表副标题** — 相对时间("5 分钟前") + 状态标签(运行中/重试中/空闲),中英文本地化 |
| `691d9c6` | `dfbb346` | **PRD Steer 产品哲学** — Surface→Review→Steer 闭环 + 重度用户时间分布 |

### 第三批(中冲突,手动移植+逐项验证)

| Commit | 上游来源 | 功能 |
|--------|----------|------|
| `149ee05` | `e59c6c5`+`75f76cf`+`3003e3c` | **Todo 面板** — TodoListPanel + SSE `todo.updated` + REST 加载 + 工具栏按钮+徽章 |
| `ed2e627` | `7c1c947` | **工作区链接预览** — WorkspaceMarkdownLinkResolver(含遍历安全防护) + ChatScreen Dialog + 113 行单测 |
| `846e1bb` | `88a0216`+`f6af110` | **Agent 活动+计时** — 实时状态字符串 + m:ss 计时器 + sessionSendTimestamps 预窗口 fallback |
| `6654fa0` | `6d2f823` Layer-2 | **FileCard 无障碍** — testTag `toolcard.read/write.*` + contentDescription + 组件测试 |
| `6d7763a` | `1f95db7` | **平板面板折叠** — sessionsPaneCollapsed + 浮层展开按钮 + rememberSaveable |

### Fork 自有创新(非上游)

| Commit | 功能 |
|--------|------|
| `afb8648` | 聊天滚动修复(derivedStateOf + 每 session LazyListState 位置记忆 + 回到底部按钮) |
| `afb8648` | 会话列表客户端目录过滤(showAllSessions + totalSessionCount + 空状态提示) |
| `afb8648` | Markdown 表格自适应宽度(BoxWithConstraints + horizontalScroll) |
| `78b8bfd` | session list 切换到 experimental 端点支持跨项目目录过滤 |
| `a5a9948` | 502/503 GET 请求重试拦截器(frp 隧道断线保护) |
| — | ImageAttachmentCompressor(EXIF 旋转 + alpha 展平,优于上游) |
| — | ContextUsageBottomSheet(比上游 AlertDialog 更丰富的上下文详情) |
| — | uiScaled() 无障碍缩放系统(上游已删除,本地保留) |
| — | wrapWithLanguage() 语言切换(比上游 AppLocaleController 更兼容旧 API) |

---

## 二、已确认跳过的上游特性

| 特性 | 跳过理由 |
|------|----------|
| **NFC Quick Prompt** (`0a42b3b`) | 硬依赖 SSH 先吸收;需引入 AppCompat;小众硬件功能;上游自身 6 轮调试仍不稳定 |
| **模型预设更新** (16 commits) | 纯数据 churn(MiniMax→Kimi→GLM 等),无代码结构变化;需要时直接编辑 ModelPresets.kt |
| **i18n AppLocaleController** | 本地已有更健壮的 wrapWithLanguage()+attachBaseContext;引入会创建第二套冲突机制 |
| **上游图片附件系统** | 本地 ImageAttachmentCompressor 严格更优(EXIF + alpha + 缩略图 + 逐图错误) |
| **移除 agent 选择器** (`28249c1`) | 本地有意保留并定制 agent 选择器(ModelAndAgentPickerPopup),设计分歧 |
| **"OpenCode" 说话人标题** (`71d7f78`) | 上游自己加完又删;本地从未加 |
| **上下文详情 AlertDialog** (`1afc93a`) | 本地已有更丰富的 ContextUsageBottomSheet(ModalBottomSheet) |
| **VoiceFlowKit 实时语音** | 外部 JitPack 依赖(2 star 单人维护);本地批量语音已可用;替换=删除本地 703 行 |

---

## 三、关键技术决策(影响后续吸收)

### 3.1 MainViewModel 构造器分歧(核心阻碍)

```
本地:  (repository, settingsManager, audioRecorderManager, imageCompressor)
上游:  (repository, settingsManager, voiceFlowClient, microphone, hostProfileStore, tunnelManager, sshKeyManager)
```

任何涉及 VM 构造器或 AppState 结构的上游特性,都需要手动移植而非 cherry-pick。

### 3.2 CRLF 行尾(机械阻碍)

本地 4 个核心文件为 CRLF(SettingsManager/MainViewModel/MainViewModelConnectionActions/MainActivity),上游为 LF。涉及这些文件的 cherry-pick 全行冲突。**建议后续吸收前先 `.gitattributes` + `git add --renormalize` 统一为 LF。**

### 3.3 uiScaled() 系统(设计分歧)

本地 pervasive 使用 `uiScaled()`,上游已删除。所有涉及 `.dp` 的上游 diff 需手动加回 `uiScaled()`。

### 3.4 字符串键命名(约定分歧)

本地用扁平键(`dismiss`/`cancel`/`settings`),上游用命名空间键(`common_dismiss`/`common_cancel`/`settings_title`)。新增字符串继续用本地扁平约定。

---

## 四、验证基线

每项吸收后执行:
```bash
./gradlew clean assembleDebug          # 零 w: 警告
./gradlew testDebugUnitTest            # 全部单测通过
```

当前状态(2026-07-12):
- ✅ `assembleDebug` 零警告
- ✅ 全部单测通过
- ✅ 已推送到 origin/master
