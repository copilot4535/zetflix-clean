# ZetFlix Music Module: Architectural State Audit

**Codebase**: CloudStream 3 (`com.lagradost.cloudstream3`)
**Module**: ZetFlix Music Module
**Date**: 2026-09-04

---

## 1. Component Checklist

### Phase 1: Core Architecture
| Component | Status | Location |
| :--- | :--- | :--- |
| **MusicActivity** | Implemented | `ui/music/MusicActivity.kt` |
| **Navigation Graph** | Implemented | `res/navigation/music_navigation.xml` |
| **InnerTube Repository** | Implemented | `ui/music/MusicRepository.kt` |
| **Home Fragment** | Implemented | `ui/music/MusicHomeFragment.kt` |
| **Search & Suggestions** | Implemented | `ui/music/MusicSearchFragment.kt` |
| **Media3 Service** | Implemented | `services/music/MusicService.kt` |
| **Mini-Player UI** | Implemented | `res/layout/view_music_mini_player.xml` |
| **Offline Support** | Implemented | `services/music/MusicDownloadService.kt` |

### Phase 2: Advanced Features
| Component | Status | Verification Notes |
| :--- | :--- | :--- |
| **Dynamic Palette API** | **Implemented** | Uses `androidx.palette` in `MusicPlayerFragment.kt` for background gradients. |
| **Synchronized Lyrics** | **Implemented** | `LyricsFragment` + `LrcParser` correctly parsing and auto-scrolling synced lyrics. |
| **Home Screen Widget** | **Missing** | No `AppWidgetProvider` or `RemoteViews` for music widgets found. |
| **Audio Processing** | **Missing** | Settings UI exists but lacks System Equalizer intent hooks. |
| **Backup/Restore** | **Partial** | Persistence layer ready (`MusicPersistence`), but lacks dedicated SAF Import/Export. |

---

## 2. File Hierarchy (Relevant Paths)

```text
app/src/main/
├── java/com/lagradost/cloudstream3/
│   ├── ui/music/
│   │   ├── MusicActivity.kt
│   │   ├── MusicHomeFragment.kt
│   │   ├── MusicPlayerFragment.kt
│   │   ├── LyricsFragment.kt
│   │   ├── MusicViewModel.kt
│   │   ├── MusicRepository.kt
│   │   ├── MusicPersistence.kt
│   │   └── LrcParser.kt
│   ├── services/music/
│   │   ├── MusicService.kt
│   │   ├── MusicDownloadService.kt
│   │   └── MusicNotificationManager.kt
├── res/
│   ├── layout/
│   │   ├── activity_music.xml
│   │   ├── fragment_music_player.xml
│   │   ├── fragment_lyrics.xml
│   │   └── view_music_mini_player.xml
│   └── navigation/
│       └── music_navigation.xml
```

---

## 3. Gradle Dependencies & Manifest

### Key Dependencies (`app/build.gradle.kts`)
- `androidx.palette:palette-ktx`: Active for dynamic player theming.
- `androidx.media3:media3-session`: Core of `MusicService`.
- `androidx.media3:media3-exoplayer`: Audio engine.
- `:musicmodules:kotlinYtmusicScraper`: InnerTube engine binding.

### Manifest Registrations (`AndroidManifest.xml`)
- `MusicActivity`: Custom theme `Theme.ZetFlix.Music`.
- `MusicService`: `mediaPlayback` foreground type, `MediaSessionService` intent filter.
- `MusicDownloadService`: `dataSync` foreground type.
- **Missing**: No Widget Receivers or specific Equalizer activity aliases.

---

## 4. Blockers & Observations

> [!IMPORTANT]
> **Missing Component: Home Screen Widget**
> The `AppWidgetProvider` implementation mentioned in Phase 2 is entirely absent from the codebase. There are no layouts for `RemoteViews` dedicated to music controls.

> [!WARNING]
> **Audio Processing Hook**
> While `MusicSettingsFragment` has placeholders, it does not invoke `AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`. This is a requirement for Phase 2 "System Equalizer" integration.

> [!NOTE]
> **Architecture Compliance**
> The project follows a clean MVVM pattern with a central `MusicViewModel` shared via `activityViewModels()`, ensuring smooth state transitions between the mini-player and full-screen fragments.
