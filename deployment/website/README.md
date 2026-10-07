# Audioly website

Static landing page and Android invitation routes, hosted on the Audioly Ubuntu VPS with Caddy.

Source: deployment/website/public
Public URL: https://audioly.me
API: https://api.audioly.me

## Preview

python -m http.server 8765 --directory deployment/website/public

Visit http://localhost:8765. Caddy provides the /invite/CODE rewrite in production; the plain development server does not.

## Publish

Archive the contents of public, including .well-known, upload to the VPS, and extract into /var/www/audioly. Files should be readable by Caddy. No build step or Node service is required.

The server configuration is deployment/native/Caddyfile. Validate it with caddy validate before reloading Caddy. The existing API reverse proxy must remain configured.

The landing page uses local assets and a canvas animation. Motion can be paused, respects reduced-motion preferences, and suspends when offscreen or the browser tab is hidden. No microphone access or audio autoplay is used.

screenshots.json supplies the real app images and captions. Keep private notifications and account details out of published screenshots. The downloads directory contains Audioly Dev v1.7 for ARM64, its SHA-256 checksum, and a corresponding Android source archive. APK signing keys and local credentials are excluded from the source archive.

/.well-known/assetlinks.json is served directly on both audioly.me and www.audioly.me without redirects. The current fingerprint is for the local debug signing key; release builds require the actual release certificate fingerprint.

/invite/CODE preserves optional custom-server query parameters and opens Audioly from a user-activated button. The app checks whether the party is active.

## Reference-style redesign

The landing page now uses a split hero, dark grid, interactive feature cards, build details and a three-step install guide. Audioly logo colors drive the palette. Scroll effects include one-time card reveals, a reading progress bar and hero parallax. Pause controls and reduced-motion preferences disable decorative motion. Screenshot capture and publishing remain paused by request.

Live browser verification passed at widths 360, 375, 768, 1024 and 1440; no horizontal overflow or JavaScript errors. Tested quality preview, translation toggle, crossfade slider, build details, motion controls and all download endpoints.

Published APK SHA-256: 9276dae7475478caaee195dce583fc61be20da4c5815b3da0518d5a649c40c4e
APK size: 83,724,556 bytes (79.8 MiB). Label: Audioly Dev. Package: com.dev.audioly. Android 8.0+, arm64-v8a. The VPS copy has the same hash as the verified local APK.
