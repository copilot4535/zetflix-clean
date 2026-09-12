# YouTube Music / YouTube Account Integration Implementation Plan

This plan outlines the steps to build a secure and robust YouTube/YouTube Music account integration for ZetMusic.

## User Review Required

> [!IMPORTANT]
> - Manual cookie paste UI will be removed.
> - Google Sign-in will be the primary method for connecting accounts.
> - Secure storage will be used for all sensitive credentials.
> - Backups will no longer contain authentication secrets.

## Proposed Changes

### Dependencies

#### [MODIFY] [libs.versions.toml](file:///home/user/zetflix-clean/gradle/libs.versions.toml)
Add Credential Manager and Google ID dependencies.

#### [MODIFY] [app/build.gradle.kts](file:///home/user/zetflix-clean/app/build.gradle.kts)
Apply the new dependencies.

---

### Security & Data Modeling

#### [NEW] [MusicAccountModels.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicAccountModels.kt)
Define `AccountState`, `AccountMetadata`, and `AuthCredentials` (securely).

#### [MODIFY] [MusicBackupModels.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicBackupModels.kt)
Remove the `cookie` field from `MusicBackupData`.

---

### Credential Storage

#### [NEW] [MusicAuthManager.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicAuthManager.kt)
Handles secure persistence of tokens using `EncryptedSharedPreferences`.

---

### Repository & Scraper Logic

#### [MODIFY] [YtmAccountRepository.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/YtmAccountRepository.kt)
- Implement `connect()`, `disconnect()`, `validateSession()`.
- Coordinate between Scraper (InnerTube) and Official YouTube API.
- Manage `AccountState`.

#### [MODIFY] [Ytmusic.kt](file:///home/user/zetflix-clean/musicmodules/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/Ytmusic.kt)
- Remove sensitive logging of `SAPISIDHASH`.

#### [MODIFY] [YouTube.kt](file:///home/user/zetflix-clean/musicmodules/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/YouTube.kt)
- Remove/Redact sensitive logging.

---

### UI Integration

#### [MODIFY] [MusicSettingsFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicSettingsFragment.kt)
- Remove manual cookie input UI.
- Add "Connect YouTube Music" button.
- Show account metadata (avatar, name) when connected.
- Implement disconnect flow.

#### [MODIFY] [MusicRepository.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicRepository.kt)
- Add personalized sections to `getHomeSections()` when authenticated.

#### [MODIFY] [fragment_music_settings.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_settings.xml)
Update layout to reflect account connection status.

## Verification Plan

### Automated Tests
- `gradlew :app:assembleDebug` to ensure compilation.
- I will add unit tests for `MusicAuthManager` if possible, but mocking `EncryptedSharedPreferences` might be complex without a full test harness. I'll focus on state machine logic in `YtmAccountRepository`.

### Manual Verification
1.  **Authentication**: Trigger "Connect YouTube Music", verify Credential Manager shows up.
2.  **State Management**: Verify UI updates to "Connected" after successful auth.
3.  **Persistence**: Restart app, verify account remains connected.
4.  **Security Audit**:
    - Check logs for cookies/tokens.
    - Export backup, check JSON for `cookie` field.
5.  **Functionality**:
    - Load "My Playlists" and "Mixed For You".
    - Play a song from a private playlist.
6.  **Disconnect**: Tap disconnect, verify credentials cleared and UI resets.
