# Implementation Plan — Fix Missing Bottom Navigation Bar in Music Section

The bottom navigation bar is missing on the Music Overview (Home) screen and other related screens. This is caused by a race condition during the initial UI transition and an overly restrictive visibility whitelist in `MusicActivity`.

## Proposed Changes

### [Music Activity](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicActivity.kt)

#### [MODIFY] [MusicActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicActivity.kt)

- **Expand `showNav` destinations**: Update the visibility logic to show the bottom navigation bar on all primary and secondary music screens (Detail, Artist, Genre, Charts, History, etc.), hiding it only for the full-screen player and lyrics screens.
- **Fix Race Condition**: Improve `showMainContent()` to more reliably determine the initial navigation state.
- **Robust Visibility Toggle**: Refine `toggleBottomNav()` to ensure it correctly synchronizes state even if previous animations were interrupted or if the view was in an inconsistent state.
- **Immersive State Handling**: Ensure `setupInsets()` correctly identifies immersive vs. non-immersive destinations to apply padding accurately.

## Verification Plan

### Automated Tests
- Since this is primarily a UI/Layout issue, manual verification on the device is preferred.

### Manual Verification
1. **Initial Boot**: Open the Music application and verify the bottom navigation bar appears after the preloader finishes.
2. **Tab Switching**: Navigate between "Home", "Search", and "Library" tabs; verify the navigation bar remains visible and correctly highlights the active tab.
3. **Deep Navigation**: Navigate to an Album detail, Artist profile, or Genre page; verify the bottom navigation bar remains visible (newly added support).
4. **Immersive Screens**: Open the Music Player and Lyrics screens; verify the bottom navigation bar is hidden and the UI is immersive.
5. **Back Navigation**: Return from the player or a detail screen to the Home screen; verify the navigation bar reappears correctly.
6. **Edge-to-Edge**: Verify the navigation bar does not overlap the Android system navigation bar (3-button or gesture).
