# Optimize Home Header for Camera Notches

The goal is to prevent the logo and avatar from appearing too close to the system status bar (where the camera notch is usually located) by adding a deliberate vertical buffer and increasing the header height.

## Proposed Changes

### Dimensions

#### [MODIFY] [dimens.xml](file:///home/user/zetflix-clean/app/src/main/res/values/dimens.xml)
- Increase `home_header_height` from `75dp` to `85dp`.

### UI Layouts

#### [MODIFY] [fragment_home.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_home.xml)
- Add `android:paddingTop="8dp"` to the `sticky_header` `LinearLayout`.
- Add `android:paddingBottom="4dp"` to the `sticky_header`.
- This combination will:
    1.  Ensure a safe **8dp gap** between the status bar/notch and the logo/avatar.
    2.  Use the remaining vertical space to center the elements, while keeping them slightly lower than the perfect "mathematical" center of the screen top to avoid the notch area.

## Verification Plan

### Manual Verification
- Deploy the app and navigate to the Home screen.
- Verify that the logo and avatar have a comfortable gap from the battery/time icons.
- Verify that the header still looks balanced and doesn't cut off content.
