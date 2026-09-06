# ZetMusic Mini Player Premium Improvement Plan

Improve the existing Mini Player into a polished, premium music player with better visual design, smoother interactions, and dynamic theming.

## Proposed Changes

### UI & Layout

#### [MODIFY] [view_music_mini_player.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/view_music_mini_player.xml)
- Increase `cardCornerRadius` to 16dp for a more modern look.
- Reduce `cardElevation` to 4dp.
- Refine layout margins to better align with the floating bottom navigation.
- Add a loading indicator (CircularProgressIndicator) overlaid on the play/pause button.
- Improve the progress bar design (slightly thicker, better placement).
- Update typography styles for title and artist.

### Logic & Interaction

#### [MODIFY] [MusicActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicActivity.kt)
- Update `applyMiniPlayerTheming` to:
    - Apply dynamic `vibrantColor` to the progress bar.
    - Apply dynamic colors to the play/pause button if it makes sense (or keep it white for contrast).
    - Ensure contrast for text using `MusicColorHelper.ensureContrast`.
- Improve `updateMiniPlayerVisibility`:
    - Handle `Player.STATE_BUFFERING` and `Player.STATE_READY`.
    - Add smoother transition animations.
- Implement Buffering State:
    - Show/hide the loading spinner on the play/pause button when buffering.
- Add Swipe Gestures:
    - Implement swipe left/right to skip tracks on the mini player.

#### [MODIFY] [MusicColorHelper.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicColorHelper.kt)
- Add a helper to set the progress bar color dynamically.

## Verification Plan

### Automated Tests
- `./gradlew :app:assembleDebug`

### Manual Verification
- Verify Mini Player appears correctly on all screens except immersive ones.
- Verify playback controls (Play/Pause, Skip via swipe).
- Verify dynamic colors applied to background and progress bar.
- Verify smooth transition to Full Player.
- Verify loading spinner shows during buffering.
- Verify correct positioning with floating bottom navigation and system insets.
