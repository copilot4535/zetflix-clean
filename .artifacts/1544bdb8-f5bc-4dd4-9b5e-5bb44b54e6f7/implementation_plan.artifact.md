# Fix ClassCastException in SimpMusic modules

The build failure is caused by a `ClassCastException` where `LibraryExtensionImpl` cannot be cast to `BaseExtension`. This is a common issue when using newer versions of AGP (9.1.1) with plugins that still expect the legacy `BaseExtension` class, or when mixing incompatible Kotlin Multiplatform and Android plugins.

Following the instructions, I will convert the "SimpMusic" modules from Kotlin Multiplatform (KMP) to standard Android Library modules.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///home/user/zetflix-clean/gradle/libs.versions.toml)
- Ensure `kotlin-android` plugin is available in the version catalog (or use it directly).

#### [MODIFY] [build.gradle.kts (root)](file:///home/user/zetflix-clean/build.gradle.kts)
- Declare `com.android.library` and `org.jetbrains.kotlin.android` in the root `plugins` block if not already present.

### SimpMusic Modules Conversion

For each of the following modules, I will:
1. Replace `org.jetbrains.kotlin.multiplatform` and `com.android.kotlin.multiplatform.library` with `com.android.library` and `org.jetbrains.kotlin.android`.
2. Move all `commonMain` and `androidMain` dependencies to a single `dependencies` block.
3. Replace the `kotlin { ... }` configuration with standard AGP `android { ... }` and `dependencies { ... }`.

#### [MODIFY] [music_full/build.gradle.kts](file:///home/user/zetflix-clean/music_full/build.gradle.kts)
#### [MODIFY] [musicmodules/common/build.gradle.kts](file:///home/user/zetflix-clean/musicmodules/common/build.gradle.kts)
#### [MODIFY] [musicmodules/domain/build.gradle.kts](file:///home/user/zetflix-clean/musicmodules/domain/build.gradle.kts)
#### [MODIFY] [musicmodules/kotlinYtmusicScraper/build.gradle.kts](file:///home/user/zetflix-clean/musicmodules/kotlinYtmusicScraper/build.gradle.kts)
#### [MODIFY] [musicmodules/ktorExt/build.gradle.kts](file:///home/user/zetflix-clean/musicmodules/ktorExt/build.gradle.kts)

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify the build completes successfully.
- Run a Gradle Sync to ensure the IDE can resolve all dependencies without `ClassCastException`.
