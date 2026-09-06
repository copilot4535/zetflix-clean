# Fix RecyclerView "Tmp detached view" Crash in SyncedLyricsView

The goal is to fix the `java.lang.IllegalArgumentException: Tmp detached view` crash occurring in `SyncedLyricsView`. This happens because high-frequency lyric updates cause `RecyclerView`'s default `ItemAnimator` to conflict with custom `ViewPropertyAnimator` calls in the `ViewHolder`.

## Proposed Changes

### Music UI Component

#### [MODIFY] [LyricsLineAdapter.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/LyricsLineAdapter.kt)
- **Payload-based Updates**: Modify the `currentLineIndex` setter to pass a payload (e.g., `"HIGHLIGHT_UPDATE"`) to `notifyItemRangeChanged`. This signals to `RecyclerView` that it shouldn't perform a full "change" animation (which causes the detachment crash).
- **Partial Binding**: Implement the 3-argument `onBindViewHolder` that accepts payloads. If the payload is present, only update the highlight colors/scale without re-inflating or re-binding the full text.
- **Animation Safety**: Explicitly call `textView.animate().cancel()` in `onViewRecycled` to ensure no animations are running on a detached view.

#### [MODIFY] [SyncedLyricsView.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/SyncedLyricsView.kt)
- **Strengthen Animator Disable**: Ensure `itemAnimator` is explicitly set to `null` and check for any potential re-enabling in layout passes.

## Verification Plan

### Automated Tests
- Build the project using `gradle_build(":app:assembleDebug")` to verify syntax.

### Manual Verification
- Observe the synced lyrics during playback to ensure the crash no longer occurs when the highlight moves between lines.
- Verify that the smooth transitions (alpha/scale) still function correctly.
