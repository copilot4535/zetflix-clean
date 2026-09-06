# Spotify-inspired Podcast Card Redesign

This plan outlines the steps to redesign the podcast cards on the Home Page to be more colorful, information-rich, and Spotify-inspired, while maintaining the overall dark theme of the application.

## User Review Required

> [!IMPORTANT]
> - The redesign is **strictly limited to the Home Page Podcasts section**. Other screens using podcast cards (like the horizontal lists) will remain unchanged.
> - We will derive dynamic colors from the artwork using the existing `MusicColorHelper` and cache them to ensure smooth scrolling.
> - The `MusicViewModel` will be updated to expose the current playback state (`isPlaying`) so the cards can update their Play/Pause icons in real-time.

## Proposed Changes

### 1. Data & State Management

#### [MODIFY] [MusicViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicViewModel.kt)
- Add `isPlaying` LiveData to track the global playback state.
- Add `updatePlaybackState(Boolean)` function to be called from the Activity.

#### [MODIFY] [MusicActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicActivity.kt)
- Update `viewModel.isPlaying` within the `Player.Listener` (e.g., in `onIsPlayingChanged` and `onMediaItemTransition`).

---

### 2. UI Components & Layouts

#### [MODIFY] [MusicColorHelper.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicColorHelper.kt)
- Define `HomePodcastCardPalette` data class.
- Implement `generateHomePodcastCardPalette(MusicPalette)` to create a normalized, gradient-ready palette.
- Ensure contrast-aware foreground color selection (near-white for dark cards, near-black for light cards).

#### [MODIFY] [item_music_podcast_card_vertical.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/item_music_podcast_card_vertical.xml)
- Redesign the card layout:
    - Increase card corner radius to 24dp.
    - Increase artwork size to 150dp.
    - Implement a proper text hierarchy (Title: 2 lines, Show: 1 line, Description: 2 lines).
    - Align Action Row (Play/Add) at the bottom right of the content area.
    - Ensure no elements overlap.

---

### 3. Adapters & Fragments

#### [MODIFY] [MusicHomeAdapter.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicHomeAdapter.kt)
- Update `MusicHomeItemAdapter` to accept `currentPlayingMediaId` and `isPlaying` state.
- In `PodcastVerticalViewHolder`:
    - Fetch and cache artwork-derived palette.
    - Apply tonal gradient background.
    - Handle description visibility (hide if no "•" separator or empty description).
    - Update Play/Pause button based on global playback state.
    - Implement subtle color transitions when artwork loads.

#### [MODIFY] [MusicHomeFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicHomeFragment.kt)
- Observe `viewModel.currentPlayingSong` and `viewModel.isPlaying`.
- Pass these values to the `homeAdapter` to trigger UI updates on the podcast cards.

## Verification Plan

### Automated Tests
- N/A (UI-driven redesign).

### Manual Verification
1. **Visual Inspection**: Open Home Page -> Podcasts. Verify cards have rounded corners (24dp), large artwork, and dynamic gradient backgrounds.
2. **Dynamic Colors**: Check cards with different artwork (colorful, dark, light, grayscale). Verify background and text contrast.
3. **Information Richness**: Verify Title (2 lines max), Show Name (1 line max), and Description (2 lines max) are correctly displayed and truncated.
4. **Metadata Fallback**: Ensure the description area is hidden if no description is provided in the metadata.
5. **Playback State**: Play a podcast episode from the card. Verify the Play button changes to Pause and the card updates when playback starts/stops.
6. **Performance**: Scroll through the Podcasts section. Verify no lag or "jank" occurs during palette extraction/loading.
7. **Scope Check**: Verify that `StandardPodcastCard` (horizontal lists) and other screens like `PodcastDetail` or `FullPlayer` remain unchanged.
8. **Overlap Check**: Test with extremely long titles and show names to ensure no overlap with the 'More' or 'Play' buttons.
