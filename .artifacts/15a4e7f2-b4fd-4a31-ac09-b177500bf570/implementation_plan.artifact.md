# Update Media Player and Identify Livestream Handler

This plan outlines the steps to identify the livestream handler used in the project and update all media player dependencies to their latest stable versions.

## Livestream Handler Identification

The project uses a custom livestream synchronization mechanism:
- **Handler**: `LiveHelper.kt` and `LiveManager.kt` (located in `app/src/main/java/com/lagradost/cloudstream3/ui/player/live/`).
- **Mechanism**: It leverages Media3's `Timeline` and `PositionInfo` to maintain a stable live edge with a 5-second offset (`PREFERRED_LIVE_OFFSET`). It automatically seeks to the optimal live point if the player drifts.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///home/user/zetflix-clean/gradle/libs.versions.toml)
- Update `media3` version from `1.10.1` to `1.11.0`.
- Update `nextlibMedia3` version from `1.10.1-0.13.0` to `1.11.0-0.14.0`.

### Media Player Implementation

#### [MODIFY] [CS3IPlayer.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/player/CS3IPlayer.kt)
- Review and update any deprecated Media3 APIs if necessary (e.g., `experimentalSetLegacyDecodingEnabled`).

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure the project still builds with the new versions.

### Manual Verification
- Verify media playback (VOD and Livestream) on a device.
- Check if FFmpeg decoders (NextLib) are correctly initialized in `FixedNextRenderersFactory`.
