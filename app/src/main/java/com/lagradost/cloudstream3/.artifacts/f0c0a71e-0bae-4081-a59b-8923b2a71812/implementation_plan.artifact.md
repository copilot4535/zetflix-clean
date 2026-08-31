# Optimize App Startup & Content Display Timing

This plan aims to significantly reduce the time it takes for a user to see content after launching the app by eliminating redundant loading logic, reducing artificial delays, and parallelizing plugin initialization.

## User Review Required

> [!IMPORTANT]
> The artificial delay in the splash screen (`ZetFlixLoadingActivity`) will be reduced from 2 seconds to 800ms. This may affect the perceived "premium" feel of the cinematic entrance, but will greatly improve utility.

## Proposed Changes

### UI & Startup Flow

#### [MODIFY] [ZetFlixLoadingActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/setup/ZetFlixLoadingActivity.kt)
- Remove the blocking call to `PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_updateAllOnlinePluginsAndLoadThem`.
- Reduce `minDisplayTime` from 2000ms to 800ms (normal start) and 4000ms to 2000ms (first setup).
- Adjust the entrance animation duration to be snappier (1200ms -> 600ms).

#### [MODIFY] [MainActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt)
- Consolidate plugin loading logic to avoid redundant work.
- Ensure `loadSinglePlugin` for the `currentHomePage` is the absolute first thing that happens in the background to unblock `HomeFragment`.
- Ensure all other plugin updates/downloads happen in a low-priority background scope after the UI is inflated.

#### [MODIFY] [AccountSelectActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/account/AccountSelectActivity.kt)
- Ensure the transition to the next activity is immediate upon auth verification.

---

## Logic Improvements

- **Lazy Plugin Updates**: Instead of waiting for all repositories to sync before showing the Home screen, the app will now show the Home screen using cached providers and update them silently in the background.
- **Background Synchronization**: Move `runAutoUpdate()` and `PluginManager` updates to a unified background synchronization routine that doesn't compete for resources during the critical startup window.

## Verification Plan

### Manual Verification
1. **Cold Start Test**: Kill the app and launch it. Measure (visually or via logcat) the time from logo appearance to content shimmer on Home.
2. **Plugin Update Test**: Verify that plugins are still updated in the background by checking the "Plugins" settings after a few seconds.
3. **First Setup Test**: Clear app data and verify that the first-setup delay is still sufficient for initial plugin installation but not excessively long.

### Automated Tests
- Monitor Logcat for "Loaded everything" and "PluginManager: Loading finished" tags to ensure no regression in total load time.
