# Testing Guide

## Commands

| Scope | Command |
|------|---------|
| All unit tests | `./gradlew testDebugUnitTest` |
| Single class | `./gradlew testDebugUnitTest --tests "com.yage.opencode_client.MainViewModelTest"` |
| Single method | `./gradlew testDebugUnitTest --tests "com.yage.opencode_client.MainViewModelTest.sendMessage success clears input and uses selected preset model"` |
| Coverage | `./gradlew koverHtmlReport` |
| Instrumented/integration | `./gradlew connectedDebugAndroidTest` |

## Unit Test Conventions

- Unit tests live under `app/src/test/java/com/yage/opencode_client/`.
- Use JUnit 4, MockK, and `kotlinx-coroutines-test`.
- `MainDispatcherRule` should own `Dispatchers.Main`.
- Silence Android logging with `mockkStatic(Log::class)` in `@Before`, and always call `unmockkAll()` in `@After`.
- Reflection helpers are acceptable for private state/method access in `MainViewModel` tests when needed.
- Typical flow: set up mocks, create the view model or seed state, trigger the action, `advanceUntilIdle()`, then assert.
- Use `coEvery` / `coVerify` for suspend functions and `every` / `verify` for non-suspend APIs.
- Use Turbine when asserting flow emissions instead of manual collection loops.

## Instrumented and Integration Tests

- Instrumented tests live under `app/src/androidTest/java/com/yage/opencode_client/`.
- `connectedDebugAndroidTest` requires a running OpenCode server plus `.env` values for `OPENCODE_SERVER_URL`, `OPENCODE_USERNAME`, `OPENCODE_PASSWORD`, `AI_BUILDER_BASE_URL`, and `AI_BUILDER_TOKEN`.
- Compose UI tests should use the standard compose test rule; keep `InstantTaskExecutorRule` where architecture components need it.

## Verification Strategy

- For `MainViewModel`, repository, or parsing changes, add or update a focused unit test first.
- For files UI, settings UI, scaling, or permission/question UX, prefer the narrowest useful test and only widen to instrumented coverage when behavior is Compose- or Android-specific.
- After touching shared synchronization paths such as SSE, busy polling, or message refresh timing, run the relevant `MainViewModelTest` subset at minimum.
