# Implementation Plan - Restrict Sport Plugins to Livestream Menu

This plan outlines the changes required to filter out plugins with "sport" in their name from the Home screen and ensure they appear in the Livestream menu.

## Proposed Changes

### [Component Name] UI Home ViewModels

Consolidate keywords and update filtering logic in `HomeViewModel` and `LiveStreamViewModel`.

#### [MODIFY] [BaseHomeViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/BaseHomeViewModel.kt)
- Add a protected `sportKeywords` list to be shared by child ViewModels.

#### [MODIFY] [HomeViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeViewModel.kt)
- Remove local `liveKeywords` list.
- Override `getFilteredApis()` to exclude APIs with names containing any `sportKeywords`.
- Update `mergeHomeResult` to use the shared `sportKeywords`.

#### [MODIFY] [LiveStreamViewModel.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/LiveStreamViewModel.kt)
- Remove local `liveKeywords` list.
- Update `getFilteredApis()` to include APIs with names containing any `sportKeywords`.
- Update `mergeHomeResult` to use the shared `sportKeywords`.

## Verification Plan

### Manual Verification
1.  Deploy the app.
2.  Install a plugin with "sport" in its name (e.g., "SportsStream").
3.  Verify that "SportsStream" does not appear as a provider on the Home screen.
4.  Verify that "SportsStream" appears as a provider in the Livestream menu.
5.  Verify that other non-sport plugins still appear on the Home screen.
