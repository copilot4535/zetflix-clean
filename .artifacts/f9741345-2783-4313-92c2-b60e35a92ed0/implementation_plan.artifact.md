# Fix Double Spinner and Stacked Loading Bug in Livestream

The user reported a UI bug in the livestream menu where "load more" shows double or overlapping spinners and a "stacked loading" bug.

## User Review Required

> [!NOTE]
> The "double spinner" is caused by the `MaterialButton` in the "load more" footer keeping its default icon visible while a separate `ProgressBar` is shown on top of it.
> The "stacked loading" bug is caused by multiple asynchronous UI updates being triggered during parallel API calls in the `LiveStreamViewModel`.

## Proposed Changes

### UI Layer

#### [MODIFY] [LiveStreamFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/LiveStreamFragment.kt)
- Update `onBindFooter` to hide the `MaterialButton` icon when the loading state is active. This ensures only the `ProgressBar` is visible, preventing overlapping icons/spinners.

### ViewModel Layer

#### [MODIFY] [LiveStreamViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/LiveStreamViewModel.kt)
- Refactor `search` and `loadMore` to avoid race conditions when updating the `searchExpandable` map.
- Move UI updates (`_searchPage.postValue`) outside of parallel `amap` blocks to prevent "stacked" or redundant UI refreshes.
- Ensure thread-safe access to shared mutable state during concurrent API fetching.

## Verification Plan

### Manual Verification
- Navigate to the Livestream menu.
- Click "Load More" and verify that only one spinner (the `ProgressBar`) is visible and centered.
- Perform a search in the Livestream menu and click "Load More" while searching; verify that results load smoothly without UI flickering or multiple redundant refreshes.
- Verify that the "Load More" button icon reappears once loading is complete.
