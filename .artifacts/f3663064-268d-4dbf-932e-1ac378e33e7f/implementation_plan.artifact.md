# Enhance Home Screen Logo Visibility

Improve the legibility and appearance of the "Zetflix" text logo on the home screen, ensuring it stands out against various hero banner backgrounds and transitions smoothly during scrolling.

## User Review Required

> [!NOTE]
> I am adding a "shadow" effect directly into the vector drawable of the logo. This is more efficient than adding it at runtime via software layers.
> I am also introducing a subtle top-down scrim (gradient) to the header area to ensure visibility even on very bright images.

## Proposed Changes

### [Home UI Components]

#### [MODIFY] [ic_zetflix_logo_text.xml](file:///home/user/zetflix-clean/app/src/main/res/drawable/ic_zetflix_logo_text.xml)
- Add a shadow group that duplicates the logo paths with a small offset and semi-transparent black fill.

#### [NEW] [home_header_scrim.xml](file:///home/user/zetflix-clean/app/src/main/res/drawable/home_header_scrim.xml)
- Create a new gradient drawable (Transparent to Semi-Transparent Black) to serve as a permanent background for the header.

#### [MODIFY] [fragment_home.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_home.xml)
- Wrap the `sticky_header` content or add a background view to use the new scrim.
- Update the `ImageView` to ensure it handles the shadowed vector correctly.

#### [MODIFY] [HomeFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeFragment.kt)
- Update the `onScrolled` logic to transition from the subtle scrim to the solid background color instead of starting from absolute transparency.

## Verification Plan

### Automated Tests
- N/A (UI visual changes)

### Manual Verification
1. Launch the app and observe the logo on the home screen.
2. Verify it is clearly visible against the hero banner (especially if the banner is bright).
3. Scroll down and verify the header background transitions smoothly to the solid primary color.
4. Verify the avatar also remains visible and well-aligned.
