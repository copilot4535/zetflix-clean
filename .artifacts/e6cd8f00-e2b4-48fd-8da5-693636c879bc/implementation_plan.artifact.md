# Direct Module Switching Implementation Plan

This plan refactors the module switching behavior to navigate directly between module activities (Music, Movies, Livestream) via the `ZetFlixLoadingActivity`, skipping the `ModuleChooserActivity` as requested. It also ensures that module preferences are updated correctly so that the app remembers the last chosen module.

## User Review Required

> [!IMPORTANT]
> - The floating buttons in `MovieHomeActivity` and `LiveStreamActivity` are currently named `btn_return_home`. I will repurpose these for direct switching.
> - In `MusicActivity`, there is only one floating button (`btn_return_to_movies`). If the user intended to have multiple floating buttons (one for each module), they are not currently in the layout. I will proceed by wiring the existing buttons to their most logical targets (Music -> Movie, Movie -> Music, LiveStream -> Movie).

## Proposed Changes

### Core Logic & Utilities

#### [MODIFY] [ModuleChooserActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/setup/ModuleChooserActivity.kt)
- No changes needed to the logic itself, but I will ensure the constants remain accessible for the other activities.

---

### UI Components (Activities)

#### [MODIFY] [MusicActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicActivity.kt)
- Replace `switchToModuleChooser()` with a more generic `switchToModule(module: String)` function.
- The new function will:
    1. Perform existing teardown (MediaController, Coil cache, coroutines).
    2. Update `SELECTED_MODULE_KEY` and set `REMEMBER_MODULE_CHOICE_KEY` to `true` in `DataStore`.
    3. Launch `ZetFlixLoadingActivity` with the target `module` extra.
    4. Call `finish()`.
- Update `btnReturnToMovies` click listener to call `switchToModule(MODULE_MOVIE)`.

#### [MODIFY] [MovieHomeActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/movie/MovieHomeActivity.kt)
- Implement `switchToModule(module: String)` with appropriate teardown (Coil cache).
- Update `btn_return_home` click listener to call `switchToModule(MODULE_MUSIC)`.

#### [MODIFY] [LiveStreamActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/livestream/LiveStreamActivity.kt)
- Implement `switchToModule(module: String)` with appropriate teardown (Coil cache).
- Update `btn_return_home` click listener to call `switchToModule(MODULE_MOVIE)`.

---

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure no compilation errors.

### Manual Verification
1. **Initial Setup**: Launch app, pick a module, check "Remember my choice".
2. **Switching from Music**: Tap the Movie FAB. Verify it goes to `ZetFlixLoadingActivity` and then to `MovieHomeActivity` without showing the chooser.
3. **Switching from Movie**: Tap the "Home" FAB. Verify it goes to `MusicActivity` (or target) directly.
4. **App Restart**: Close app and restart. Verify it opens the *last switched* module directly.
5. **Back Stack**: Verify that pressing "Back" from the new module does not show the chooser (it should exit or go back within the module).
