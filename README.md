# OpenCode Android Client

OpenCode 的原生 Android 客户端，用于远程连接 OpenCode 服务端、发送指令、监控 AI 工作进度、浏览代码变更。

## 功能概述

- **Chat**：发送消息（支持多图）、切换模型和 Agent、查看 AI 回复与工具调用（Markdown 渲染、Patch diff、Todo 列表、Tool 卡片展开/折叠）
- **SSE 实时流**：完整支持所有 Part 类型渲染（文本、工具调用、工具结果、Reasoning 等），流式输出逐字显示
- **Question 卡片**：接收服务端 AI 提问，支持在 Chat 中直接回复或拒绝
- **Files**：文件树浏览、git 状态标记、代码与 Markdown 预览
- **Settings**：服务器连接配置、Basic Auth 认证、工作目录选择、主题切换（Light / Dark / System）、无障碍设置
- **SSH 隧道**：支持通过 SSH 端口转发安全连接到仅监听 localhost 的 OpenCode 服务端，无需公网暴露；自动生成密钥对、TOFU 主机密钥验证、隧道断线自动重连
- **诊断日志**：内置集中式日志模块（内存环形缓冲 + 按天滚动持久化文件），app 内可实时查看、按级别/分类筛选、关键词搜索、一键导出分享
- **语音输入**：通过 AI Builder WebSocket API 实时语音转写
- **平板适配**：手机底部 Tab 导航，平板三栏布局（文件 / 预览 / Chat）

## 与原版差异

本仓库基于 [OCDcreator/opencode_android_client](https://github.com/OCDcreator/opencode_android_client) fork，在原版基础上增加了以下改进：

### UI 优化

- **模型/Agent 双栏选择器**：模型和 Agent 分为左右两栏并排显示，按 Provider 分组可展开折叠，Agent 面板可独立折叠
- **动态模型列表**：模型从服务端动态获取，替代原版硬编码预设列表
- **全屏目录选择器**：工作目录选择改为全屏对话框，路径显示完整，支持快捷路径和书签
- **输入栏重设计**：重新设计输入栏布局，支持图片预览、收起展开
- **Tool 卡片半宽布局**：工具调用卡片采用半宽网格排列，信息密度更高
- **用户消息折叠**：长用户消息可展开/折叠，减少滚动距离
- **连续助手消息合并**：连续的助手消息自动视觉合并，对话流更连贯
- **输入框高度修复**：修复多行输入删除内容后不恢复单行高度的问题
- **中文输入法修复**：使用本地文本状态避免中文 IME 重复输入问题
- **键盘遮挡修复**：修复键盘弹出时 Question 卡片被遮挡的问题
- **聊天列表滚动优化**：修复流式输出时列表跳动到顶部的问题；支持手动上滑浏览历史、滑回底部自动恢复贴底；每个会话独立记忆滚动位置；右下角"回到底部"浮动按钮
- **Markdown 表格渲染**：自定义表格组件，宽表格可横向滚动，单元格内容多行换行不再截断；表格撑满消息容器宽度，无右侧空白

### 新功能

- **SSH 隧道连接**：支持通过 SSH 端口转发连接到仅监听 localhost 的 OpenCode 服务端（如 Mac mini 本地运行的 `127.0.0.1:65358`），无需将服务端暴露到公网或局域网。App 自动生成 ed25519 密钥对，支持 TOFU 主机密钥验证；冷启动自动重建隧道；隧道断线自动重连（15 秒检测周期，自动重建 + 重配 repository + 重启 SSE）
- **诊断日志模块**：内置集中式 `AppLogger`，接管全项目所有日志输出（取代散落的 `android.util.Log`）。设置 → 诊断日志可查看全部日志，支持按级别（Debug/Info/Warn/Error）和分类（Connection/SSH/Session/Stream/Repository/Audio/UI）筛选、关键词搜索、复制、一键导出分享
- **日志持久化**：日志按天滚动写入磁盘（每天最多 5 个文件 × 5MB，保留最近 7 天），app 被系统杀掉或重启后历史日志仍然可见，便于事后排查连接和隧道问题
- **多图输入**：支持在 Chat 中附加多张图片一并发送
- **工作目录支持**：设置中可配置工作目录，SSE 事件按目录过滤，支持多项目管理
- **跨项目会话列表**：使用实验性 API 获取跨项目的所有会话，客户端按工作目录前缀过滤（支持子目录）；目录无匹配时显示空状态提示并提供"显示所有会话"入口
- **Question 回复/拒绝**：支持在 Chat 中直接回复或拒绝 AI 的提问，目录参数正确传递
- **中英文切换**：设置中新增语言切换（跟随系统 / English / 中文），点击即时生效，无需重启
- **隐藏麦克风图标**：语音识别设置中新增开关，可隐藏会话输入栏的麦克风图标
- **无障碍设置**：字体大小滑块 + UI 缩放滑块，支持大范围调节，所有界面适配缩放
- **上下文用量详情**：新增上下文用量详情面板，查看 Token 使用明细

## 环境要求

- Android 8.0+（API 26）
- Android Studio（用于构建）
- 运行中的 OpenCode Server（`opencode serve` 或 `opencode web`）

## 快速开始（局域网）

1. 在电脑上启动 OpenCode：`opencode serve --port 4096`
2. 打开 Android App，进入 Settings，填写服务器地址（如 `http://192.168.x.x:4096`）
3. 点击 Test Connection 验证连接
4. 在 Chat 中创建或选择 Session，开始对话

## 远程访问

默认为局域网使用。远程访问推荐以下方案：

### SSH 隧道（推荐：服务端仅监听 localhost 时）

当 OpenCode 服务端仅监听 `127.0.0.1`（如 Mac mini 本地运行）时，使用 SSH 隧道是最安全的远程方案——无需将服务端暴露到公网或局域网：

1. 服务器上确保 SSH 服务已开启（macOS：系统设置 → 通用 → 共享 → 远程登录）
2. App Settings → 连接配置 → 新建或编辑 Profile，传输方式选择 **SSH Tunnel**
3. 填写 SSH 网关地址（服务器 IP）、SSH 端口（默认 22）、SSH 用户名、分配的远程端口（OpenCode 监听端口，如 65358）
4. 复制 App 显示的 SSH 公钥，添加到服务器的 `~/.ssh/authorized_keys`
5. 保存后 App 自动建立 SSH 隧道，所有流量通过加密隧道转发到服务端的 localhost

App 会自动管理密钥对、TOFU 主机密钥验证、隧道断线重连。冷启动时自动重建隧道。

### HTTPS + 公网服务器

将 OpenCode 部署在公网服务器上，使用 HTTPS 加密：

1. 服务器上运行 OpenCode，配置 TLS
2. App Settings 中填写 `https://your-server.com:4096`
3. 配置 Basic Auth 用户名和密码

### Tailscale

通过 Tailscale 组网，Android 设备和 OpenCode 服务器处于同一 tailnet：

1. 两端安装并登录 Tailscale
2. App Settings 中填写 Tailscale MagicDNS 地址（如 `http://your-machine.tail*****.ts.net:4096`）

App 已对 `*.ts.net` 域名配置 HTTP 豁免，无需额外设置。

## 诊断日志

App 内置集中式日志模块，用于排查连接和隧道问题：

1. 进入 Settings → 滚动到「诊断日志」分区
2. 点「查看日志」打开全屏日志查看器
3. 支持按级别（Debug/Info/Warn/Error）和分类（Connection/SSH/Session/Stream/Repository/Audio/UI）筛选
4. 支持关键词搜索、复制到剪贴板、一键导出为文本文件分享

日志同时写入磁盘持久化（按天滚动，每天最多 25MB，保留最近 7 天），app 被系统杀掉或重启后历史日志仍可查看。

## 构建

```bash
# 设置 JDK（使用 Android Studio 自带的）
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 构建
./gradlew assembleDebug

# 单元测试
./gradlew testDebugUnitTest

# 测试覆盖率
./gradlew koverHtmlReport
# 报告位于 app/build/reports/kover/html/index.html
```

集成测试需要运行中的 OpenCode Server，将 `.env.example` 复制为 `.env` 并填入实际凭证后执行：

```bash
./gradlew connectedDebugAndroidTest
```

## 项目结构

```
app/src/main/java/com/yage/opencode_client/
├── data/
│   ├── api/          # REST API 接口、SSE 客户端
│   ├── audio/        # 语音录制、重采样、WebSocket 转写
│   ├── model/        # 数据模型（Session、Message、File、HostProfile 等）
│   └── repository/   # 数据仓库层，封装 API/SSE/工作目录感知调用
├── di/               # Hilt 依赖注入
├── ssh/              # SSH 隧道管理（JSch）、密钥对生成、TOFU 主机密钥验证
├── ui/
│   ├── chat/         # Chat 页面、消息列表/内容、输入栏、上下文面板、Question 卡片
│   ├── files/        # Files 页面 + FilesViewModel + 预览/导航
│   ├── session/      # Session 列表与树形展示
│   ├── settings/     # Settings 页面、连接配置、诊断日志查看器
│   └── theme/        # 主题、字体、UI 缩放
└── util/             # SettingsManager、AppLogger、LogFileWriter、ThemeMode 等
```

## 技术栈

- Jetpack Compose + Material 3
- OkHttp + Retrofit（网络）
- Kotlin Serialization（JSON）
- Hilt（依赖注入）
- EncryptedSharedPreferences（安全存储）
- JSch + BouncyCastle（SSH 隧道）
- Kover（测试覆盖率）

## 文档

- `docs/PRD.md` — 产品需求
- `docs/RFC.md` — 技术方案
- `docs/speech_recognition.md` — 语音转写设计

## 姊妹项目

- [OpenCode iOS Client](https://github.com/grapeot/opencode_ios_client) — iOS 原生客户端，功能对等

## License

与 OpenCode 保持一致。
