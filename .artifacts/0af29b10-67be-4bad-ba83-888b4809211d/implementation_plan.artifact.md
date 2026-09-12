# ZetSports Rescue - Restore Working Live Content + Crash-Safe Launch

Restore the working LiveStream content pipeline inside `SportsActivity` while ensuring the Activity launches safely.

## User Review Required

> [!IMPORTANT]
> - `SportsActivity` will now launch directly into the existing `LiveStreamFragment` instead of the previous `SportsHomeFragment`.
> - The old `SportsHomeFragment` and related OpenLigaDB logic will be preserved but disconnected from the main navigation path.
> - A thin wrapper `ZetSportsFragment` will be used to customize the title to "ZetSports" while maintaining 100% logic parity with `LiveStreamFragment`.

## Proposed Changes

### [Component] Sports Navigation & Activity

#### [MODIFY] [SportsActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/sports/SportsActivity.kt)
- Fix the `navHostFragment` ID lookup to match `activity_sports.xml` (`sports_nav_host_fragment` instead of `nav_host_fragment`).

#### [NEW] [ZetSportsFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/sports/ZetSportsFragment.kt)
- Create a minimal wrapper extending `LiveStreamFragment` to override the header title to "ZetSports".

#### [MODIFY] [sports_navigation.xml](file:///home/user/zetflix-clean/app/src/main/res/navigation/sports_navigation.xml)
- Add `ZetSportsFragment` as a destination.
- Set `ZetSportsFragment` as the `startDestination`.
- Keep `SportsHomeFragment` in the graph but remove it from the start path.

#### [MODIFY] [activity_sports.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/activity_sports.xml)
- Add the initial navigation shell (ZetSports title and category placeholders) if it doesn't conflict with the fragment's internal header.
- *Note: For the first pass, we will prioritize stability and might rely on the Fragment's header.*

---

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify compilation.

### Manual Verification
- Launch `SportsActivity` via ADB: `adb shell am start -n com.lagradost.cloudstream3/.ui.sports.SportsActivity`.
- Verify `ZetSports` title is displayed.
- Verify provider content starts loading (shimmer appears).
- Verify navigation back to `MainActivity` works.
- Check `logcat` for any `FATAL EXCEPTION` or navigation errors.
