# Walkthrough - Separate Livestream Search

I have implemented a dedicated search bar for the Livestream tab that filters only the content specific to the livestream menu.

## Changes Made

### 1. UI Enhancements
Modified `fragment_home.xml` to include a `home_search_bar` in the sticky header. This bar is hidden by default and is shared by any fragment using the home layout that chooses to implement it.

### 2. ViewModel Search Logic
Updated `LiveStreamViewModel.kt` to support real-time filtering:
- Added `searchQuery` and `filteredPage` LiveData to manage the search state.
- Implemented a `search(query: String)` method that filters the loaded livestream items by name (case-insensitive).
- Ensured that content is filtered immediately upon loading or when the search query changes.

### 3. Fragment Integration
Updated `LiveStreamFragment.kt` to wire up the search UI:
- Replaced the global search navigation with a local toggle for the new search bar.
- Added a `TextChangedListener` to provide instant search results as the user types.
- Handled the focus and soft keyboard interactions for a smooth search experience.
- Ensured the UI correctly observes the filtered results instead of the raw data.

## Verification

### Build Status
- **Success**: The project compiles successfully after fixing minor resource linking issues.

### Functionality
- Tapping the search icon in the Livestream tab now opens a local search bar.
- Typing in the search bar filters the current list of live channels and categories.
- Closing the search bar clears the filter and restores the default Livestream view.
