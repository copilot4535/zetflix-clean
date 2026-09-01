# Fix App Freeze by Optimizing Plugin Loading and SharedPreferences Writes

The app is freezing due to excessive and redundant SharedPreferences writes during plugin loading at startup. Specifically, each plugin being loaded (potentially hundreds) triggers a full read/write of the plugin list to SharedPreferences, resulting in $O(N^2)$ complexity. This causes long monitor contention on the main thread during `QueuedWork.processPendingWork()`.

## User Review Required

> [!IMPORTANT]
> The changes involve core plugin loading logic and startup sequence. While designed to be safe and more efficient, they touch critical paths.

## Proposed Changes

### [Plugin Manager Component](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt)

#### [MODIFY] [PluginManager.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt)
- Optimize `loadPlugin` to:
    - Return the plugin version (`Int?`) instead of just `Boolean`.
    - Accept a `saveToPrefs` parameter to allow batching writes.
    - Move the "already exists" check to the top to avoid redundant work.
    - Only call `setPluginData` if the version has actually changed compared to what was passed in `PluginData`.
- Refactor `___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins` to:
    - Avoid `removeKey(PLUGINS_KEY_LOCAL)` at the start if possible, or at least batch the subsequent saves.
    - Use the new `loadPlugin` batching capability to write the local plugin list once at the end.
- Use thread-safe map access for `plugins` and `urlPlugins`.

---

### [Main Activity Component](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt)

#### [MODIFY] [MainActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt)
- Flatten the nested `ioSafe` blocks in `onCreate` into a single sequential block. This prevents parallel execution of `loadSinglePlugin`, `updateAllOnlinePluginsAndLoadThem`, and `loadAllLocalPlugins`, which previously caused high lock contention and redundant IO overhead.

## Verification Plan

### Automated Tests
- Run the app and monitor Logcat for the "already exists" and "Long monitor contention" logs.
- Verify that plugins still load correctly on startup.

### Manual Verification
- Verify that the app starts up without freezing or showing ANR.
- Test downloading a new plugin and ensure it still loads and saves correctly.
- Test local plugin loading by placing a plugin in the local folder.
