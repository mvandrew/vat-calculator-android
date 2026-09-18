# VAT Calculator icon set

The editable vector sources and Play asset in this directory are derived from
the approved Figma file:

- Master Artwork: node `5:2`;
- Play Store Icon: node `5:93`;
- Adaptive Background: node `5:56`;
- Adaptive Foreground: node `5:59`;
- Adaptive Monochrome: node `5:82`.

Source file: <https://www.figma.com/design/G33nxbYnjAAk9gNP6LXH1H/VAT-Calculator>

## Files

- `app-icon.svg` — canonical 1024 × 1024 text-free artwork;
- `app-icon-background.svg` — 108 × 108 adaptive background;
- `app-icon-foreground.svg` — transparent 108 × 108 adaptive foreground;
- `app-icon-monochrome.svg` — transparent single-color themed-icon source;
- `app-icon-round-fallback.svg` — circular crop used only for the legacy
  `roundIcon` raster fallback; it does not alter the adaptive source layers;
- `play/play-icon-512.png` — 512 × 512 RGBA Google Play asset;
- `preview/` — mask, theme and small-size validation renders.

Figma frames have a white editor background. That frame fill is intentionally
excluded from the foreground and monochrome SVG files; only their approved
child components are exported.

The Android runtime does not consume these SVG files directly. Equivalent
VectorDrawable resources live in `app/src/main/res/drawable`, while raster
fallbacks for API 24–25 live in the density-specific `mipmap` directories.
