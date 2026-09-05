# Music UI Metadata and Insets Fix

Fix the full-screen music player to ensure metadata (album art, title, artist, album) is visible and the UI correctly handles system bar insets (notch/status bar and navigation bar).

## Proposed Changes

### [Music Module]

#### [MODIFY] [fragment_music_player.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_player.xml)
- Optimize constraints for metadata TextViews to ensure they are not squeezed by the `RecyclerView`.
- Ensure default visibility and alignment.

#### [MODIFY] [custom_music_controls.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/custom_music_controls.xml)
- Add a root ID to the main container to allow applying bottom insets.

#### [MODIFY] [MusicPlayerFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicPlayerFragment.kt)
- Use `UIHelper.fixSystemBarsPadding` to handle status bar/notch insets for the top bar and navigation bar insets for the player controls.
- Refine metadata binding to ensure synchronization between `MediaController` metadata and `ViewModel` song data.
- Ensure the `headerAdapter` (album art) is updated correctly on track transitions.

#### [MODIFY] [MusicActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicActivity.kt)
- Apply insets to the `music_content_layout` or specific sub-views like `music_bottom_nav` and `global_mini_player` to prevent overlap with the navigation bar in the main music tabs.

## Verification Plan

### Automated Tests
- Build check: `./gradlew :app:assembleDebug`

### Manual Verification
- Deploy to a device/emulator with a notch and gesture navigation.
- Open the music player.
- Verify:
  1. Album art is visible at the top.
  2. Title and Artist are visible at the bottom.
  3. Album name appears when available.
  4. The back button and "Now Playing" text are below the status bar/notch.
  5. The playback controls and bottom action buttons (Like, Share, etc.) are above the navigation bar.
