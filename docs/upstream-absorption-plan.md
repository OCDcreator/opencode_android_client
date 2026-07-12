# 上游剩余项吸收规划

> **最后更新**: 2026-07-12
> **关联文档**: [upstream-absorption-status.md](./upstream-absorption-status.md)

本文档定义剩余可吸收的上游特性,按优先级排序,每项含完整实施方案。执行流程:逐项规划 → 子代理实现 → 验证通过 → 规划下一项 → 继续。

---

## 执行流程(每项)

```
1. 规划(本文档):确定方案、文件清单、验证标准
2. 实现(子代理):在 git worktree 或 feature branch 执行
3. 验证:
   - ./gradlew clean assembleDebug  (零 w: 警告)
   - ./gradlew testDebugUnitTest    (全部通过)
   - 手动/集成测试(如适用)
4. 通过 → 提交+推送 → 规划下一项
   失败 → 修复 → 重新验证
```

---

## 前置准备(建议先做)

### P0. EOL 统一(LF)

**问题**: 4 个核心文件为 CRLF,导致涉及它们的 cherry-pick 全行冲突。

**方案**:
```bash
# 添加 .gitattributes
*.kt text eol=lf
*.xml text eol=lf
*.kts text eol=lf
*.toml text eol=lf
*.md text eol=lf

# 统一
git add .gitattributes
git add --renormalize .
git commit -m "chore: normalize line endings to LF"
```

**影响**: 所有后续吸收项受益。
**风险**: 一次性的大 diff(纯行尾变化),但内容零变化。

**优先做此项**,后续所有涉及 MainViewModel/SettingsManager/MainActivity 的吸收都更顺畅。

---

## 剩余项(按优先级排序)

### 1. Edit Rewind(编辑回退)— 选择性吸收 API 层

**上游 commit**: `7c441d9` (部分)
**价值**: 用户消息溢出菜单"从此处编辑"→服务端截断对话→预填原文,可编辑重发
**冲突**: 中-高(ChatMessageContent + MainViewModel 已分歧)

**实施方案(分两步)**:

**步骤 A — API + Repository + Model 层(低冲突)**:
- `Session.kt`: 加 `val revert: RevertInfo?` 字段(`RevertInfo(messageId, partId, snapshot, diff)`)
- `OpenCodeApi.kt`: 加 `@POST("session/{id}/revert")` + `RevertSessionRequest(messageID, partID?)`
- `OpenCodeRepository.kt`: 加 `suspend fun revertSession(sessionId, messageId, partId?): Result<Session>`
- 新增单测验证 API 调用

**步骤 B — VM + UI 层(中冲突,可延后)**:
- `MainViewModel.kt`: 加 `fun editFromMessage(messageId)`,调用 revertSession,更新 session,加载草稿
- `AppState`: 加 `visibleMessages` 派生属性(过滤 `id >= revert.messageId` 的消息)
- `ChatMessageContent.kt`: 用户消息溢出菜单加"从此处编辑"选项,调 `onEditFromMessage`

**验证**:
- 步骤 A: 单测验证 revertSession 调用 + RevertInfo 反序列化
- 步骤 B: 手动测试编辑回退流程

**建议**: 先做步骤 A(API 层独立可用),步骤 B 作为后续 UI 任务。

---

### 2. Quiet Tech 色彩 token(仅取 token,非整主题)

**上游 commit**: `c8b06b1`
**价值**: 视觉一致性(品牌蓝 #3B82F6、警告红 #E5484D、中性灰阶)
**冲突**: 高(上游删除了本地的 fontSizeScale/uiScale/scaledTypography)

**实施方案(选择性)**:
- `Color.kt`: 新增上游的 brand token 常量(`BrandPrimary`、`StopRed`、`BrandGold` + 中性灰阶 `BgDark`/`SurfaceDark`/`OnSurfaceDark` 等)
- `Theme.kt`: 将现有 `darkColorScheme`/`lightColorScheme` 的 container slot 映射替换为 brand token,**保留** `dynamicColor`、`fontSizeScale`、`uiScale`、`scaledTypography`、`ProvideScaledDpDensity`
- **不改动**: Type.kt(保留 scaledTypography)、UiScale.kt、ChatInputBar(保留本地布局)

**验证**:
- `./gradlew assembleDebug` 编译通过
- 截图对比深色/浅色主题下各页面(顶栏、卡片、输入栏)颜色是否协调
- 确认 fontSizeScale/uiScale slider 仍生效

**风险**: 色彩微调可能需要几轮视觉验证;不影响功能。

---

### 3. SSH Host Profiles + 隧道

**上游 commit**: `d43b2c4` + `bef9786` + `15a51e6` + `20ab9a0`
**价值**: 多连接配置管理 + SSH 隧道远程访问(不依赖 frp/Tailscale)
**冲突**: 极高(重构 MainViewModel 构造器 + AppState + 删本地工作目录 UI)

**前置**: 必须先做 P0(EOL 统一)

**实施方案(手动移植,非 cherry-pick)**:

1. **新依赖** (`build.gradle.kts` + `libs.versions.toml`):
   - `com.github.mwiede:jsch:0.2.26`
   - `org.bouncycastle:bcprov-jdk18on:1.78.1`

2. **5 个新文件(直接复制,零冲突)**:
   - `data/model/HostProfile.kt` — `HostTransport.DIRECT`/`SSH_TUNNEL` + `SshTunnelConfig` + `BasicAuthConfig`
   - `data/repository/HostProfileStore.kt` — EncryptedSharedPreferences JSON 持久化 + 导入/导出
   - `ssh/KnownHostStore.kt` — TOFU 主机指纹 SHA-256 锁定
   - `ssh/SSHKeyManager.kt` — ed25519 设备密钥对生成(BouncyCastle)
   - `ssh/TunnelManager.kt` — JSch local port-forward

3. **Hilt provider** (`AppModule.kt`):
   - 加 `provideTunnelManager` provider

4. **SettingsManager 扩展**(加属性,不改现有):
   - `hostProfilesJson`、`currentHostProfileId`、`sshPrivateKeyPem`、`sshPublicKey`、`knownHostsJson`、`basicAuthPassword`/`setBasicAuthPassword`

5. **MainViewModel 手动合并**(最复杂):
   - 构造器加 3 参数(`hostProfileStore`、`tunnelManager`、`sshKeyManager`)
   - AppState 加 `hostProfiles`、`currentHostProfileId`、`connectionPhase` 字段
   - `testConnection` 重写为按 transport 分阶段诊断(Direct: health; SSH: gateway→auth→tunnel→health)
   - `applySavedSettings` 签名扩展,线程 `hostProfileStore` **同时保留** `workingDirectory`/model-key/scale 状态
   - 所有 MainViewModel 构造测试更新签名

6. **Settings UI 重建**(高冲突):
   - `ConnectionProfileSection` 替换 `ServerConnectionSection`,**但必须重新加入**工作目录+最近目录+浏览控件(SSH 重写会删除它们)
   - `HostProfilesManagerScreen` — profile 列表/编辑/详情/设备公钥/密钥轮换确认

**验证**:
- 5 个新文件的单元测试(HostProfileStoreTest、KnownHostStoreTest、SSHKeyManagerTest、HostProfileImportExportTest — 基本可 drop-in)
- MainViewModel 构造器测试全部更新
- 手动:创建 Direct profile 连接现有服务器;创建 SSH Tunnel profile 连接远程

**预估工作量**: 1-2 天聚焦工作
**建议**: 作为独立 feature branch(`feat/ssh-host-profiles`)

---

### 4. 会话归档工作流

**上游 commit**: `cbc5034` + `77e744b` + `f230812` + `e2e6123` + `017ccea`
**价值**: active/archived 分区 + 双向滑动(Archive/Restore + Delete)
**冲突**: 高(SessionList 从零重写 + 破坏性 API 变更)

**关键冲突点**:
- `UpdateSessionRequest` 破坏性变更:`title: String` → `title: String? = null, time: UpdateSessionTimeRequest?`
- SessionList 完全重写,会**替换**本地的 showAllSessions/totalSessionCount/空状态提示 UI
- 两种列表哲学冲突:本地"目录过滤"vs 上游"active/archived 分区" — 需设计融合方案

**实施方案**:
1. **API/Model 层**:
   - `Session.kt`: 加 `val isArchived: Boolean get() = (time?.archived ?: 0L) > 0L`
   - `OpenCodeApi.kt`: `UpdateSessionRequest` 改为可选 title + `UpdateSessionTimeRequest(archived: Long?)`
   - 更新所有 `updateSession` 调用点

2. **VM 层**:
   - `archiveSession(id)` / `restoreSession(id)` → 调用更新 API 设 archived 时间戳
   - `sendMessage` 前检查 `currentSession.isArchived`,自动 restore

3. **UI 层(需设计融合)**:
   - 决定:目录过滤如何与 active/archived 分区共存?
   - 方案 A:在 active 分区内应用目录过滤(推荐)
   - 方案 B:目录过滤作用于全部,archive 仅是状态标记
   - SwipeRevealRow 改为双向滑动:左滑 Archive/Restore,右滑 Delete
   - 列表分两个 section:active + archived(可折叠)

**验证**:
- 单测:archive/restore API 调用、isArchived 派生属性、auto-restore on send
- 手动:滑动归档/恢复、目录过滤与归档共存

**预估工作量**: 1 天
**前置**: 设计决策(目录过滤 × 归档分区的融合方案)

---

## 优先级建议

| 优先级 | 项 | 理由 |
|--------|-----|------|
| **P0** | EOL 统一 | 所有后续项的前置;纯机械,低风险 |
| **P1** | Edit Rewind 步骤 A | API 层独立可用,低冲突,高价值 |
| **P2** | Quiet Tech 色彩 token | 纯视觉提升,选择性吸收保留缩放系统 |
| **P3** | SSH Host Profiles | 高价值但极高冲突,独立 feature branch |
| **P4** | 会话归档 | 需设计决策,破坏性 API 变更 |

---

## 已排除项(不在规划中)

见 [upstream-absorption-status.md §二](./upstream-absorption-status.md#二已确认跳过的上游特性) — NFC、模型预设、AppLocaleController、上游图片附件、移除 agent 选择器、VoiceFlowKit 均已确认跳过。
