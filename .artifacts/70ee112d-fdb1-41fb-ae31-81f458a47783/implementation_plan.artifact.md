# ZetFlix Authentication & Session Overhaul Plan

This plan outlines the steps to fix and improve the ZetFlix login, register, account, fingerprint, and session systems to achieve a stable, secure, local-only authentication system with a clean UX.

## User Review Required

> [!IMPORTANT]
> - **Biometrics:** The fingerprint login will be implemented as a local-only security layer. It will use `EncryptedSharedPreferences` to store credentials securely.
> - **Phone Hint:** The registration screen will use Google Phone Hint to simplify phone number entry.
> - **Account Separation:** Login and Registration are now separate activities for better UX.

## Proposed Changes

### [Authentication & Security]

#### [MODIFY] [ZetFlixCryptoUtils.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/utils/ZetFlixCryptoUtils.kt)
- Ensure all required keys are documented and handled consistently.

#### [MODIFY] [ZetFlixAuthPrefs.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/auth/ZetFlixAuthPrefs.kt)
- Add methods for normalized credential access.

#### [MODIFY] [BiometricAuthenticator.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/utils/BiometricAuthenticator.kt)
- Add `isBiometricAvailable(context)`, `isFingerprintEnabled(context)`, and `setFingerprintEnabled(context, enabled)`.
- Clean up initialization logic.

#### [NEW] [BiometricSetupDialog.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/auth/BiometricSetupDialog.kt)
- Implement the `show(context, onEnable, onSkip)` dialog.

---

### [Login & Register Screens]

#### [MODIFY] [ZetFlixLoginActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/auth/ZetFlixLoginActivity.kt)
- Refactor to handle **only** login logic.
- Implement identifier normalization (email or phone).
- Add fingerprint login button integration.
- Add debug logs with tag `ZetFlixAuthDebug`.

#### [NEW] [ZetFlixRegisterActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/auth/ZetFlixRegisterActivity.kt)
- Implement registration logic.
- Normalize input: Gmail-only email, digit-only phone, trimmed password.
- Integrate Google Phone Hint.
- Show `BiometricSetupDialog` upon success.
- Add debug logs with tag `ZetFlixAuthDebug`.

#### [MODIFY] [activity_zetflix_login.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/activity_zetflix_login.xml)
- Update to only show login-related fields.

#### [NEW] [activity_zetflix_register.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/activity_zetflix_register.xml)
- Create new layout for registration.

---

### [Account & Session]

#### [MODIFY] [SettingsAccount.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsAccount.kt)
- Overhaul to match ZetFlix account screen design.
- Show username (email prefix), masked phone, and fingerprint toggle.
- Implement logout with confirmation.

#### [MODIFY] [ZetFlixSessionManager.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/utils/ZetFlixSessionManager.kt)
- Ensure 7-day session expiry logic is correct.
- Update `logout` to clear all sensitive data while preserving user data (bookmarks, etc.).

#### [MODIFY] [AccountSelectActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/account/AccountSelectActivity.kt)
- Verify startup redirect logic.

#### [MODIFY] [HomeParentItemAdapterPreview.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeParentItemAdapterPreview.kt)
- Wire up the header avatar to navigate to `SettingsAccount`.

---

### [Manifest & Resources]

#### [MODIFY] [AndroidManifest.xml](file:///home/user/zetflix-clean/app/src/main/AndroidManifest.xml)
- Register `ZetFlixRegisterActivity`.

#### [MODIFY] [strings.xml](file:///home/user/zetflix-clean/app/src/main/res/values/strings.xml)
- Add any missing strings for the new UI and biometric setup.

---

### [Loading Screen]

#### [MODIFY] [ZetFlixLoadingActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/setup/ZetFlixLoadingActivity.kt)
- Ensure the 30-second timeout is robustly implemented.

## Verification Plan

### Automated Tests
- Build the app using `./gradlew :app:assembleDebug`.

### Manual Verification
1. **Registration:**
   - Test with valid/invalid Gmail addresses.
   - Test with phone number and Google Phone Hint.
   - Verify biometrics setup dialog appears.
2. **Login:**
   - Test login with email, phone (with/without country code), and fingerprint.
   - Verify session expiry by manually adjusting the stored timestamp.
3. **Account:**
   - Verify data masking and avatar display.
   - Test enabling/disabling fingerprint.
   - Test logout and ensure bookmarks/downloads are preserved.
4. **App Startup:**
   - Verify redirects for unauthenticated or expired sessions.
