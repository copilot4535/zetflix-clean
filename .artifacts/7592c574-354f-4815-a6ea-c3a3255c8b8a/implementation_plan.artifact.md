# Fix RecyclerView "Tmp detached view" Crash in SyncedLyricsView

The goal is to fix the `java.lang.IllegalArgumentException: Tmp detached view should be removed from RecyclerView before it can be recycled` error occurring in `SyncedLyricsView`. This crash typically happens when `RecyclerView` tries to recycle a `ViewHolder` that is still in a "temporarily detached" state due to ongoing animations, often triggered by rapid calls to `notifyItemChanged` or `notifyItemRangeChanged`.

## User Review Required

> [!NOTE]
> This fix involves using `RecyclerView` payloads to bypass default change animations and ensuring that custom `ViewPropertyAnimator` animations are properly cancelled when a view is recycled. This is a standard and safe way to handle high-frequency UI updates in a `RecyclerView`.

## Proposed Changes

### UI Components

#### [MODIFY] [LyricsLineAdapter.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/LyricsLineAdapter.kt)
- Use a payload in `notifyItemRangeChanged` within the `currentLineIndex` setter. This informs `RecyclerView` that the update is a partial change, preventing the problematic "change animation" (cross-fade) that leads to the `tmpDetached` state.
- Override `onBindViewHolder` (3-argument version) to handle the payload.
- Implement `onViewRecycled` to cancel any running `ViewPropertyAnimator` animations on the `textView`.
- Add a `cancelAnimations()` method to `LyricsLineViewHolder`.

#### [MODIFY] [SyncedLyricsView.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/SyncedLyricsView.kt)
- Reinforce that `itemAnimator` is null and ensure no default animations can interfere.

## Verification Plan

### Manual Verification
- Launch the music player with synced lyrics.
- Observe lyrics scrolling and highlighting as the song progresses.
- Verify that the application no longer crashes with `IllegalArgumentException` during lyrics updates or scrolling.
