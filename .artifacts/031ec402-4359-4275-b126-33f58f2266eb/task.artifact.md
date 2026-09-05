# Task Checklist

## Core Stability & Telemetry
- [x] Implement `PluginLoadingState` and `StateFlow` in `PluginManager.kt`
- [x] Refactor `loadAllOnlinePlugins` and `loadAllLocalPlugins` to be async in `PluginManager.kt`
- [x] Refine `SafeProviderRegistry.kt` with unified telemetry logging
- [x] Add `getProvidersForType` to `APIHolder` in `MainAPI.kt`

## Startup Optimization
- [x] Create `OatCleanupWorker.kt`
- [x] Update `MainActivity.kt` to use `WorkManager` for oat cleanup
- [ ] Defer `CastContext` initialization in `MainActivity.kt`
- [x] Implement activity-swap memory cleanup in `MainActivity.kt`

## Feature-Specific Components
- [x] Create `layout/activity_live_stream.xml`
- [x] Update `LiveStreamActivity.kt` with `repeatOnLifecycle`
- [x] Create `movie_navigation.xml` and `MoviePagerAdapter.kt`
- [x] Move VOD fragments to `ui.movie`
- [x] Update `MovieHomeActivity.kt` with `ViewPager2`, `repeatOnLifecycle`, and memory cleanup

## Media Session Isolation
- [ ] Extract `MediaController` bindings from `ControllerActivity.kt`

## Verification
- [ ] Compile project (`./gradlew :app:compileStableDebugKotlin`)
- [ ] Verify `CastContext` lazy loading in logs
- [ ] Profile memory during Activity swaps
