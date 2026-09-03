# Music Home Section Fix Plan

The music home screen currently displays very few items compared to SimpMusic. This is likely due to incomplete parsing of the InnerTube browse response in `YouTube.browse` and potential mapping issues in `MusicRepository`.

## Proposed Changes

### [kotlinYtmusicScraper]

#### [MODIFY] [YouTube.kt](file:///home/user/zetflix-clean/musicmodules/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/YouTube.kt)
- Expand `browse` method to handle more renderer types in `SectionListRenderer.Content`:
    - Add support for `musicShelfRenderer`.
    - Add support for `musicPlaylistShelfRenderer`.
- Improve `musicCarouselShelfRenderer` parsing:
    - Handle `musicResponsiveListItemRenderer` in addition to `musicTwoRowItemRenderer`.
- Add support for `response.contents.sectionListRenderer` if `singleColumnBrowseResultsRenderer` is missing.

### [app]

#### [MODIFY] [MusicRepository.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicRepository.kt)
- Add detailed logging for the number of sections and items fetched.
- Ensure all `YTItem` types are correctly mapped to `MusicHomeItem`.
- Handle potential null titles in sections more gracefully.

## Verification Plan

### Automated Tests
- Build the project: `./gradlew :app:assembleDebug`

### Manual Verification
- Deploy the app to a device/emulator.
- Open the Music Home screen.
- Verify that multiple sections (Quick Picks, Moods & Genres, etc.) are displayed.
- Check Logcat for "MusicHome" and "MusicViewModel" tags to verify the number of sections and items.
