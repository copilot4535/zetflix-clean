# LiveStreamViewModel Changes

The `LiveStreamViewModel` was updated to support sport-specific filtering for the `ZetSports` experience.

## Changes:

1. **SportCategory Enum**: Defined categories for `Live`, `Football`, `Cricket`, and `More`.
2. **Category Selection**: Added `currentSportCategory` LiveData and `setSportCategory` method.
3. **Classification Logic**: Implemented `isItemInCategory` to classify streams based on provider category names and stream titles using keywords.
4. **Reactive Filtering**: Updated `_filteredPage` (MediatorLiveData) to react to category changes and apply classification-based filtering on the fly.
5. **Empty States**: Configured `_filteredPage` to return `Resource.Failure` with a descriptive message (e.g., "No Football streams available") when a selected category has no results.

## Classification Keywords:

- **Football**: football, soccer, premier league, la liga, serie a, bundesliga, ligue 1, champions league, europa league.
- **Cricket**: cricket, ipl, icc, t20, odi, test match.
- **More**: Generic "sports" keywords not matching Football or Cricket.
