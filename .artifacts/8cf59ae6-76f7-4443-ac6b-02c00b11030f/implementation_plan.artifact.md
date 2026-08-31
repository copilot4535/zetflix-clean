# Cinematic Splash Screen Improvements

This plan outlines the steps to overhaul the ZetFlix splash screen with a cinematic, high-end animation sequence, including logo shimmering, pulsing, and a dramatic "Hero" zoom transition.

## Proposed Changes

### [Component] UI / Layout

#### [MODIFY] [activity_zetflix_loading.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/activity_zetflix_loading.xml)
- Add a `com.facebook.shimmer.ShimmerFrameLayout` around the `loading_logo` to provide a premium light-sweep effect during loading.
- Add a `View` behind the logo to act as a "Glow" source with a radial gradient.
- Improve the placement and styling of the status text and progress indicator for a cleaner look.

#### [NEW] [logo_glow_gradient.xml](file:///home/user/zetflix-clean/app/src/main/res/drawable/logo_glow_gradient.xml)
- Create a radial gradient drawable to serve as the background glow for the logo.

### [Component] Logic / Animation

#### [MODIFY] [ZetFlixLoadingActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/setup/ZetFlixLoadingActivity.kt)
- Replace basic `ViewPropertyAnimator` calls with a coordinated `AnimatorSet`.
- **Phase 1: Entrance**: Dramatic scale-up with `OvershootInterpolator`.
- **Phase 2: Loading State**: Continuous subtle "breathing" pulse and shimmer activation.
- **Phase 3: Exit**: A cinematic "zoom-in" (hero transition) where the logo expands towards the viewer while the screen fades out.
- Coordinate status text updates with alpha transitions.

## Verification Plan

### Manual Verification
- Deploy to an emulator/device.
- Observe the splash screen on a "Cold Start" (Authenticated).
- Verify the shimmer effect is visible while plugins are loading.
- Ensure the exit transition to `MainActivity` is smooth and free of flickering.
- Test "First Setup" mode (logout/login) to see the extended 4-second animation.
