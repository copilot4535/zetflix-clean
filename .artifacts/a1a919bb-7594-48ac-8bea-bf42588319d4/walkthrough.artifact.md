# Refactored "My Account" Dedicated Page

I have moved the "My Account" section from the Settings menu into a dedicated page, which is now accessible via a new avatar icon on the top right of the Home screen.

## Changes Made

### Home Screen Header
- **[fragment_home.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_home.xml)**: Added a `ShapeableImageView` (avatar) to the sticky header, aligned to the top right.
- **[HomeFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeFragment.kt)**:
    - Added logic to load the user's avatar with a dynamic background color based on their username.
    - Set a click listener on the avatar to navigate to the new Account page.

### Dedicated Account Page
- **[NEW] [fragment_account.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_account.xml)**: Created a new layout for the account page, featuring:
    - A toolbar with a back button.
    - A profile info card (avatar, username, email).
    - A settings card for biometric/app lock.
    - A logout action card.
- **[NEW] [AccountFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/settings/AccountFragment.kt)**: Implemented the logic for the account page, including loading user data, toggling biometric settings, and performing logout.
- **[mobile_navigation.xml](file:///home/user/zetflix-clean/app/src/main/res/navigation/mobile_navigation.xml)**: Added the new account fragment to the navigation graph (`navigation_account`).

### Settings Menu Cleanup
- **[fragment_settings_unified.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_settings_unified.xml)**: Removed the "Account & Security" section.
- **[SettingsFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsFragment.kt)**: Removed all account-related logic and biometric callbacks.

### Home Screen Header Refinements
- **Improved Vertical Centering**:
    - Fixed a logic error in `UIHelper.kt` where top bar heights weren't correctly accounting for the status bar inset.
    - The sticky header now dynamically adjusts its height to `75dp + statusBarHeight`.
    - Both the ZetFlix logo and avatar are now perfectly centered within the **75dp strip below the system status bar**, regardless of device-specific status bar sizes.
- **Avatar Size**: Maintained the account avatar icon size at 30dp x 30dp.

## Verification Results

### Automated Tests
- Build successful (`app:assembleDebug`).

### Manual Verification
- Navigated to Settings: Confirmed the Account section is gone.
- Navigated to Home: Verified the new avatar icon is in the top right.
- Clicked Avatar: Confirmed it opens the dedicated Account page.
- Back Button: Verified returning from Account page to Home works as expected.
- Account Actions: Logged out and verified biometric toggle from the new page.
