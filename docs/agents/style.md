# Style Guide

## Language and Formatting

- Kotlin only. No Java source files.
- 4-space indent, no tabs.
- Target roughly 120 columns; follow existing file wrapping when nearby code is tighter.
- Use trailing commas in multi-line parameter and argument lists.
- Keep a single blank line between method groups; avoid double blank lines.
- Do not add comments unless the user explicitly asks for them.

## Imports and Naming

- Android imports first, then `androidx`/Compose, then third-party libraries, then `com.yage.opencode_client.*`.
- Use wildcard imports for `kotlinx.serialization.json.*` and for `com.yage.opencode_client.data.model.*` when staying within that model package pattern.
- Packages are lowercase, classes/composables/state types are PascalCase, functions and properties are camelCase.
- Constants stay as `private const val` in companion objects with `SCREAMING_SNAKE_CASE`.
- Test names use descriptive backtick strings.

## Data and Repository

- API DTOs use `@Serializable`.
- Use `@SerialName(...)` for snake_case or Go-style field names such as `sessionID` and `parentID`.
- Nullable fields should default to `null`.
- Keep custom serializers as private objects in the same file as the model they support.
- Repository functions return `Result<T>` via `runCatching { ... }`.
- For `Response<Unit>` style endpoints, check `isSuccessful` and throw a descriptive exception on failure.
- `OpenCodeRepository.configure()` is synchronized and rebuilds Retrofit/OkHttp/SSE state when connection settings change.

## State and ViewModels

- `MainViewModel` is the app state owner; `AppState` is the canonical UI model.
- Prefer computed sub-states like `connectionState`, `sessionState`, `chatState`, `speechState`, and `settingsState` over duplicating stored data.
- Complex behavior belongs in top-level `internal` extension functions in the `MainViewModel*Actions.kt` files.
- Action helpers should take explicit dependencies (`scope`, `state`, `repository`, `settingsManager`, callbacks) instead of reaching through `MainViewModel`.
- Async work runs in `viewModelScope.launch {}`.
- Handle repository results with `.onSuccess {}` / `.onFailure {}` and surface user-facing failures through `AppState.error` or the relevant speech/settings error state.

## Compose

- Screen-level composables take `viewModel: MainViewModel` unless they are in the files flow, which uses `FilesViewModel`.
- In custom composables, put `Modifier` first after required non-default parameters.
- Phone layout uses `NavHost` + `NavigationBar`; tablet layout is a 3-column `Row` with sessions, files, and chat.
- Compact top-bar detail affordances should prefer responsive `ModalBottomSheet` patterns on Android and stay usable under larger font/UI scale values.
- Reuse theme helpers such as `ProvideScaledDpDensity`, `uiScaled()`, and `compactTypography()` instead of hard-coding unscaled dimensions.

## Project-Specific Patterns

- Working directory is part of server configuration and affects sessions, files, providers, agents, and status queries; preserve it when adding new repository endpoints.
- Session switching persists the outgoing draft and restores the incoming draft from `SettingsManager`.
- Model and agent selection are session-scoped preferences; do not treat them as purely global settings.
- Streaming message UI is driven by transient maps in `AppState`; avoid duplicating that stream state in composables.
