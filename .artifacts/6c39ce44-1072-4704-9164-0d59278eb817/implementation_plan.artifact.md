# Music Player UI Rebuild Implementation Plan

The objective is to fix the music player's black screen and missing UI components by simplifying the layout and ensuring reliable metadata binding.

## User Review Required

> [!IMPORTANT]
> The `RecyclerView` usage in the main player fragment is being removed in favor of a direct, static layout. This simplifies debugging and guarantees visibility of essential elements. If "Up Next" related tracks are still needed, they will be moved to the combined bottom sheet or a separate section if requested later.

## Proposed Changes

### UI Layouts

#### [MODIFY] [fragment_music_player.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_player.xml)
- Replace `RecyclerView` with a direct `ImageView` for album art.
- Restructure into a clean vertical flow: Top Bar -> Album Art -> Metadata -> Controls.
- Add proper padding for system insets.

#### [MODIFY] [custom_music_controls.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/custom_music_controls.xml)
- Ensure all control button IDs are accessible and correctly labeled.

### Fragments

#### [MODIFY] [MusicPlayerFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicPlayerFragment.kt)
- Remove `PlayerHeaderAdapter` and `ConcatAdapter`.
- Bind `music_player_album_art` directly using Coil.
- Update `updateMetadata` to handle fallbacks correctly.
- Ensure the back button works and is visible.
- Apply dynamic background colors using `MusicColorHelper`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure the project builds correctly.

### Manual Verification
- Open the music player.
- Verify album art is visible and centered.
- Verify Song Title, Artist, and Album are displayed.
- Verify Back button returns to the previous screen.
- Verify Seek bar and controls work.
- Verify background gradient updates based on album art.
