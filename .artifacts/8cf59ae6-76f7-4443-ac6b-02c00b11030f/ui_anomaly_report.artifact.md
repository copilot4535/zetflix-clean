# UI Design Anomaly & Non-Uniformity Report

This report documents inconsistencies, design breaks, and maintainability issues found during a deep search of the ZetFlix UI implementation.

## 1. Color Palette Inconsistencies

The project defines a semantic color palette in `colors.xml`, but it is frequently bypassed or used inconsistently.

### Multiple Shades of Black/Near-Black
| Resource Name | Hex Value | Intended Use |
| :--- | :--- | :--- |
| `zetflix_background` | `#0b0b0f` | Main background |
| `primaryBlackBackground` | `#000000` | Used in `fragment_result.xml` and `LoadedStyle` |
| `darkBar` | `#121212` | General dark bar |
| `zetflix_surface_light` | `#121218` | Elevated surfaces |

> [!WARNING]
> Using `#000000` alongside `#0b0b0f` creates visual "crushing" and inconsistent depth across different screens.

### Variant Reds (Accent Colors)
| Context | Hex Value / Resource |
| :--- | :--- |
| `zetflix_accent` | `#e50914` |
| `colorPrimaryDark` | `#b0070f` |
| `colorOngoing` | `#F53B66` |
| `adultColor` | `#FF6F63` |
| `colorTestFail` | `#ea596e` |
| Hardcoded in Layouts | `#e50914` (Frequent) |

### Hardcoded Colors in Layouts
Over 30 instances of hardcoded hex codes were found in layout files instead of using theme attributes (`?attr/...`) or color resources (`@color/...`).
- **White**: `#ffffff`, `#B3FFFFFF` (70%), `#E6FFFFFF` (90%)
- **Red**: `#e50914`
- **Transparent White**: `#80ffffff`

## 2. Dimension & Grid Violations

The project defines a `grid_` system (4dp base), but many layouts ignore it in favor of "magic numbers".

### Margin & Padding Inconsistency
- `grid_1` is `8dp`, but `result_padding` and `loading_margin` are `15dp`.
- `SettingsItem` height is hardcoded to `56dp`, but `nav_view_height` is `70dp`.
- `fragment_result.xml` uses `paddingStart="15dp"`, while `sticky_header` uses `paddingHorizontal="16dp"`.

### Corner Radius Anomaly
Corner radii are not standardized, leading to a "patchwork" look for cards and buttons:
- `rounded_image_radius`: `10dp`
- `rounded_button_radius`: `12dp`
- `card_corner_radius`: `2dp` (Virtually square, inconsistent with others)
- `ZetFlixSettingsCard`: `12dp`
- `loading_radius`: `3dp`
- `VideoButton`: `3dp`
- `RoundedSelectableButton`: `100dp` (Pill shape)

## 3. Style & Maintenance Issues

### Hardcoded Loading States
In `fragment_home.xml`, the shimmer loading state uses brittle `translationX` values (`-164dp`, `164dp`) and hardcoded card sizes (`125dp x 200dp`). This will break on different aspect ratios or screen widths.

### Resource Syntax Error
In `styles.xml` (Line 389), a stray single quote was found:
```xml
<item name="castStopButtonDrawable">@drawable/cast_ic_mini_controller_stop</item>'
```

### Redundant Declarations
Styles like `ChipFilled` redeclare both `android:fontFamily` and `fontFamily` with the same value, adding unnecessary noise.

### Logic Errors
`DubButton` (in `styles.xml`) sets `textColor` to `?attr/primaryGrayBackground`. Since `primaryGrayBackground` is `#0b0b0f` (dark), the text will likely be invisible on the dark UI background.

## 4. Proposed Uniformity Standards

To resolve these anomalies, the following steps are recommended:

1.  **Centralize Colors**: Consolidate similar shades of black and red into single semantic definitions.
2.  **Enforce Grid**: Replace all `15dp`, `10dp`, etc., with `grid_` multiples (e.g., `16dp`, `12dp`).
3.  **Standardize Radii**: Choose 2-3 standard radii (e.g., `4dp` for small, `8dp` for medium, `12dp` for large).
4.  **Theme Attributes**: Migrate layout files to use `?attr/textColor` and `?attr/colorPrimary` instead of hex codes.
5.  **Responsive Shimmers**: Replace hardcoded translations in `ShimmerFrameLayout` with `ConstraintLayout` or `LinearLayout` with weights.
