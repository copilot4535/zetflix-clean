# Implementation Plan - Restoring Global Initialization

The music engine fails to extract URLs because the new startup flow (ModuleChooser -> ZetFlixLoadingActivity -> MusicActivity) bypasses `MainActivity`, skipping critical initialization steps:
1. **Network/SSL Layer**: `Conscrypt` and global `Requests` (NiceHttp) initialization.
2. **Global Components**: `CommonActivity.init()`, `DownloadQueueManager`, and `BackupUtils`.
3. **Core Engine Fallback**: NewPipe initialization and consistent Downloader setup.
4. **Plugin Accessibility**: Aggressive filtering blocking essential decoding/utility plugins.

This plan centralizes these tasks into `CloudStreamApp` (Application level) and `ZetFlixLoadingActivity` (Gateway level) to ensure the engine is fully operational regardless of the entry point.

## Proposed Changes

### Core Application Layer

#### [MODIFY] [CloudStreamApp.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/CloudStreamApp.kt)
- Initialize `app` and `insecureApp` clients in `onCreate()`. This registers `Conscrypt` and sets up the global HTTP cache/DNS early.
- This ensures any background service or activity has immediate access to a configured network client.

### Startup Gateway Layer

#### [MODIFY] [ZetFlixLoadingActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/setup/ZetFlixLoadingActivity.kt)
- Call `CommonActivity.init(this)` to set the global activity instance and initialize core NewPipe state.
- Initialize `DownloadQueueManager`, `BackupUtils`, and `APIRepository` settings.
- These calls must happen before `PluginManager` starts loading plugins to ensure the environment is ready for plugin-provided decoders.

### Plugin Management Layer

#### [MODIFY] [PluginManager.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt)
- Update `isPluginMatch` to always allow plugins with an empty `tvTypes` list.
- This ensures utility plugins (Extractors, JS Decoders, UI extensions) that don't have content category tags are not filtered out, allowing NewPipe to decipher URLs.

### Clean Up

#### [MODIFY] [MainActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt)
- Remove the now-redundant `app.initClient` and `insecureApp.initClient` calls from `onCreate()`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure no regression in build logic.

### Manual Verification
1. **Startup**: Launch the app, enter through `ModuleChooserActivity`.
2. **Music Playback**: Select a song in `MusicActivity`.
3. **Validation**:
   - Check Logcat for "Conscrypt provider registered" (implicit in `initClient`).
   - Confirm song starts playing (proves URL extraction successful).
   - Check other modules (Movies/Live) to ensure they still initialize correctly.
