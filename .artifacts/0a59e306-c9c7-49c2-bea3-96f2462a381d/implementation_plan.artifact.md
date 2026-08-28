# Remove Internal Storage Details Bar from Downloads Screen

This plan describes how to remove the internal storage details bar from the Downloads screen as requested. The storage info bar shows used, app, and free space, which is deemed unnecessary.

## Proposed Changes

### Layout Changes

#### [MODIFY] [fragment_downloads.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_downloads.xml)
- Set `android:visibility="gone"` for `download_storage_appbar`.
- Update `android:nextFocusUp` of `download_list` to avoid referencing the hidden bar, or ensure it doesn't cause issues. Since `download_appbar` is the parent of `download_storage_appbar`, I will set `download_appbar` visibility to `gone` as well if it only contains the storage info.

### UI Logic Changes

#### [MODIFY] [DownloadFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/download/DownloadFragment.kt)
- Remove `setLayoutWidth` extension function.
- Remove observers for `availableBytes`, `usedBytes`, and `downloadBytes`.
- Remove `updateStorageInfo` helper method.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure the project still builds correctly after removing code references.

### Manual Verification
- Deploy the app and navigate to the Downloads screen.
- Verify that the storage details bar is no longer visible at the top.
- Verify that multi-delete mode still works correctly (the `download_delete_appbar` should still show up when items are selected).
