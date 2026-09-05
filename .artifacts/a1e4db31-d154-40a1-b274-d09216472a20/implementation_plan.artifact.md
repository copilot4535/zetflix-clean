# Music Combined Lyrics & Queue Bottom Sheet

Implement a Spotify-style bottom sheet that combines Lyrics and Queue (Up Next) into a single, tabbed interface reachable from the Music Player.

## User Review Required

- The bottom sheet will replace the current navigation to separate `LyricsFragment` and `MusicQueueFragment`.
- The existing fragments will be reused as child fragments within the bottom sheet's `ViewPager2`.

## Proposed Changes

### [Layouts]

#### [NEW] [layout_music_combined_panel.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/layout_music_combined_panel.xml)
- Contains a drag handle, a `TabLayout` with "Lyrics" and "Up Next" tabs, and a `ViewPager2`.
- Background set to `@color/music_background_black`.

#### [MODIFY] [fragment_lyrics.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_lyrics.xml)
- Wrap the toolbar/header in a container that can be hidden when hosted in the bottom sheet.

#### [MODIFY] [fragment_music_queue.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_queue.xml)
- Make the toolbar hideable when hosted in the bottom sheet.

### [UI Components]

#### [NEW] [MusicCombinedBottomSheetFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicCombinedBottomSheetFragment.kt)
- Implements `BottomSheetDialogFragment`.
- Hosts `LyricsFragment` and `MusicQueueFragment` in a `ViewPager2`.
- Handles initial tab selection based on which button was clicked in `MusicPlayerFragment`.

#### [MODIFY] [MusicPlayerFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicPlayerFragment.kt)
- Change `music_player_lyrics` and `music_player_queue` click listeners to open `MusicCombinedBottomSheetFragment`.
- Pass the appropriate starting tab index.

#### [MODIFY] [LyricsFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/LyricsFragment.kt)
- Add logic to hide its own header if it's being hosted by the new bottom sheet.

#### [MODIFY] [MusicQueueFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicQueueFragment.kt)
- Add logic to hide its toolbar if it's being hosted by the new bottom sheet.

### [Navigation]

#### [MODIFY] [music_navigation.xml](file:///home/user/zetflix-clean/app/src/main/res/navigation/music_navigation.xml)
- Add the new dialog destination for the combined bottom sheet.
- Optional: Remove or keep old destinations (I'll keep them but bypass them to avoid breaking other possible deep links, but the prompt says "Remove or bypass").

## Verification Plan

### Manual Verification
- Launch Music Player.
- Tap Lyrics button -> Bottom sheet opens at half-height with Lyrics tab.
- Tap Queue button -> Bottom sheet opens at half-height with Up Next tab.
- Swipe between tabs.
- Expand to full height by dragging up.
- Swipe down to dismiss.
- Verify lyrics sync still works.
- Verify queue interaction still works.
