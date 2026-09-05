# Implementation Plan - Music Module System Bar Insets Fix

Fix system bar interference in the music module where content overlaps with the status bar and navigation bar.

## Proposed Changes

### [Music Module]

#### [MODIFY] [MusicActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicActivity.kt)
- Add `ViewCompat.setOnApplyWindowInsetsListener` to `music_content_layout`.
- Apply `insets.top` as top padding to ensure content starts below the status bar.
- Apply `insets.bottom` as bottom padding to ensure floating navigation and mini-player stay above the system navigation bar.

#### [MODIFY] [MusicPlayerFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicPlayerFragment.kt)
- Refine `setOnApplyWindowInsetsListener` to ensure `music_player_top_bar` and `music_player_view` (controls) respect system bars correctly.

#### [MODIFY] [MusicHomeFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicHomeFragment.kt)
- Add inset listener to apply top padding to the main content area.

#### [MODIFY] [MusicSearchFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicSearchFragment.kt)
- Add inset listener to apply top padding to `music_search_appbar`.

#### [MODIFY] [MusicLibraryFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicLibraryFragment.kt)
- Add inset listener to apply top padding to the header.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure no build regressions.

### Manual Verification
- Deploy to device/emulator.
- Check all music tabs (Home, Search, Library) to ensure headers don't overlap with the status bar.
- Check the full player to ensure the top bar and bottom controls respect system bars.
- Verify the floating bottom navigation and mini-player are correctly positioned above the system navigation bar without excessive gaps.
