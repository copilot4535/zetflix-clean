# Upgrade Media Player Dependencies

Update Media3 and NextLib (FFmpeg extension) to the latest compatible stable versions to benefit from bug fixes, performance improvements, and new features like `PlayerPool`.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///home/user/zetflix-clean/gradle/libs.versions.toml)
- Update `media3` version from `1.9.3` to `1.10.1`.
- Update `nextlibMedia3` version from `1.9.3-0.12.0` to `1.10.1-0.13.0`.

## Verification Plan

### Automated Tests
- Run a clean build: `./gradlew clean :app:assembleDebug`
- Run unit tests related to player logic: `./gradlew :app:testDebugUnitTest --tests "com.lagradost.cloudstream3.ui.player.*"`

### Manual Verification
- Deploy the app and verify video playback works as expected (Internal player).
- Verify that FFmpeg-dependent streams (if any) still play correctly using NextLib decoders.
- Check subtitle rendering and seeking behavior.
