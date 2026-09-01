# ZetFlix Codebase Report v1.0

**Status:** Technical Summary / Derived from Static Analysis
**Date:** 2026-09-01
**Ref:** commit `be24bcf` (main)

## 1. Executive Summary
The ZetFlix codebase is a mature, Kotlin-centric Android project structured for modularity and high-performance media handling. It leverages a custom plugin architecture to decouple content extraction from the core UI logic. Current development is focused on transitioning from legacy MVVM patterns to modern MVI/Compose architectures.

## 2. Project Architecture

### Modules
*   **`:app`**: The main Android application module containing UI, services, and business logic.
*   **`:library`**: A Kotlin Multiplatform (KMP) compatible module providing the API interfaces and extractors used by plugins.
*   **`:docs`**: A Dokka-powered documentation module.

### Core Patterns
*   **UI Layer**: Primarily XML View-based with ViewBinding, currently being prepared for Jetpack Compose migration.
*   **Architecture**: Loosely MVVM with a push towards MVI to enable cross-platform logic sharing.
*   **Dependency Management**: Centralized via Gradle Version Catalog (`libs.versions.toml`).

## 3. Technology Stack
| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.4.0 (JVM 1.8 Target) |
| **Media Player** | AndroidX Media3 (ExoPlayer) 1.10.1, NextLib (FFmpeg) |
| **Networking** | NiceHttp (OkHttp wrapper), Jsoup/Ksoup (HTML Parsing), Rhino (JS Engine) |
| **Concurrency** | Kotlin Coroutines & Flow |
| **Security** | EncryptedSharedPreferences (Security-Crypto), Biometric Auth API |
| **Dependency Injection** | Manual / ViewModelProvider |

## 4. Key Components Analysis

### Authentication & Sessions
The system utilizes `ZetFlixAuthPrefs` to manage local credentials. It uses `MasterKey` encryption for sensitive data. The authentication flow is gated by `AccountSelectActivity`, ensuring no access to `MainActivity` without a valid local session.

### Media Playback
`CS3IPlayer` serves as the primary player implementation, abstracting ExoPlayer logic. It includes advanced features like:
*   SubRip and Matroska subtitle support.
*   Custom video-skip APIs (Intro/Outro DB).
*   Pip (Picture-in-Picture) and gesture-based controls.

### Plugin Management
`PluginManager` handles the lifecycle of external `.jar` extensions. It includes safety checks (Safe Mode) to prevent boot-loops caused by faulty extensions.

## 5. Identified Maintenance Items
*   **UI Uniformity**: Static analysis identified inconsistencies in color shades (multiple blacks) and padding values (mix of 8dp/15dp/16dp).
*   **Legacy Remnants**: The codebase still contains substantial TV and Emulator-specific layouts and logic that are currently being targeted for removal.
*   **Production Readiness**: Several "TODO" items remain regarding backend URL verification and plugin verification before a stable production release.

## 6. Build & Deployment
*   **CI/CD**: GitHub Actions handle builds, Dokka generation, and pre-release distribution.
*   **Signing**: Configured for `prerelease` and `stable` flavors.
*   **Binary Compatibility**: Tracked in `api/jvm/library.api` to ensure plugin stability across app updates.
