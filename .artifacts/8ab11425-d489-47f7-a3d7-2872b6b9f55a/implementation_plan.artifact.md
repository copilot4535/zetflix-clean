# Implementation Plan - Music Module Test Infrastructure and Initial Tests

This plan outlines the steps to add necessary test dependencies and implement the first set of automated tests for the music module components: `LrcParser` and `StreamUrlCache`.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///home/user/zetflix-clean/gradle/libs.versions.toml)
- Add `mockk` version `1.13.8` and `archCoreTesting` version `2.2.0` to `[versions]`.
- Add `mockk` and `arch-core-testing` to `[libraries]`.

#### [MODIFY] [app/build.gradle.kts](file:///home/user/zetflix-clean/app/build.gradle.kts)
- Add `testImplementation(libs.mockk)` and `testImplementation(libs.arch.core.testing)`.
- Add `androidTestImplementation(libs.mockk)`.

---

### Music Module Tests

#### [NEW] [LyricsUtilsTest.kt](file:///home/user/zetflix-clean/app/src/test/java/com/lagradost/cloudstream3/ui/music/LyricsUtilsTest.kt)
- Implement unit tests for `LrcParser.parse()`:
    - Successful parsing of valid LRC strings with timestamps.
    - Graceful handling of null, empty, or whitespace strings (should return empty list).
    - Handling of strings without timestamps (should return empty list).
    - Verification of timestamp calculation (minutes, seconds, milliseconds).

#### [NEW] [StreamUrlCacheTest.kt](file:///home/user/zetflix-clean/app/src/androidTest/java/com/lagradost/cloudstream3/ui/music/StreamUrlCacheTest.kt)
- Implement instrumentation tests for `StreamUrlCache`:
    - Basic `put` and `get` functionality.
    - LRU eviction: inserting more than 50 items and verifying old items are removed.
    - Expiration logic: verifying items are removed after 30 minutes (may require internal state manipulation or manual clock if supported, otherwise basic verification of time-based retrieval).

## Verification Plan

### Automated Tests
- Run unit tests: `./gradlew :app:testDebugUnitTest --tests "com.lagradost.cloudstream3.ui.music.LyricsUtilsTest"`
- Run instrumentation tests: `./gradlew :app:connectedDebugAndroidTest --tests "com.lagradost.cloudstream3.ui.music.StreamUrlCacheTest"`
- Verify build success: `./gradlew :app:assembleDebug`

### Manual Verification
- Review test coverage and ensure all edge cases for `LrcParser` are handled.
- Check that `StreamUrlCache` behavior matches the implementation in `StreamUrlCache.kt`.
