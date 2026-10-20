# Audioly website

Static homepage, platform downloads, addon documentation, and Android party invites. Served by Caddy on the Audioly Ubuntu VPS.

- Website: https://audioly.me
- Downloads: https://audioly.me/downloads.html
- Addon guide: https://audioly.me/docs/
- Party API: https://api.audioly.me
- Source: deployment/website/public

## Preview

```sh
python -m http.server 8766 --directory deployment/website/public
```

Open http://localhost:8766. Production Caddy rewrites /invite/CODE to invite.html; Python's static server does not implement that rewrite. No frontend build or Node service is required.

## Page behavior

The dark homepage uses local Plus Jakarta Sans typography, the Audioly logo, an amber phone preview, horizontally scrolling Replay cards, a desktop preview, and artwork shelves. Download and GitHub buttons appear above the fold. Replay data and the desktop interface are clearly marked illustrations. Catalogue metadata comes from the Apple Music RSS feeds listed in assets/catalogue.json.

Original demo lyrics animate word by word. Motion respects reduced-motion preferences, with explicit play/pause controls. There is no audio autoplay or microphone access. The phone also has a subtle scroll-driven perspective effect.

The download page contains all 16 files from the public 2.0.0 release, including Android, Windows, Linux, and Intel/Apple Silicon Mac packages. Static links work without JavaScript. GitHub's latest-release API updates matching assets and version labels when available. GitHub statistics are real release download counts and repository stars; unavailable requests retain the static fallback.

Addon documentation includes searchable sections, copyable examples, endpoint schemas, quality negotiation, immersive formats, downloads, testing, and troubleshooting. Examples describe Audioly's existing addon implementation. Replace example media domains with actual reachable media.

## Publish and rollback

Package the contents of public, including hidden .well-known files. Extract into a fresh staging directory and verify index.html, invite.html, docs/index.html and .well-known/assetlinks.json before deployment. Archive the entire previous /var/www/audioly directory outside /var/www, then rename the staging directory to /var/www/audioly. This prevents obsolete pages and assets from remaining publicly accessible. Restore the archived root to roll back.

Keep Caddy's API reverse proxy, legacy APK redirects, and party invite rewrite configured. Replacing static files does not require restarting Caddy. Legacy Dev APK URLs redirect to the public production APK; app-link verification retains production and original Dev signing certificates.

The font license is included beside the font file.
