# 🏃 KintsugiRun

*Minimalist, mindful interval running timer & GPS tracker for Android.*

[![CI/CD & Release](https://github.com/fbagagli/running_timer/actions/workflows/ci.yml/badge.svg)](https://github.com/fbagagli/running_timer/actions/workflows/ci.yml)
[![GitHub Pages](https://img.shields.io/badge/Web%20Companion-GitHub%20Pages-f97316)](https://fbagagli.github.io/running_timer/)
[![Latest APK](https://img.shields.io/badge/Download-Latest%20APK-22c55e)](https://github.com/fbagagli/running_timer/releases/latest/download/kintsugirun-debug.apk)

---

## ⚡ Quick Download & Web Companion

Get the latest build or create and share interval workouts directly in your browser:

- 📥 **[Download Latest APK (GitHub Releases)](https://github.com/fbagagli/running_timer/releases/latest/download/kintsugirun-debug.apk)** — Direct download of the latest debug build.
- 🌐 **[Download from Web Companion (GitHub Pages)](https://fbagagli.github.io/running_timer/kintsugirun.apk)** — Direct APK download mirror hosted alongside our documentation.
- 🛠️ **[Web Companion Tool (Create & Share Workouts via QR)](https://fbagagli.github.io/running_timer/)** — Create workouts in your browser and import them instantly into the app via QR code.

---

## ✨ Features Overview

- ⏱️ **Zero-Drift Interval Timer**: Built on Android hardware monotonic clock (`SystemClock.elapsedRealtime()`) to prevent cumulative time drift, ensuring absolute precision across all warmup, run, walk, and cooldown intervals.
- 🔒 **Background & Lock Screen Controls**: Powered by an Android Foreground Service with rich interactive notification actions (`Play`, `Pause`, `Skip`, `Stop`). Full lock-screen visibility and controls (`showWhenLocked` / `turnScreenOn`) allow you to track and manage workouts without unlocking your device.
- 🗣️ **Mindful Voice Feedback (Smart Audio Cues)**: Essential audio cues designed for runners who prefer running without music or looking at a screen:
  - Phase start cues with repetition counters (*"Start running, 2 of 5"*, *"Start walking, 2 of 5"*).
  - Intermediate pace check-ins (*"Halfway, pace M:SS"* for intervals $> 120$ s).
  - Transition warnings (*"One minute left"* for intervals $\ge 180$ s).
- 📍 **GPS Tracking & OpenStreetMap Visualization**:
  - Live distance and current interval pace calculation with GPS accuracy filtering.
  - Granular post-workout breakdown: distinct Run and Walk telemetry (distances, split paces, and durations).
  - Interactive route preview using embedded Leaflet / OpenStreetMap with two-tone trace (red for Run phases, green for Walk phases).
  - One-tap GPX 1.1 file export for Strava, Garmin Connect, and Runkeeper.
- 💾 **Local Persistence & Continuous Auto-Backup**:
  - Offline-first Room SQLite database (v3) storing workouts and immutable execution snapshots.
  - Full backup & restore to a single JSON file via Android Storage Access Framework (SAF).
  - Continuous auto-backup to a user-selected external folder (`kintsugirun_auto_backup.json`), updated on every workout modification or completed session.
- 📲 **Instant QR Code Sharing**:
  - Share workouts via deep-link QR codes (`kintsugirun://workout?...`).
  - Seamless scanning via Google Code Scanner without requiring camera permissions.

---

## 🛠️ Architecture & Tech Stack

- **Language & Runtime**: Kotlin 2.0+ (JVM 17 target)
- **UI Framework**: Jetpack Compose with Material 3 design system
- **Dependency Injection**: Koin (`koin-android`, `koin-androidx-compose`)
- **Database & Persistence**: Android Room 2.6+ with KSP code generation
- **Navigation**: Type-safe routes via `androidx.navigation.compose` 2.8+ with `kotlinx.serialization`
- **Location & Maps**: Android native `LocationManager` + Leaflet / OpenStreetMap WebView component
- **Audio & TTS**: Android `TextToSpeech` via decoupled `VoicePromptManager`
- **Target Platform**: Android SDK 34 (minSdk 24)

---

## 🚀 Build & Test Commands

### Prerequisites
- JDK 17
- Android SDK 34

### Commands
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Run lint checks
./gradlew lintDebug
```

---

## 📄 License
This project is open-source.
