# Implementation Plan - Revert Startup Chooser & Restore Navigation

Restore the original startup flow (AccountSelect -> Loading -> MainActivity) and remove the Module Chooser logic, while preserving Music module enhancements.

## User Review Required

> [!IMPORTANT]
> I will be merging changes in `AndroidManifest.xml` and `MainActivity.kt` rather than a blind revert to ensure the Music module and other non-chooser improvements (like the new avatar in the rail) are preserved.

## Proposed Changes

### [Core App]

#### [MODIFY] [PluginManager.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt)
- Revert to `main` branch state.
- This removes the `PluginLoadingState` Flow and tiered loading logic which were introduced for the chooser.

#### [MODIFY] [ZetFlixLoadingActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/setup/ZetFlixLoadingActivity.kt)
- Revert to `main` branch state.
- Restores `navigateToMain()` as the sole exit point.
- Removes `moduleFilter` usage.

#### [MODIFY] [AccountSelectActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/account/AccountSelectActivity.kt)
- Revert to `main` branch state.
- Navigates directly to `ZetFlixLoadingActivity` without checking for module preferences.

#### [MODIFY] [MainActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt)
- Remove `moduleFilter` logic in `onCreate`.
- Remove references to `ModuleChooserActivity`.
- Restore original `updateNavBar` and `onNavDestinationSelected` logic.
- **Keep**: `launchMusic()`, `loadRailAvatar()`, and Music-related UI updates (like mini-player visibility) if they are not chooser-dependent.

#### [MODIFY] [AndroidManifest.xml](file:///home/user/zetflix-clean/app/src/main/AndroidManifest.xml)
- Remove `ModuleChooserActivity`, `MovieHomeActivity`, and `LiveStreamActivity` declarations.
- Restore `AccountSelectActivity` as the launcher (already is, but ensure intent filters match `main`).
- **Keep**: `MusicActivity`, `MusicService`, and `ZetFlixDownloadService` declarations.

#### [MODIFY] [activity_main.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/activity_main.xml)
- Revert to `main` branch state (uses `primaryGrayBackground`).

#### [MODIFY] [bottom_nav_menu.xml](file:///home/user/zetflix-clean/app/src/main/res/menu/bottom_nav_menu.xml)
- Revert to `main` branch state.

#### [MODIFY] [rail_header.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/rail_header.xml)
- Revert to `main` branch state.

### [Module Chooser Clean-up]

#### [DELETE] [ModuleChooserActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/setup/ModuleChooserActivity.kt)
#### [DELETE] [activity_module_chooser.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/activity_module_chooser.xml)
#### [DELETE] [MovieHomeActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/movie/MovieHomeActivity.kt)
#### [DELETE] [LiveStreamActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/livestream/LiveStreamActivity.kt)

### [Music Module Clean-up]

#### [MODIFY] [MusicActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicActivity.kt)
- Remove imports and references to `ModuleChooserActivity`.
- Replace `switchToModule(ModuleChooserActivity.MODULE_MOVIE)` with a simple `finish()` or navigation back to `MainActivity`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure no compile errors after removing chooser references.

### Manual Verification
- Launch the app: Verify it goes `AccountSelectActivity` -> `ZetFlixLoadingActivity` -> `MainActivity`.
- Verify `MainActivity` shows the standard Movie home.
- Verify Music module is still accessible and functional.
