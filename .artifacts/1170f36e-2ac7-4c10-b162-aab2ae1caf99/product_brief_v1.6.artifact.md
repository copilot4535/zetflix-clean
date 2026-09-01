# ZetFlix Product Brief v1.6

**Status:** Draft / Derived from Codebase Analysis
**Date:** 2026-09-01
**Version:** 1.6

## 1. Product Vision
ZetFlix is a high-performance, extension-based media center for Android that prioritizes user freedom, privacy, and a premium viewing experience. It serves as a unified interface for streaming content from a variety of user-installed sources, removing the need for multiple disparate apps.

## 2. Core Values
*   **Complete Freedom**: An open plugin architecture allows users to define their own content sources.
*   **Privacy First**: No built-in tracking or analytics. All authentication is local and secure.
*   **Ad-Free Experience**: A clean, uninterrupted UI for content consumption.
*   **Cross-Platform Aspirations**: Architected with Kotlin Multiplatform (KMP) compatibility in mind for future expansion.

## 3. Target Audience
*   Android mobile power users seeking a centralized media hub.
*   Users who value privacy and data sovereignty.
*   The "cutting-edge" community (via the pre-release channel).

## 4. Key Features
*   **Modular Extension System**: Seamless integration of third-party plugins via the ZetFlix Library API.
*   **Cinematic Playback**: Powered by Media3 (ExoPlayer) and NextLib (FFmpeg), supporting 4K, HDR, and custom subtitle rendering.
*   **Secure Session Management**: Local-only accounts protected by `EncryptedSharedPreferences` and Biometric/PIN authentication.
*   **Discovery Engine**: Advanced search with provider-specific filtering and "Continue Watching" synchronization.
*   **Offline Support**: Robust download manager with parallel and concurrent connection controls.

## 5. Branding & Identity
The v1.6 release solidifies the **ZetFlix** identity:
*   **Palette**: Premium "Netflix Dark" style using `#000000` (Background) and `#E50914` (ZetFlix Accent).
*   **Logo**: A stylized "Z" with a play button cutout, featuring linear gradients and subtle shadows for depth.
*   **UX**: Cinematic splash transitions and high-end animations to convey a professional, polished feel.

## 6. Roadmap / Version Focus
Version 1.6 specifically focuses on:
1.  **Security Overhaul**: Implementing a dedicated registration/login flow and secure credential storage.
2.  **Branding Refresh**: Full integration of the new logo and cinematic splash screen.
3.  **Mobile Optimization**: Beginning the removal of TV/Emulator remnants to provide a leaner mobile-first experience.
