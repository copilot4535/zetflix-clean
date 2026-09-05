# Redesign Podcast Card to Spotify-style Dark Episode Card

This plan involves a complete redesign of the vertical podcast card layout (`item_music_podcast_card_vertical.xml`) to match Spotify's premium dark episode discovery UI. It also includes updating the `MusicHomeAdapter` to handle dynamic color extraction and binding of new UI elements.

## User Review Required

> [!IMPORTANT]
> The `MusicHomeItem` data class only contains `title` and `subtitle`. I will parse the `subtitle` field (assuming it follows the "Show Name • Description" pattern) to populate the Show Name and Description snippet. If the pattern is different, the UI might need adjustment.

## Proposed Changes

### UI Resources

#### [NEW] [bg_circle_white.xml](file:///home/user/zetflix-clean/app/src/main/res/drawable/bg_circle_white.xml)
A simple circular white background for the play button.

#### [NEW] [bg_circle_ripple.xml](file:///home/user/zetflix-clean/app/src/main/res/drawable/bg_circle_ripple.xml)
A circular ripple effect for action buttons.

### Layouts

#### [MODIFY] [item_music_podcast_card_vertical.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/item_music_podcast_card_vertical.xml)
Redesign the layout to include:
- A horizontal top row with artwork and text.
- A multi-line description area.
- A bottom action row with Play, Add, and More buttons.
- Modern typography using Metropolis fonts.

### Logic

#### [MODIFY] [MusicHomeAdapter.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicHomeAdapter.kt)
Update `PodcastVerticalViewHolder` to:
- Bind the show name and description snippet by parsing the `subtitle`.
- Use `MusicColorHelper.getPalette` and `darkenColor` to compute a premium dark background.
- Wire up the new action buttons.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure no resource or binding errors.

### Manual Verification
- Inspect the Home screen's Podcast section.
- Verify the card background matches the artwork but remains dark.
- Ensure the Play, Add, and More buttons are correctly positioned and sized.
- Confirm text hierarchy (Title > Show Name > Description) is clear and readable.
