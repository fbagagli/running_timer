# KintsugiRun — Agent Guidelines & Architecture Reference

## Overview
KintsugiRun is an Android interval timer app for running workouts built with Jetpack Compose, Kotlin Coroutines/Flow, and Android Foreground Services.

## Tech Stack
- **Language**: Kotlin 2.0+ (JVM 17 target)
- **Framework**: Android SDK 34 (minSdk 24)
- **Build**: Gradle Version Catalog (`gradle/libs.versions.toml`), Kotlin Compose Plugin
- **Database**: Android Room 2.6+ with KSP code generation
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

### 2. Background Execution & Interactive Notification (`TimerService.kt`, `TimerNotificationHelper.kt`, `LockScreenHelper.kt`)
- Android 14 (API 34) requires `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for foreground timer execution.
- Maintain the `WAKE_LOCK` acquire/release lifecycle tied to `TimerState.isPlaying`.
- Interactive notifications are constructed via `TimerNotificationHelper` with `setOnlyAlertOnce(true)` to avoid alert noise on periodic second ticks, and `setVisibility(NotificationCompat.VISIBILITY_PUBLIC)` to display workout progress directly on the lock screen.
- Intent actions (`ACTION_PAUSE`, `ACTION_RESUME`, `ACTION_NEXT`, `ACTION_STOP`) are decoupled and dispatched via `TimerServiceActionHandler`.
- Tapping the notification deep-links to `ActiveWorkoutRoute` via `NavigationIntentHelper` in `MainActivity`.
- `MainActivity` coordinates with `LockScreenHelper` to dynamically enable `showWhenLocked` and `turnScreenOn` (via API 27+ methods or legacy window flags) when a workout is active or paused (`isPlaying || totalElapsedSeconds > 0`), ensuring users can view and control their workout over the lock screen.
- `ActiveWorkoutScreen` utilizes Compose `DisposableEffect` with `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` to prevent the display from turning off while viewing the active workout session.

### 3. Audio & Voice Cues (`TtsManager.kt`, `VoicePromptManager.kt`)
- Voice feedback uses `VoicePromptManager` interface implemented by `TtsManager` (Android `TextToSpeech`).
- Handles queued messages if called before `OnInitListener` completes.

### 4. Data Models & Storage (`AppDatabase.kt`, `WorkoutDao.kt`, `WorkoutSessionDao.kt`, `WorkoutRepository.kt`)
- Workouts and completed sessions are stored in a local **Room SQLite Database** (`kintsugi_run.db`, version 2).
- `Workout` is a clean Room `@Entity(tableName = "workouts")` with auto-generated `Long` IDs.
- `WorkoutSession` is a Room `@Entity(tableName = "workout_sessions")` capturing completed and interrupted sessions with an immutable `@Embedded(prefix = "workout_")` snapshot of the executed `Workout`.
- `WorkoutDao.getAllWorkouts()` and `WorkoutSessionDao.getLatestSessionForWorkout()` emit reactive Flows.
- The flat workout structure (`Workout` containing warmup, run, walk, repeats, cooldown) is expanded linearly via `Workout.flatten()`.
- Session persistence is centralized in `TimerManager.kt` on natural completion or early stop (`totalElapsedSeconds >= 30`).
- Latest session for a workout is displayed in `WorkoutReviewScreen.kt`.

### 5. Workout Sharing & Import (`WorkoutQrHelper.kt`, `docs/index.html`)
- Workout transfer uses Deep Link QR URIs (`kintsugirun://workout?name=...&w=...&r=...&k=...&n=...&c=...`).
- Scanning is handled seamlessly via **Google Code Scanner** (no camera permission needed).
- Workout sharing renders a QR code bitmap via ZXing.
- Static companion web tool is located in `docs/index.html` (hosted on GitHub Pages).

### 6. Universal Backup & Restore (`BackupHelper.kt`, `HomeScreen.kt`)
- Full database backup to a single JSON file (`KintsugiBackup`) containing workouts and execution sessions with snapshots.
- Export and import via Android Storage Access Framework (SAF) file picker contracts (`CreateDocument` and `OpenDocument`) accessible from `HomeScreen` TopBar menu.

### 7. Dependency Injection & Navigation Wiring
- Dependency injection is configured in `AppModule.kt` and initialized in `KintsugiApp.kt`.
- Navigation uses type-safe Kotlin Serializable routes defined in `Routes.kt` (`HomeRoute`, `WorkoutReviewRoute`, `WorkoutEditorRoute`, `ActiveWorkoutRoute`).
- ViewModels are injected into composables using `koinViewModel()`.

## Code Style & Testing Expectations
- Always write/update unit tests in `app/src/test/` for new ViewModel or business logic changes.
- Use `Turbine` for testing `StateFlow` and Flow emissions.
- Use `MockK` for mocking dependencies.
