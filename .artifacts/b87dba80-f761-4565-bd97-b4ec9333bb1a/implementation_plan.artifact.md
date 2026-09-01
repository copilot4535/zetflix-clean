# Optimize Provider and Plugin Management to Prevent Startup Crashes

The application currently loads multiple providers and plugins concurrently during startup. Using `amap` without a concurrency limit can lead to excessive resource consumption (memory and CPU), causing crashes (OOM or SIGSEGV) especially on devices with limited resources or when many plugins are installed/pinned.

## User Review Required

> [!IMPORTANT]
> This change introduces a concurrency limit to plugin loading. While this improves stability, it may slightly increase the total time it takes for *all* plugins to be loaded, although the "Current Home" provider will be prioritized to ensure the UI feels responsive.

## Proposed Changes

### Core Library Optimization

#### [MODIFY] [ParCollections.kt](file:///home/user/zetflix-clean/library/src/commonMain/kotlin/com/lagradost/cloudstream3/ParCollections.kt)
- Add a new `amap` extension function that supports a `concurrencyLimit` using a `Semaphore`.
- This will allow us to control how many background tasks run in parallel.

### Plugin System Optimization

#### [MODIFY] [PluginManager.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt)
- Update `___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins` to use the throttled `amap`.
- Prioritize Tier 1 (Current Home) by loading it strictly before other tiers.
- Apply a concurrency limit (e.g., 2 or 3) to Tier 2 (Pinned) and Tier 3 (Priority) plugins.
- Keep Tier 4 as sequential/staggered but ensure it doesn't interfere with the primary UI responsiveness.

### Startup Logic Refactoring

#### [MODIFY] [MainActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt)
- Ensure `initAll()` and plugin loading are orchestrated to minimize main thread pressure and peak memory usage.
- Potentially wrap `api.init()` calls in a way that they don't block each other if one is slow.

## Verification Plan

### Automated Tests
- I will check for any existing unit tests for `PluginManager` or `ParCollections` and add tests for the new throttled `amap`.
- Since these are concurrency issues, manual verification on a device is more effective.

### Manual Verification
- Deploy the app and monitor logcat during startup.
- Observe the "Currently loading extension" logs to ensure they are now staggered/throttled.
- Verify that the Home screen remains responsive and the primary provider loads first.
- Simulate having many pinned providers to see if stability improves.
