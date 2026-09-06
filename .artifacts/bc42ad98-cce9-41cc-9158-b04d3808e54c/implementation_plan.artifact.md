# ZETMUSIC Engine Optimization Plan

This plan outlines the systematic optimization of the ZetMusic engine to improve reliability, reduce latency, and minimize redundant work.

## User Review Required

> [!IMPORTANT]
> The optimization work will be performed on the `zetmusic-engine-optimization` branch.
> Baseline measurements will be established before applying changes.
> The architecture will move towards a unified Stream Resolver.

## Open Questions

- Should we increase the cache size for stream URLs beyond 50 entries?
- Is there a specific target for first-play latency (e.g., < 1.5s)?

## Proposed Changes

### Phase 3-5: Unified Stream Resolver & Deduplication

We will consolidate stream resolution logic into a single logical component to handle caching, deduplication, and fallback.

#### [NEW] [StreamResolver.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/StreamResolver.kt)
- Create a `StreamResolver` class to encapsulate `extractStreamUrl` logic.
- Implement in-flight deduplication using a `Mutex` or `MutableStateFlow` of jobs.

#### [MODIFY] [MusicViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicViewModel.kt)
- Delegate stream extraction to `StreamResolver`.
- Improve `playQueue` to use the new resolver and handle cancellations better.

### Phase 4: Cache Optimization

#### [MODIFY] [StreamUrlCache.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/StreamUrlCache.kt)
- Enhance `CacheEntry` to include more metadata (bitrate, mediaId).
- Investigate better expiration logic based on observed URL lifetime.

### Phase 7: Stream URL Expiration Recovery

#### [MODIFY] [MusicService.kt](file:///home/user/zetflix-clean/services/music/MusicService.kt)
- Implement a playback error listener that classifies 403 errors as potential URL expirations.
- Implement a callback to the ViewModel or Resolver to re-extract the URL and update the player.

### Phase 10-11: Prefetch & Search Optimization

#### [MODIFY] [MusicViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicViewModel.kt)
- Implement priority-based prefetch (next track vs. rest of queue).
- Add debounce/cancellation to the `search()` function to handle rapid typing.

### Phase 12: Home Loading Optimization

#### [MODIFY] [MusicViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicViewModel.kt)
- Optimize `loadHomeSections()` parallelism.
- Implement basic in-memory caching for home sections in `MusicRepository`.

---

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` after each phase.
- Create unit tests for `StreamResolver` deduplication and cache logic.

### Manual Verification
- Verify search responsiveness.
- Verify playback stability on track transitions.
- Verify that 403 errors (simulated if possible) trigger re-resolution.
- Monitor memory usage during large queue loading.
