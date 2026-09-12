# ZETFLIX FEED ENGINE REFACTOR PLAN

Extract the CloudStream provider and feed orchestration logic from `LiveStreamViewModel` into a reusable `FeedEngine`. This foundation will support both `ZetStream` (LiveStream) and `ZetSports` while maintaining isolated provider failure handling and unified search/pagination.

## User Review Required

> [!IMPORTANT]
> **Refactor vs. Redesign**: This is purely an architectural refactor. The existing `LiveStreamFragment` and `ZetSportsFragment` will maintain their current UI and behavior, but their underlying data source will move to the new `FeedEngine`.

## Proposed Changes

### [Feed Engine]

#### [NEW] [FeedEngine.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/feed/FeedEngine.kt)
Create the core engine responsible for:
- Provider discovery and filtering (based on Live/Sports types).
- Concurrent data loading with isolated failure handling (using `APIRepository`).
- Feed state management (`ExpandableHomepageList` maps).
- Category-based filtering (`Live`, `Football`, `Cricket`, `More`).
- Unified search and pagination mechanics.

#### [NEW] [FeedModels.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/feed/FeedModels.kt)
Define generic models for the engine:
- `FeedQuery`: Encapsulates search query, category, and page.
- `FeedState`: Represents the current state of the feed (Loading, Success, Failure).
- `SportCategory` (Move from `LiveStreamViewModel`).

---

### [ZetStream / LiveStream]

#### [MODIFY] [LiveStreamViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/LiveStreamViewModel.kt)
- Instantiate `FeedEngine` within the ViewModel.
- Delegate data operations (`load`, `search`, `loadMore`, `expand`) to the engine.
- Expose engine state through the existing `filteredPage` LiveData to ensure `LiveStreamFragment` compatibility.
- Remove redundant provider loading and merging logic.

---

### [ZetSports]

#### [MODIFY] [ZetSportsFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/sports/ZetSportsFragment.kt)
- Ensure it continues to use the `LiveStreamViewModel` (shared with the engine) for its data feed.
- No UI changes, but it will now benefit from the cleaner engine-based architecture.

---

### [Architecture & Providers]

#### [PRESERVE] [APIRepository.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/APIRepository.kt)
The `FeedEngine` will consume the existing `APIRepository` and `MainAPI` architecture. No new repository or provider protocol will be introduced.

#### [PRESERVE] [MainActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt)
No changes to `MainActivity` or its navigation structure.

## Verification Plan

### Automated Tests
- Build the project: `./gradlew :app:assembleDebug`

### Manual Verification
- **LiveStream (ZetStream)**:
    - Verify the feed loads correctly on startup.
    - Verify search functionality works and returns filtered Live results.
    - Verify "Load More" (pagination) works for both home and search.
    - Verify that provider errors do not crash the entire feed.
- **ZetSports**:
    - Verify "Live", "Football", and "Cricket" chips correctly filter the engine output.
    - Verify search remains category-aware if triggered within Sports.
- **General**:
    - Check Logcat for any threading or concurrency issues during parallel provider loads.
    - Ensure `ZetMusic` remains unaffected.
