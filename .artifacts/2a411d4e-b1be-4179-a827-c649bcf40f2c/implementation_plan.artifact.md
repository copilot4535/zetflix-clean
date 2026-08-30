# New Logo Implementation

This plan updates the app logo to the new design provided by the user. The new design features a stylized "Z" with a play button cutout, using a "premium dark Netflix style" with a pure black background and vibrant red accents.

## Proposed Changes

### Icon Resource Optimization

#### [MODIFY] [ic_launcher_background.xml](file:///home/user/zetflix-clean/app/src/main/res/values/ic_launcher_background.xml)
- Change the background color to pure black (`#000000`) for that premium Netflix look.

#### [MODIFY] [ic_launcher_foreground.xml](file:///home/user/zetflix-clean/app/src/main/res/drawable/ic_launcher_foreground.xml)
- Update the foreground vector with the new "Z" and play button cutout design.
- Use Netflix Red (`#E50914`) for the main logo body.
- Use pure black (`#000000`) for the play button cutout to match the background.

#### [MODIFY] [ic_stat_name.xml](file:///home/user/zetflix-clean/app/src/main/res/drawable/ic_stat_name.xml)
- Update the notification icon to use the new logo's simplified silhouette.

#### [MODIFY] [splash_background.xml](file:///home/user/zetflix-clean/app/src/main/res/drawable/splash_background.xml)
- Ensure the splash screen uses the new foreground logo centered on a black background.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure resource compilation.

### Manual Verification
- Verify the new icon appears correctly on the device launcher (may require cache clearing).
- Check the splash screen on app startup.
- Verify the notification icon in the status bar.
