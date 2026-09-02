# Implementation Plan - Resolve SDK XML Version Warning

This plan aims to resolve the "SDK XML version warning" and ensure a clean build environment by updating SDK components and clearing relevant caches.

## Proposed Changes

### Android SDK Components

#### [MODIFY] Update SDK Components
Update the following components to their latest stable versions:
- Command-line Tools (`cmdline-tools;latest`)
- Build-Tools (ensure 35.0.0+ is available, currently 36.0.0 is installed)
- Platform-Tools
- Platforms (Ensure `android-37` or `android-35` are properly installed as per `compileSdk`)

### Cache Cleanup

#### [DELETE] Clear Android Studio and Gradle Caches
- Manually delete `~/.android/cache` and `~/.android/build-cache` if they exist.
- Note: "File → Invalidate Caches" is an IDE action I cannot perform directly, but I can clear disk-based caches.

### Project Configuration

#### [MODIFY] [local.properties](file:///home/user/zetflix-clean/local.properties)
- Verify `sdk.dir` is correct (currently `/home/user/Android/Sdk`).

#### [MODIFY] [gradle/libs.versions.toml](file:///home/user/zetflix-clean/gradle/libs.versions.toml)
- Review SDK versions to ensure they are consistent with installed components.

## Verification Plan

### Automated Tests
- Run `./gradlew help` and `./gradlew clean` to ensure the build environment is healthy.
- Check for any remaining warnings in the output.

### Manual Verification
- Confirm with the user if the "SDK XML version warning" in the IDE is resolved.
