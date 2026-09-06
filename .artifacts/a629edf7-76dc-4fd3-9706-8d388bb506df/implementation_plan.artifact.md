# Podcast Episode Card Redesign

Redesign the podcast episode cards in the Music module to match a compact, Spotify-inspired layout. This includes reducing card height, artwork size, and improving the visual hierarchy and color logic.

## User Review Required

> [!IMPORTANT]
> The `podcast_description` field in `item_music_podcast_card_vertical.xml` will be removed or significantly reduced to achieve the "compact" layout requested. If the description is essential, I will truncate it to a single line.

## Proposed Changes

### UI Layout Redesign

#### [MODIFY] [item_music_podcast_card_vertical.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/item_music_podcast_card_vertical.xml)
*   Redesign the layout to be more compact.
*   Reduce `podcast_thumbnail_container` size from `120dp` to `80dp`.
*   Reposition `podcast_title` and `podcast_show_name` to be strictly to the right of the thumbnail.
*   Remove the large `podcast_description` TextView or limit it to 1 line.
*   Redesign `action_row` to be a compact horizontal row below the metadata or integrated into the right side.
*   Use `music_` palette colors for text and backgrounds.
*   Adjust margins and padding to eliminate excessive whitespace.

### Adapter Adjustments

#### [MODIFY] [MusicHomeAdapter.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicHomeAdapter.kt)
*   Update `PodcastVerticalViewHolder` to match the new layout structure.
*   Refine the `Palette` logic if necessary, ensuring the background color contrast remains high for text readability.

### FAB Overlap Prevention

#### [MODIFY] [fragment_music_home.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_home.xml)
*   Increase `paddingBottom` of the `RecyclerView` to ensure the "Return to Movies" FAB does not overlap the last podcast card.

## Verification Plan

### Automated Tests
*   Run layout inspector (if available in this environment) to verify dimensions.
*   Build the app to ensure no compilation errors in the binding classes.

### Manual Verification
*   Verify the new layout on different screen sizes using the `ui_state` or `take_screenshot` tools after deployment.
*   Confirm that the FAB is not obscuring any card content when scrolled to the bottom.
