# Implementation Plan - TV Remnants Cleanup

This plan outlines the steps to remove all TV and Emulator specific logic and resources from the ZetFlix project, making it mobile/smartphone-only.

## Proposed Changes

### Step 1: Delete TV Layout Files
- [ ] Delete `app/src/main/res/layout/activity_main_tv.xml`
- [ ] Delete `app/src/main/res/layout/fragment_home_tv.xml`
- [ ] Delete `app/src/main/res/layout/fragment_library_tv.xml`
- [ ] Delete `app/src/main/res/layout/fragment_player_tv.xml`
- [ ] Delete `app/src/main/res/layout/fragment_result_tv.xml`
- [ ] Delete `app/src/main/res/layout/fragment_search_tv.xml`
- [ ] Delete `app/src/main/res/layout/home_scroll_view_tv.xml`
- [ ] Delete `app/src/main/res/layout/bottom_resultview_preview_tv.xml`
- [ ] Delete `app/src/main/res/layout/player_custom_layout_tv.xml`
- [ ] Delete `app/src/main/res/layout/repository_item_tv.xml`
- [ ] Delete `app/src/main/res/layout/homepage_parent_tv.xml`
- [ ] Delete `app/src/main/res/layout/homepage_parent_emulator.xml`

### Step 2: Delete TV Resource Files
- [ ] Delete `app/src/main/res/color/item_select_color_tv.xml`
- [ ] Delete `app/src/main/res/color/player_button_tv.xml`
- [ ] Delete `app/src/main/res/color/player_on_button_tv.xml`
- [ ] Delete `app/src/main/res/color/player_on_button_tv_attr.xml`
- [ ] Delete `app/src/main/res/drawable/player_button_tv.xml`
- [ ] Delete `app/src/main/res/drawable/player_button_tv_attr.xml`
- [ ] Delete `app/src/main/res/drawable/player_button_tv_attr_no_bg.xml`
- [ ] Delete `app/src/main/res/drawable/player_gradient_tv.xml`

### Step 3: Update Adapters
- [ ] `HomeScrollAdapter.kt`: Remove `HomeScrollViewTvBinding` and TV conditional logic.
- [ ] `PluginAdapter.kt`: Use `R.layout.repository_item` and remove TV conditional logic.
- [ ] `SearchHistoryAdaptor.kt`: Remove `isLayout(TV or EMULATOR)` logic.
- [ ] `SearchSuggestionAdapter.kt`: Remove `isLayout(TV or EMULATOR)` logic.

### Step 4: Simplify Globals.kt
- [ ] Remove `TV` and `EMULATOR` constants.
- [ ] Simplify `layoutIntCorrected` to return `PHONE`.
- [ ] Simplify `isLayout` to only check for `PHONE`.
- [ ] Simplify `isLandscape`.

### Step 5: Clean up Conditional UI Logic
- [ ] `EpisodeAdapter.kt`: Remove TV branches.
- [ ] `UIHelper.kt`: Remove TV/EMULATOR system UI logic.
- [ ] `MainActivity.kt`: Remove TV exit logic and nav rail focus logic.
- [ ] `GeneratorPlayer.kt`: Remove TV episode size logic.
- [ ] `HomeFragment.kt`: Remove `saveHomepageToTV()` and TV layout checks.
- [ ] `SettingsPlayer.kt`: Remove TV/EMULATOR preference hiding.
- [ ] `DownloadQueueFragment.kt`: Remove TV layout padding logic.
- [ ] `CS3IPlayer.kt`: Remove TV audio/subtitle priority logic.
- [ ] `AppContextUtils.kt`: Simplify alert dialog focus logic.
- [ ] `PlayerView.kt` / `PlayerGestureHelper.kt`: Remove TV orientation and gesture logic.

### Step 6: Update Instrumented Tests
- [ ] `ExampleInstrumentedTest.kt`: Remove TV binding imports and TV-specific tests.

### Step 7: Build and Verify
- [ ] Build app: `./gradlew :app:assembleDebug`
- [ ] Verify no TV/EMULATOR references: `grep -r "TV\|EMULATOR\|TvBinding" app/src/main/java`

## Verification Plan
### Automated Tests
- Build success confirms no broken references to deleted files.
- Instrumented tests (after cleanup) should pass.

### Manual Verification
- Grep for TV remnants.
