# Typography and Font Consistency Improvement Plan

Address font inconsistencies, anomalies, and messy appearance by standardizing typography across the application.

## User Review Required

> [!IMPORTANT]
> This plan involves moving hardcoded font sizes to centralized styles and dimensions. This might slightly alter the look of some screens as we consolidate similar but slightly different sizes (e.g., merging 14sp and 15sp into a single body text size).

## Proposed Changes

### [Theme & Styles]

#### [MODIFY] [res/values/dimens.xml](file:///home/user/zetflix-clean/app/src/main/res/values/dimens.xml)
- Add standard text size dimensions based on Material3 guidelines but adapted to the current app's look.
  - `text_size_display`: 30sp
  - `text_size_h1`: 26sp
  - `text_size_h2`: 24sp
  - `text_size_h3`: 20sp
  - `text_size_body`: 16sp
  - `text_size_body_small`: 14sp
  - `text_size_caption`: 12sp
  - `text_size_overline`: 10sp

#### [MODIFY] [res/values/styles.xml](file:///home/user/zetflix-clean/app/src/main/res/values/styles.xml)
- Define `TextAppearance` styles for the application, inheriting from `TextAppearance.Material3`.
- Update `AppTheme` to set these text appearances as defaults where possible.
- Fix `AppTextViewStyle` and other base styles to use these dimensions.

### [Layouts]

#### [MODIFY] [res/layout/fragment_home_head.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_home_head.xml)
- Fix the `20dp` font size anomaly and change it to a standard `sp` dimension.

#### [MODIFY] [Audit and Bulk Update Layouts]
- Systematically replace hardcoded `android:textSize="XXsp"` with `android:textAppearance="?attr/textAppearanceXXX"` or `android:textSize="@dimen/text_size_XXX"`.
- Focus on high-traffic screens first:
  - `fragment_result.xml`
  - `fragment_home_head.xml`
  - `player_custom_layout.xml`
  - `search_result_grid.xml`

## Verification Plan

### Manual Verification
- Deploy the app and inspect major screens (Home, Search, Result, Player) to ensure text is legible, consistently sized, and scales correctly with system font size settings.
- Verify that headers are distinct from body text and follow a logical hierarchy.
- Ensure no text is clipped due to scaling issues.
