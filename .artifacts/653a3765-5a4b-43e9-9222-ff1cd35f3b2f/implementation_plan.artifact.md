# Music Home Redesign (Phase 3)

Redesigning the Music Home screen to be content-dense and Spotify-like while adhering to ZetFlix branding.

## Proposed Changes

### 1. Palette & Theming
#### [MODIFY] [colors.xml](file:///home/user/zetflix-clean/app/src/main/res/values/colors.xml)
- Update music palette to hybrid dark:
  - `music_background_black`: #000000 (AMOLED)
  - `music_surface_dark`: #121212
  - `music_chip_button`: #242424
  - `music_text_secondary`: #888888

### 2. Music Home Redesign
#### [MODIFY] [MusicViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicViewModel.kt)
- Refactor `loadHomeSections()` to fetch and merge multiple data sources concurrently:
  - `repository.getHomeSections()`
  - `repository.getMoodAndGenres()`
  - Trending data (via `searchSongs("trending music")`)
  - Curated category shelves ("Chill Hits", "Workout Energy", "Romantic Hits", "Top 50 Global") via hardcoded searches.
- Preserve section order as per design blueprint.

#### [MODIFY] [MusicHomeAdapter.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicHomeAdapter.kt)
- Ensure all view types (Artist, Genre, Chart, Normal) are handled cleanly.
- Fix "See All" button styling to be more compact.

#### [MODIFY] [fragment_music_home.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_home.xml)
- Implement ZetFlix-branded header with Search and Settings icons.
- Use the updated background color.

#### [MODIFY] [item_music_home_section.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/item_music_home_section.xml)
- Fix "See All" button size and gravity.

### 3. Search Cleanup
#### [MODIFY] [MusicSearchFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicSearchFragment.kt)
- Stop showing trending content when the search query is empty.
- Show a clean "Search for songs, artists, albums" prompt instead.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure no build regressions.

### Manual Verification
- Verify Home screen loads all sections in the correct order.
- Verify Search screen displays the new empty state.
- Verify new palette is applied across all music module screens and mini-player.
- Verify all click actions (Song, Album, Artist, Genre) still work correctly.
