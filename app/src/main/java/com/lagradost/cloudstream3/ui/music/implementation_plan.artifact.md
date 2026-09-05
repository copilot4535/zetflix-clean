# Fix Music Player Metadata and UI

This plan addresses the "dead" UI state in the full-screen music player by fixing metadata fallback logic, binding album art from Media3 controller metadata, and refining the layout to prevent clipping and overlapping.

## User Review Required

> [!IMPORTANT]
> I will be changing how metadata is prioritized. The `MediaController` (playback service) will be the primary source, but I will implement strict fallback to the `ViewModel` (UI state) if the service provides blank strings or nulls.

> [!WARNING]
> The layout structure will be adjusted to ensure controls, metadata, and album art have dedicated, non-overlapping vertical space. This might slightly shift the position of elements on smaller screens.

## Proposed Changes

### UI & Layout

#### [MODIFY] [fragment_music_player.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_player.xml)
- Adjust `music_player_main_recycler` constraints to maximize space for album art.
- Ensure `music_player_metadata_container` is clearly pinned above the controls.
- Set a fixed or minimum height for the controls container if necessary to prevent them from squashing the album art.

#### [MODIFY] [custom_music_controls.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/custom_music_controls.xml)
- Verify `exo_progress` and buttons have enough touch target area without taking excessive vertical space.

### Logic & Binding

#### [MODIFY] [MusicPlayerFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicPlayerFragment.kt)
- **Metadata Fallback**: Update `updateMetadata()` to handle `isNullOrBlank()` for title, artist, and album.
- **Album Art Binding**:
    - Extend `PlayerHeaderAdapter` to handle updating the album art via a URL string.
    - In `onMediaMetadataChanged`, extract `artworkUri` and pass it to the header adapter.
    - Implement fallback to `viewModel.currentPlayingSong.thumbnailUrl` if `artworkUri` is missing.
- **Dynamic Theming**: Ensure `applyDynamicTheming` is triggered correctly from the header adapter's image loading listener.
- **Z-Order**: Ensure `PlayerView` (controls) doesn't have an opaque background that hides the metadata container.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure no build regressions.

### Manual Verification
1. Open the full-screen player.
2. Verify album art is visible and updates on track skip.
3. Verify title and artist are correctly displayed (no blank text).
4. Verify the background gradient changes based on the album art.
5. Verify controls (Play/Pause, Seek, etc.) are responsive and don't overlap the song title.
