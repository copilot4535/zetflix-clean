# ZetFlix Player Architecture Implementation Plan

Model the music player architecture on Spotify's 2026 direction (inline lyrics, mini player above nav) while maintaining a premium ZetFlix (Netflix-inspired) visual identity.

## User Review Required

> [!IMPORTANT]
> - The **ZetFlix Red (`#E50914`)** will be used exclusively for primary actions and active states to maintain a premium feel.
> - The **Mini Player** will be positioned directly above the bottom navigation bar.
> - **Inline Lyrics** will show only the currently active line beneath the artwork, while a preview card remains below for context.

## Proposed Changes

### 🎨 Theme and Colors

Standardize the palette to the requested ZetFlix premium colors.

#### [MODIFY] [colors.xml](file:///home/user/zetflix-clean/app/src/main/res/values/colors.xml)
- Update `zetflix_accent` to `#E50914`.
- Update `zetflix_background` to `#0B0B0F`.
- Add/Update `zetflix_surface` to `#141419`.
- Add `zetflix_surface_2` as `#1B1B22`.
- Add `zetflix_muted` as `#6F6F78`.
- Add `zetflix_divider` as `#25252C`.
- Update `zetflix_text_secondary` to `#A0A0A8`.

#### [MODIFY] [bg_circle_white.xml](file:///home/user/zetflix-clean/app/src/main/res/drawable/bg_circle_white.xml)
- Rename or update to `bg_circle_accent.xml` and use `zetflix_accent`.

### 📱 Mini Player

Update the mini player to match the compact 2026 Spotify-like layout.

#### [MODIFY] [view_music_mini_player.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/view_music_mini_player.xml)
- Set height to `72dp`.
- Use `zetflix_surface` for background.
- Set album art radius to `8dp`.
- Update colors for title (white) and artist (`#A0A0A8`).
- Update play/pause button to use `zetflix_accent`.

### 🎵 Full Player (Now Playing)

Restructure the full player for a cinematic, premium experience.

#### [MODIFY] [fragment_music_player.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/fragment_music_player.xml)
- Restructure hierarchy: Artwork -> **Active Lyric Line** -> Metadata -> Progress -> Controls -> Lyrics Card.
- Ensure the artwork occupies ~80-88% of the width.
- Configure `music_player_live_lyric` for better prominence.

#### [MODIFY] [custom_music_controls.xml](file:///home/user/zetflix-clean/app/src/main/res/layout/custom_music_controls.xml)
- Update `exo_progress` colors: played (`zetflix_accent`), unplayed (`#55555F`), thumb (white).
- Update controls color logic: active states = `zetflix_accent`, default = white/gray.

### 🧠 Logic and Transitions

#### [MODIFY] [MusicPlayerFragment.kt](file:///home/user/zetflix-clean/app/src/main/java/com/lagradost/cloudstream3/ui/music/MusicPlayerFragment.kt)
- Refine the active lyric line update logic.
- Implement dynamic darkened background derived from artwork.
- Handle mini player to full player expansion transition.

## Verification Plan

### Manual Verification
- Deploy the app and navigate to the Music section.
- Verify the **Mini Player** is above the bottom nav and has the correct colors.
- Expand the **Full Player** and check the hierarchy (Artwork -> Lyric -> Metadata).
- Play a song with lyrics and verify the **Active Lyric Line** updates in real-time.
- Check the **Progress Bar** and **Controls** for the correct ZetFlix Red accents.
