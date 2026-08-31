# Fix Splash Screen Logo Animation and Status Text Placement

The goal is to fix the "cut box" effect during the logo zoom animation and move the "Optimizing your experience" status text higher on the screen.

## User Review Required

> [!NOTE]
> I am moving the zoom animation target from the logo `ImageView` to its parent `ShimmerFrameLayout`. This ensures that the logo isn't clipped by its parent's bounds during the 12x zoom.

## Proposed Changes

### UI Components

#### [MODIFY] [activity_zetflix_loading.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/activity_zetflix_loading.xml)
- Adjust `loading_progress` vertical bias from `0.75` to `0.70` to move the status text higher.
- Ensure all relevant containers have `clipChildren="false"`.

#### [MODIFY] [ZetFlixLoadingActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/setup/ZetFlixLoadingActivity.kt)
- Update `performHeroExit` to stop the shimmer effect before starting the zoom.
- Change the zoom target from `logo` to `shimmer` (the parent container) to prevent clipping.
- Slightly increase zoom scale to `15f` and `ALPHA` to `0f` to ensure a smooth transition to the next screen.

## Verification Plan

### Manual Verification
- Deploy the app and observe the splash screen transition.
- Verify the "Zetflix" logo zooms smoothly without being clipped by a rectangular box.
- Verify the "Optimizing your experience" text is positioned higher and more comfortably on the screen.
