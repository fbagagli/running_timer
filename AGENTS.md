# KintsugiRun — Agent Guidelines & Architecture Reference

## Overview
KintsugiRun is an Android interval timer app for running workouts built with Jetpack Compose, Kotlin Coroutines/Flow, and Android Foreground Services.

## Tech Stack
- **Language**: Kotlin 2.0+ (JVM 17 target)
- **Framework**: Android SDK 34 (minSdk 24)
- **Build**: Gradle Version Catalog (`gradle/libs.versions.toml`), Kotlin Compose Plugin
- **DI**: Koin (`koin-android`, `koin-androidx-compose`)
- **UI**: Jetpack Compose with Material 3
- **Navigation**: `androidx.navigation.compose` 2.8+ (Type-safe routes with Kotlinx Serialization)
- **Serialization**: `kotlinx.serialization` (JSON format)
- **Testing**: JUnit 4, MockK, Turbine, Google Truth, Koin Test, `kotlinx-coroutines-test`

## Build & Test Commands
- Run unit tests: `./gradlew testDebugUnitTest`
- Assemble debug APK: `./gradlew assembleDebug`
- Lint check: `./gradlew lintDebug`

## Key Architectural Rules & Constraints

### 1. Timing Engine (`TimerManager.kt`, `TimeProvider.kt`)
- Timing **MUST** be calculated against `TimeProvider.elapsedRealtime()` (`SystemClock.elapsedRealtime()`) to avoid drift. Do NOT replace with pure `delay(1000)` tick loops.
- `TimerManager` is an injectable class provided as a singleton in `AppModule.kt`.
- Timer state is exposed via `StateFlow<TimerState>`.

### 2. Background Execution (`TimerService.kt`)
- Android 14 (API 34) requires `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for foreground timer execution.
- Maintain the `WAKE_LOCK` acquire/release lifecycle tied to `TimerState.isPlaying`.

### 3. Audio & Voice Cues (`TtsManager.kt`, `VoicePromptManager.kt`)
- Voice feedback uses `VoicePromptManager` interface implemented by `TtsManager` (Android `TextToSpeech`).
- Handles queued messages if called before `OnInitListener` completes.

### 4. Data Models & Storage (`WorkoutRepository.kt`)
- Workouts are stored as standalone `.json` files in `context.filesDir`.
- Safe file naming sanitizes titles using `Regex("[^a-z0-9]")`.
- The nested workout structure (`Workout` -> `Set` -> `Interval`) is flattened linearly for execution via `Workout.flatten()`.

### 5. Dependency Injection & Navigation Wiring
- Dependency injection is configured in `AppModule.kt` and initialized in `KintsugiApp.kt`.
- Navigation uses type-safe Kotlin Serializable routes defined in `Routes.kt` (`HomeRoute`, `WorkoutReviewRoute`, `WorkoutEditorRoute`, `ActiveWorkoutRoute`).
- ViewModels are injected into composables using `koinViewModel()`.

## Code Style & Testing Expectations
- Always write/update unit tests in `app/src/test/` for new ViewModel or business logic changes.
- Use `Turbine` for testing `StateFlow` and Flow emissions.
- Use `MockK` for mocking dependencies.
