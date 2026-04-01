# AGENTS.md - opencode_android_client

OpenCode native Android client. Jetpack Compose + Material 3, targeting API 26+ (Android 8.0).

## Build & Environment

On macOS, the terminal may not find Java. Use Android Studio's bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
# For integration tests (adb):
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
```

Persist in `~/.zshrc` then `source ~/.zshrc`. On Windows, ensure `JAVA_HOME` and the Android SDK `platform-tools` are on `PATH`.

## Build Commands

| Task | Command |
|------|---------|
| Debug APK | `./gradlew assembleDebug` |
| All unit tests | `./gradlew testDebugUnitTest` |
| Single test class | `./gradlew testDebugUnitTest --tests "com.yage.opencode_client.MainViewModelTest"` |
| Single test method | `./gradlew testDebugUnitTest --tests "com.yage.opencode_client.MainViewModelTest.sendMessage success clears input and uses selected preset model"` |
| Coverage report | `./gradlew koverHtmlReport` → `app/build/reports/kover/html/index.html` |
| Integration tests | `./gradlew connectedDebugAndroidTest` (requires `.env` with `OPENCODE_*` and `AI_BUILDER_*` credentials) |

If "Module not found": File → Sync Project with Gradle Files. Run config uses module `opencode_client.app`.

## Project Structure

```
app/src/main/java/com/yage/opencode_client/
├── data/
│   ├── api/          # OpenCodeApi (Retrofit interface), SSEClient
│   ├── audio/        # AudioRecorderManager, AIBuildersAudioClient
│   ├── model/        # @Serializable data classes (Session, Message, Part, SSE, etc.)
│   └── repository/   # OpenCodeRepository (single @Singleton, wraps API + SSE)
├── di/               # Hilt AppModule
├── ui/
│   ├── chat/         # ChatScreen, ChatInputBar, ChatTopBar, ChatMessageContent, QuestionCardView
│   ├── files/        # FilesScreen, FilesViewModel, FileBrowserPane, FilePreviewPane
│   ├── session/      # SessionList, SessionTree
│   ├── settings/     # SettingsScreen, SettingsSections
│   ├── theme/        # Color, Theme, Type
│   ├── MainViewModel.kt          # @HiltViewModel, single AppState StateFlow
│   ├── MainViewModel*Actions.kt  # Extension functions split by concern
│   ├── MainViewModelSupport.kt   # Internal helpers (parsing, timings, utilities)
│   └── ModelPresets.kt
├── util/             # SettingsManager (EncryptedSharedPreferences wrapper)
├── MainActivity.kt   # Navigation, phone/tablet layout switching
└── OpenCodeApp.kt    # @HiltApplication
```

## Tech Stack

Jetpack Compose + Material 3 + WindowSizeClass · OkHttp 4 + Retrofit 2 + kotlinx-serialization-json · Hilt (KSP) · kotlinx.coroutines + StateFlow · EncryptedSharedPreferences · multiplatform-markdown-renderer · JUnit 4 + MockK + Turbine + Kover · Kotlin 2.2.x, Java 17 target

## Code Style

### Language & Formatting

- Kotlin only. No Java source files.
- 4-space indent, no tabs.
- Max line length ~120; follow existing file's line length.
- Trailing commas in multi-line parameter/argument lists.
- Single blank line between method groups; no double blank lines.
- Do NOT add comments unless explicitly requested.

### Imports

- Use wildcard imports for `kotlinx.serialization.json` (`buildJsonObject`, `JsonPrimitive`, etc.) and within `data.model` subpackage (`import com.yage.opencode_client.data.model.*`).
- Android platform imports (`android.util.Log`, `android.os.Bundle`) come first.
- Then androidx / compose imports.
- Then third-party (dagger, kotlinx, okhttp, retrofit).
- Then project imports (`com.yage.opencode_client.*`).
- No unused imports.

### Naming Conventions

- **Packages**: all lowercase, dot-separated (`com.yage.opencode_client.data.model`).
- **Classes/Interfaces**: PascalCase (`ChatScreen`, `OpenCodeRepository`, `AppState`).
- **Functions/properties**: camelCase (`loadMessages`, `currentSessionId`).
- **Constants**: `private const val` in companion objects, SCREAMING_SNAKE_CASE (`DEFAULT_SERVER`, `TAG`).
- **Composable functions**: PascalCase (`ChatScreen`, `PhoneLayout`).
- **State holder data classes**: PascalCase with `State` suffix (`ChatState`, `SessionState`).
- **Test method names**: backtick descriptive strings (`` `sendMessage success clears input and uses selected preset model` ``).

### Data Models

- Use `@Serializable` on all API DTOs.
- Use `@SerialName("fieldName")` for camelCase→snake_case or Go-style naming mismatches (`@SerialName("sessionID")`, `@SerialName("parentID")`).
- Nullable fields use `val field: Type? = null`.
- Computed properties for UI display: `val displayName: String get() = ...`.
- Custom serializers as `private object` within the same file (see `PartStateSerializer`, `PartFilesSerializer`).

### State Management

- Single `AppState` data class in `MainViewModel.kt`. All UI state in one `StateFlow<AppState>`.
- Update state via `_state.update { it.copy(...) }` — never mutate fields directly.
- Decompose into sub-states (`ConnectionState`, `SessionState`, `ChatState`, `SpeechState`) via computed properties.
- Compose screens collect via `viewModel.state.collectAsStateWithLifecycle()`.

### ViewModel Pattern

- `MainViewModel` is `@HiltViewModel`. Complex logic is extracted into top-level `internal fun` extensions in `MainViewModel*Actions.kt` files.
- Extension functions take explicit `scope`, `state: MutableStateFlow<AppState>`, and `repository` params — keeping `MainViewModel` as a thin dispatcher.
- All async work runs in `viewModelScope.launch {}`.
- Error handling: use `Result<T>` from repository, then `.onSuccess { ... }.onFailure { error -> ... }`.

### Repository Pattern

- `OpenCodeRepository` is `@Singleton`, manually creates OkHttp/Retrofit clients.
- All suspend functions return `Result<T>` via `runCatching { api.call() }`.
- For `Response<Unit>` endpoints, check `response.isSuccessful` and throw descriptive exception on failure.
- `configure()` is `@Synchronized` — rebuilds OkHttp + Retrofit + SSEClient on URL change.

### Dependency Injection

- Hilt with KSP (not kapt).
- `@Module @InstallIn(SingletonComponent::class) object AppModule` in `di/AppModule.kt`.
- `@Inject constructor()` on repository; `@Provides @Singleton` for manual provisions.
- ViewModels use `@HiltViewModel` + `@Inject constructor()`.

### Compose UI

- Use `@OptIn(ExperimentalMaterial3Api::class)` and `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)` where needed.
- Screen composables accept `viewModel: MainViewModel` parameter.
- Navigation: `NavHost`/`NavGraph` with sealed class `Screen` routes. Phone: `NavigationBar` with 3 tabs. Tablet (`WindowWidthSizeClass.Expanded`): 3-column `Row`.
- `Modifier` parameter always first in custom composable signatures after required params.

### Error Handling

- Repository: wrap every API call in `runCatching {}`. Return `Result<T>`.
- ViewModel: handle `Result` with `.onSuccess {} / .onFailure {}`. Surface user-facing error via `AppState.error` (String?).
- Non-critical failures: `reportNonFatalIssue(tag, message)` → `Log.w()`.
- User-facing error messages: use `errorMessageOrFallback(throwable, "Fallback message")`.

## Testing Conventions

### Unit Tests

- Location: `app/src/test/java/com/yage/opencode_client/`.
- Framework: JUnit 4 + MockK + kotlinx-coroutines-test (`runTest`).
- Dispatcher rule: `MainDispatcherRule` (sets `Dispatchers.Main` to `StandardTestDispatcher`).
- Mocking: `mockk(relaxed = true)` for dependencies; `mockkStatic(Log::class)` in `@Before` to silence Android `Log`.
- Always `unmockkAll()` in `@After`.
- Access private state via reflection helper (`updateState(viewModel) { ... }`).
- Access private methods via `getDeclaredMethod(...).apply { isAccessible = true }`.
- Test flow: set up mocks → create VM / set state → call method → `advanceUntilIdle()` → assert state.
- Use `coEvery` / `coVerify` for suspend functions; `every` / `verify` for regular.
- Use `Turbine` for `Flow` testing where applicable.

### Integration Tests

- Location: `app/src/androidTest/java/com/yage/opencode_client/`.
- Require running OpenCode server. Copy `.env.example` → `.env` and fill credentials.
- Use `@get:Rule` with `InstantTaskExecutorRule` and compose test rules.

## Key Files to Read

- `app/build.gradle.kts` — dependencies, build config, version catalog aliases
- `gradle/libs.versions.toml` — all version pins
- `ui/MainViewModel.kt` — AppState definition, core ViewModel
- `ui/MainViewModelSupport.kt` — internal timing constants, SSE parsers, utilities
- `data/model/Message.kt` — core data model with custom serializers
- `data/repository/OpenCodeRepository.kt` — all API interaction, Result wrapping
- `data/api/OpenCodeApi.kt` — Retrofit interface, request/response DTOs
