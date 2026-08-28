# Implementation Plan - Fix IDE Warnings (Post-TV Cleanup)

This plan addresses several IDE warnings introduced or revealed by the recent cleanup of TV/Emulator specific logic. The fixes include removing unused imports, addressing unused parameters, and improving performance by using sequences.

## User Review Required

> [!NOTE]
> Most changes are purely stylistic or related to code health. No functional changes are expected.

## Proposed Changes

### App Module

#### [MODIFY] [SettingsGeneral.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsGeneral.kt)
- Remove unused import: `android.content.Context`.
- Remove unused exception parameter `e` in `catch` block.

#### [MODIFY] [AccountSelectActivity.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/account/AccountSelectActivity.kt)
- Remove unused import: `com.lagradost.cloudstream3.ui.settings.Globals.isLayout`.
- Add missing trailing comma.
- Add clarifying parentheses to a complex boolean expression in `skipStartup` initialization.
- Use named parameter for boolean literal.

#### [MODIFY] [HomeChildItemAdapter.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeChildItemAdapter.kt)
- Remove unused import: `com.lagradost.cloudstream3.ui.settings.Globals.isLayout`.
- Annotate unused parameter `isFirstItem` with `@Suppress("UNUSED_PARAMETER")`.

#### [MODIFY] [HomeParentItemAdapter.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeParentItemAdapter.kt)
- Remove unused import: `com.lagradost.cloudstream3.ui.settings.Globals.PHONE`.

#### [MODIFY] [LibraryFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/library/LibraryFragment.kt)
- Remove unused import: `android.app.Activity`.
- Remove redundant qualifier `BaseFragment`.

#### [MODIFY] [PluginsFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/settings/extensions/PluginsFragment.kt)
- Remove redundant qualifier `BaseFragment`.
- Add missing trailing comma.
- Convert collection call chain to `Sequence`.
- Add line break before `object : SearchView.OnQueryTextListener`.

#### [MODIFY] [SettingsPlayer.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsPlayer.kt)
- Use named parameter for boolean literal.
- Add missing trailing comma.
- Convert collection call chain to `Sequence`.

#### [MODIFY] [SettingsUI.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsUI.kt)
- Replace `enumValues` with `enumEntries`.
- Convert collection call chain to `Sequence`.
- Rename shadowed `it` parameter.
- Add missing trailing comma.
- Move lambda out of parentheses.

#### [MODIFY] [SingleSelectionHelper.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/utils/SingleSelectionHelper.kt)
- Remove unused import: `com.lagradost.cloudstream3.ui.settings.Globals.PHONE`.
- Suppress unused parameters `poster` and `tvOptions`.
- Annotate unused function `showNginxTextInputDialog` with `@Suppress("UNUSED")` (or remove if preferred).
- Add missing trailing comma.
- Add line break before `o`.
- Use foldable `if-then` (replace with `?.let`).
- Use named parameter for boolean literal.

### Library Module

#### [MODIFY] [MainAPI.kt](file:///home/user/zetflix-clean/library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt)
- Fix redundant qualifier for `Score`.
- Remove unused imports (will check manually).

## Verification Plan

### Automated Tests
- Run `analyze_file` on modified files to ensure warnings are resolved.

### Manual Verification
- Review diffs for logic consistency.
