# Performance & Stability Refactor Plan

This plan addresses two critical areas: optimizing layout hierarchy by removing redundant `NestedScrollView` wrappers around `RecyclerView`s and improving app responsiveness by moving synchronous `DataStore` (SharedPreferences) calls off the main thread.

## User Review Required

> [!IMPORTANT]
> - **Layout Changes**: Removing `NestedScrollView` changes how scrolling is handled. In some complex layouts like `fragment_result.xml`, this requires significant structural changes (moving headers into the `RecyclerView`).
> - **DataStore Lifecycle**: Moving calls to background threads may introduce race conditions if the app logic depends on immediate synchronous results. I will prioritize startup and frequent UI-update paths.

## Proposed Changes

### Layout Optimization (Removing NestedScrollView)

#### [MODIFY] [fragment_music_library.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_library.xml)
- Remove `NestedScrollView`.
- Move the static headers (Liked Songs, Downloads, Playlists) into a vertical `LinearLayout` above the `RecyclerView`s or as `RecyclerView` headers if applicable.
- For this specific layout, since there are multiple `RecyclerView`s, I will keep them in a `LinearLayout` but remove `NestedScrollView` and set one (or all) to wrap_content if they are short, or use a single `RecyclerView` with multiple view types for the whole screen (recommended).
- **Decision**: I will refactor `fragment_music_library.xml` to use a single `RecyclerView` for the entire content or optimize the `LinearLayout` if it's simpler for the specific case.

#### [MODIFY] [fragment_music_queue.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_queue.xml)
- Remove `NestedScrollView`.
- Use a vertical `LinearLayout` containing the "Now Playing" item and the `RecyclerView` (with `weight="1"`).

#### [MODIFY] [fragment_music_detail.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_detail.xml)
- Remove `NestedScrollView`.
- Move the header (Play/Shuffle buttons) into the `RecyclerView` as a header item type or keep it fixed above the list.

#### [MODIFY] [fragment_music_artist.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_artist.xml)
- Remove `NestedScrollView`.
- Similar to `fragment_music_detail.xml`, move content into `RecyclerView`.

#### [SKIP] [fragment_result.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_result.xml)
- **Reason**: Extremely complex layout with 900+ lines and multiple interactive components inside a `NestedScrollView`. Removing it requires a full rewrite of `ResultFragmentPhone.kt`'s scrolling logic and adapter.

---

### DataStore Backgrounding

#### [MODIFY] [MainActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt)
- Move `loadCache()` and `migrateResumeWatching()` to a background coroutine in `onCreate`.

#### [MODIFY] [SearchFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/movie/SearchFragment.kt)
- Move `DataStoreHelper.searchPreferenceProviders` and `DataStoreHelper.searchPreferenceTags` reads to background during initialization.

#### [MODIFY] [MusicViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicViewModel.kt)
- Ensure `loadPersistenceData()` is fully backgrounded (already partially done, but will verify all `MusicPersistence` calls).

## Verification Plan

### Automated Tests
- Run `:app:assembleDebug` to verify no layout compilation errors.

### Manual Verification
- Check Home, Result, and Music Library screens for scrolling behavior.
- Monitor Logcat for `TransactionTooLargeException`.
- Verify settings (liked songs, search history) persist correctly after backgrounding.
