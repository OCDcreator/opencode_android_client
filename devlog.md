# Development Log — opencode_android_client

> Newest entries are at the top. Older entries are at the bottom.

---

## [Unreleased] — 2026-04-02

### feat: Working Directory Support
- All API endpoints (`OpenCodeApi`) now accept an optional `directory` query parameter
- `OpenCodeRepository` added `workingDirectory` field, `effectiveDirectory()` helper, and every method passes directory to API
- `AppState` / `SettingsState` / `ConnectionFormSettings` gained `workingDirectory` field
- `MainViewModel.configureServer()` persists working directory, remembers history, triggers data reload
- `SettingsManager` stores `workingDirectory`, tracks recent directories (max 8) with dedup

### feat: Server Directory Picker UI
- New `ServerDirectoryPickerDialog` composable — browse server directories in real-time
- Supports navigating into subdirectories, going up to parent, selecting current, or using server default
- Recent working directories shown as `AssistChip` list in settings
- `FilesScreen` auto-refreshes when working directory changes

### feat: Chinese (zh) Localization
- New `values-zh/strings.xml` with full Simplified Chinese translations (163 strings)
- Covers all UI sections: navigation, chat, permissions, settings, appearance, speech, about, sessions, files, error messages

### refactor: i18n String Externalization
- Replaced every hardcoded English string across all UI composables with `stringResource()` calls
- `Screen` sealed class changed from `val title: String` to `@StringRes val titleRes: Int`
- `strings.xml` expanded from ~14 strings to 163 strings
- Affected files: `MainActivity`, `ChatInputBar`, `ChatMessageContent`, `ChatScreen`, `ChatTopBar`, `QuestionCardView`, `FileBrowserPane`, `FilePreviewPane`, `FilesScreen`, `SessionList`, `SettingsScreen`, `SettingsSections`

### docs: AGENTS.md Comprehensive Rewrite
- Rewrote from short Chinese doc (~25 lines) to full English reference (~169 lines)
- Added: build commands table, project structure tree, tech stack, code style conventions (formatting, imports, naming, data models, state management, ViewModel/repo/DI/Compose patterns, error handling, testing conventions, key files)

---

## v0.1.20260322 (v4) — 2026-03-22

### fix: Restore glm-5-turbo model preset
- PR #19 — restored GLM-5-Turbo model preset after regression

### chore: Update GLM model preset
- PR #18 — switched GLM model preset from GLM-5-Turbo to GLM-5.1

---

## v0.1.20260322 (v3) — 2026-03-22

### fix: Markdown image absolute path
- PR #16 — resolve preview markdown images against session directory
- Fixed Unix absolute markdown image paths

### chore: Bump version
- Version bumped to 0.1.20260322 (v4)

---

## Sprint A/B/C Code Review — 2026-03-21

### refactor: Address code review medium+ issues
- PR #15 — Sprint A/B/C medium+ priority code review fixes

### fix: Address code review critical and high issues
- PR #14 — critical and high priority fixes from code review

---

## Markdown Image Rendering — 2026-03-20

### feat: Render embedded images in markdown content
- PR #13 — support rendering inline images within markdown chat messages

---

## Model Preset & Agent Updates — 2026-03-19

### chore: Model preset updates
- Switched GLM-5 model preset to glm-5-turbo
- Default model selection changed to GPT-5.4
- Default agent switched to Opus 4.6, fixed Gemini model IDs

---

## File Preview Back Navigation — 2026-03-18

### feat: Navigate back to Chat from file preview
- PR #11 — when closing file preview opened from Chat, navigate back to Chat tab

---

## Fork Session — 2026-03-15

### feat: Fork session from assistant message
- Added fork context menu to assistant message model label
- `launchForkSession` action and `forkSession` ViewModel method
- `forkSession` API endpoint and repository method

### test: ForkSessionTest
- Added unit tests for fork session functionality

### docs: PRD/RFC updates
- Fixed PRD/RFC discrepancies — updated API samples, data models, feature status
- Version bumped to 0.1.20260314; updated PRD/RFC with fork session

---

## Phase 5b — 2026-03-13

### feat: Message pagination, model/agent capsules, per-message model badge
- PR #9 — message pagination fix, model/agent capsule UI, per-message model display badge

---

## Per-Session Draft & Model Memory — 2026-03-12

### feat: Per-session draft persistence and model/agent memory
- Draft text, selected model, and selected agent are persisted per session
- `SettingsManager` stores per-session state keys

### test: Per-session draft/model/agent tests
- Added unit tests for per-session draft, model, and agent persistence

---

## ChatTopBar Redesign — 2026-03-11

### feat: Redesign ChatTopBar layout to match iOS
- Large title style with left/right button split
- Rename session dialog integrated into top bar

---

*Earlier history available in git log.*
