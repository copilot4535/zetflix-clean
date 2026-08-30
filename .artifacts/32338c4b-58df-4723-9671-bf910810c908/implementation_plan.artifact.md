# Optimize Logo and Avatar Placement for Camera Notches

Optimize the Home screen header to ensure that the logo and avatar have sufficient clearance from the system status bar and camera notch, preventing UI overlap and improving aesthetics.

## Proposed Changes

### UI Components

#### [MODIFY] [dimens.xml](file:///home/user/zetflix-clean/app/src/main/res/values/dimens.xml)
- Increase `home_header_height` from `75dp` to `80dp` to allow for more breathing room.

#### [MODIFY] [fragment_home.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_home.xml)
- Add `android:paddingTop="8dp"` to the `sticky_header` `LinearLayout`.
- This ensures a minimum 8dp gap below the system status bar/notch, as the `fixSystemBarsPadding` utility will add the system insets on top of this initial padding.

## Verification Plan

### Manual Verification
- Deploy the app to a device or emulator with a camera notch.
- Verify that the ZetFlix logo and user avatar are clearly separated from the status bar icons (clock, battery, etc.) and the notch.
- Ensure the header background transition (translucency on scroll) still works correctly with the adjusted height.
