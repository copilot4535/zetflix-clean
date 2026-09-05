# Fix ReceiverCallNotAllowedException in MusicWidgetProvider

The goal is to fix a fatal runtime crash in `MusicWidgetProvider.kt` caused by calling `bindService()` (via `MediaController.Builder`) using a restricted `BroadcastReceiver` context.

## Proposed Changes

### [app]

#### [MODIFY] [MusicWidgetProvider.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicWidgetProvider.kt)

- Refactor `onReceive` to use `context.applicationContext`.
- Use `goAsync()` to safely handle asynchronous `MediaController` initialization.
- Ensure `pendingResult.finish()` is called in `finally` to prevent ANRs.
- Wrap UI updates and playback controls in `try-catch` blocks with graceful logging.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure clean compilation.

### Manual Verification
- Re-run `adb logcat *:E | grep -E "AndroidRuntime|FATAL|com.zetflix.app.prerelease.debug"` to verify that launching `MusicActivity` no longer triggers `EXIT_SELF`.
- Verify widget functionality (Play/Pause, Next, Prev) still works as expected.
