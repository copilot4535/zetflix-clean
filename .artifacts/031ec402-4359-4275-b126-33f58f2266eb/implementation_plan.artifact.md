# UI Decoupling and Deferred Initialization Plan (Revised)

This plan outlines the steps to decouple the Movie Engine, isolate Media3 sessions, defer SDK initializations, and refine the plugin loading safety mechanisms.

## User Review Required

> [!IMPORTANT]
> The `MovieHomeActivity` will transition to a `ViewPager2` based layout to support lazy fragment inflation via `FragmentStateAdapter`. This will change how the Bottom Navigation interacts with fragments.

> [!IMPORTANT]
> `LiveStreamActivity` and `MovieHomeActivity` will be updated to use `repeatOnLifecycle(Lifecycle.State.STARTED)` for `StateFlow` collection to prevent race conditions during UI binding.

> [!WARNING]
> Google Cast SDK will now be initialized lazily. Users might see a slight delay (or the Cast icon appearing later) when opening the player for the first time.

## Proposed Changes

### [Core] [Performance & Stability Refinements]

#### [MODIFY] [MainActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt)
- Remove synchronous `CastContext.getSharedInstance()` from `onCreate`.
- Implement lazy initialization triggered by playback entry or Cast menu clicks.
- Ensure `finish()` and `Coil.imageLoader.memoryCache?.clear()` are called during activity transitions to other engines.

#### [MODIFY] [SafeProviderRegistry.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/plugins/SafeProviderRegistry.kt)
- Implement unified logging tag `SafeProviderRegistry` for all suppressed plugin failures to facilitate telemetry and debugging.

---

### [Feature] [Movie Engine Decoupling]

#### [NEW] [movie_navigation.xml](file:///home/user/zetflix-clean/app/src/main/res/navigation/movie_navigation.xml)
- Dedicated navigation graph for VOD content (Home, Search, Library).

#### [NEW] [MoviePagerAdapter.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/movie/MoviePagerAdapter.kt)
- `FragmentStateAdapter` to manage VOD fragments with `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT`.

#### [MODIFY] [MovieHomeActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/MovieHomeActivity.kt)
- Update layout to include `ViewPager2` for lazy fragment inflation.
- Update `loadingState` collection to use `repeatOnLifecycle`.
- Implement aggressive `onDestroy()` and `onTrimMemory()` cache clearing.

#### [MODIFY] [LiveStreamActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/livestream/LiveStreamActivity.kt)
- Update `loadingState` collection to use `repeatOnLifecycle`.

#### [MOVE] VOD Fragments
- Move `HomeFragment`, `SearchFragment`, `LibraryFragment` and their ViewModels to `com.lagradost.cloudstream3.ui.movie`.

---

### [Media] [Session Isolation]

#### [MODIFY] [ControllerActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/ControllerActivity.kt)
- Extract `MediaController` and `SessionManager` bindings to ensure they are only active during media playback contexts.

## Verification Plan

### Automated Tests
- `./gradlew :app:compileStableDebugKotlin`

### Manual Verification
- **Cold Boot Timing**: Verify via Logcat that `CastContext` is NOT initialized during `MainActivity.onCreate`.
- **Memory Profiling**: Use the Android Studio Memory Profiler to confirm bitmap memory release when switching from `MovieHomeActivity` to `LiveStreamActivity` or `MusicActivity`.
- **Telemetry**: Verify that plugin failures are logged with the `SafeProviderRegistry` tag in Logcat.
