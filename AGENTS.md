# AGENTS.md - opencode_android_client

OpenCode native Android client. Jetpack Compose + Material 3, targeting API 26+.

## Commands

| Task | Command |
|------|---------|
| Debug APK | `./gradlew assembleDebug` |
| All unit tests | `./gradlew testDebugUnitTest` |
| Single test class | `./gradlew testDebugUnitTest --tests "com.yage.opencode_client.MainViewModelTest"` |
| Coverage report | `./gradlew koverHtmlReport` |
| Integration tests | `./gradlew connectedDebugAndroidTest` |

## Environment

- macOS may need Android Studio's bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
```

- On Windows, keep `JAVA_HOME` and Android SDK `platform-tools` on `PATH`.
- `connectedDebugAndroidTest` reads `.env` values for `OPENCODE_*` and `AI_BUILDER_*`.
- If Android Studio says module missing, run Sync Project with Gradle Files. The run config module is `opencode_client.app`.

## Architecture

```text
app/src/main/java/com/yage/opencode_client/
├── data/
│   ├── api/          # Retrofit API + SSE client
│   ├── audio/        # Recorder + AI Builder speech client
│   ├── model/        # @Serializable API/session/message/file DTOs
│   └── repository/   # OpenCodeRepository; wraps API, SSE, working-directory aware calls
├── di/               # Hilt AppModule
├── ui/
│   ├── chat/         # Chat screen, message list/content, input, context sheet, questions
│   ├── files/        # FilesScreen + FilesViewModel + preview/navigation helpers
│   ├── session/      # Session list/tree
│   ├── settings/     # Settings screen and sections
│   ├── theme/        # Theme, typography, UI scale helpers
│   ├── MainViewModel.kt
│   ├── MainViewModelConnectionActions.kt
│   ├── MainViewModelSessionActions.kt
│   ├── MainViewModelSpeechActions.kt
│   ├── MainViewModelSyncActions.kt
│   ├── MainViewModelSupport.kt
│   ├── ModelPresets.kt
│   └── StreamDebugLogger.kt
├── util/             # SettingsManager + ThemeMode
├── MainActivity.kt   # Phone nav + tablet 3-column layout
└── OpenCodeApp.kt    # @HiltApplication
```

## Working Rules

- `MainViewModel` owns the single `AppState`; update with `_state.update { it.copy(...) }`. `FilesViewModel` is the only separate state holder.
- `OpenCodeRepository.configure()` is the source of truth for base URL, auth, and working directory. Most repository calls flow through `effectiveDirectory()`.
- Chat sync is SSE-first with busy-poll fallback. Streaming UI state lives in `streamingPartTexts` and `streamingReasoningPart`; keep refresh/clear logic in sync actions, not in composables.
- Session drafts, selected agent, and selected model are persisted per session in `SettingsManager`. `loadMessages()` restores saved choices before inferring from the latest assistant message.
- Font size and UI scale are first-class app settings. New UI should respect `ProvideScaledDpDensity`, `uiScaled()`, and remain usable in bottom sheets/dialogs at larger scales.
- `StreamDebugLogger` is debug-only tracing for send/stream/message refresh behavior; keep production behavior independent from it.

## OpenCode Server Source

The upstream OpenCode server (TypeScript monorepo) is at:

```
/Volumes/SDD2T/obsidian-vault-write/open-source-project/AI-tools-agents/opencode/
```

Key server paths:

| Path | Purpose |
|------|---------|
| `packages/opencode/src/server/routes/instance/httpapi/` | HTTP API route handlers + middleware |
| `packages/opencode/src/server/routes/instance/httpapi/handlers/question.ts` | Question reply/reject handlers |
| `packages/opencode/src/server/routes/instance/httpapi/middleware/workspace-routing.ts` | `?directory=` / `?workspace=` query param → instance resolution |
| `packages/opencode/src/question/index.ts` | Question service: ask/reply/reject with per-instance pending map |
| `packages/opencode/src/question/schema.ts` | QuestionID (`que_` prefix) schema |
| `packages/opencode/src/session/message-v2.ts` | Part types (TextPartInput, FilePartInput, etc.) |
| `packages/sdk/js/src/v2/gen/types.gen.ts` | SDK-generated request/response types |

## References

- `docs/agents/style.md` - coding conventions, state/viewmodel/repository rules, Compose patterns
- `docs/agents/testing.md` - test commands, unit/instrumented test patterns, verification guidance

## Key Files

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/java/com/yage/opencode_client/ui/MainViewModel.kt`
- `app/src/main/java/com/yage/opencode_client/ui/MainViewModelSupport.kt`
- `app/src/main/java/com/yage/opencode_client/data/repository/OpenCodeRepository.kt`
- `app/src/main/java/com/yage/opencode_client/data/api/OpenCodeApi.kt`
- `app/src/main/java/com/yage/opencode_client/util/SettingsManager.kt`
