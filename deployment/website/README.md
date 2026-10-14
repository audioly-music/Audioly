# Audioly website

Static landing page, addon documentation, and Android party invites. Served by Caddy on the Audioly Ubuntu VPS.

- Website: https://audioly.me
- Addon guide: https://audioly.me/docs/
- Party API: https://api.audioly.me
- Source: deployment/website/public

## Preview

```sh
python -m http.server 8766 --directory deployment/website/public
```

Open http://localhost:8766. Production Caddy rewrites /invite/CODE to invite.html; Python's static server does not implement that rewrite. No frontend build or Node service is required.

## Page behavior

The poster hero uses a generated headphone cutout, local fonts, and HTML typography. Download and GitHub buttons are visible on the first screen. The page includes original animated demo lyrics, scroll-driven perspective effects, real Audioly app screens, platform download tabs, installation FAQs, and personal author links.

Motion respects reduced-motion preferences. The lyrics preview has an explicit play/pause control; visitors who request reduced motion can start it themselves. Decorative motion can be paused from the footer. There is no audio autoplay or microphone access.

Download links have working static fallbacks for the public 2.0.0 release. When GitHub's latest-release API succeeds, the page updates available matching assets and version labels. All 16 files from the current release are linked, including Intel and Apple Silicon Mac packages. Public Android downloads install as Audioly.

The addon documentation includes searchable sections, copyable code examples, endpoint schemas, quality negotiation, immersive formats, downloads, testing, and troubleshooting. Its examples describe Audioly's existing addon implementation. Example media domains must be replaced with actual reachable media.

## Publish

Back up the current HTML, CSS, and JavaScript. Upload and copy public files into /var/www/audioly, including assets and docs. Preserve invite.html and .well-known/assetlinks.json. Keep the API reverse proxy and party invite rewrite configured. Static content changes do not require restarting Caddy.

Legacy Dev APK URLs redirect to the public production APK. Android app-link verification retains the production and original Dev signing certificates.

See ASSET-GENERATION.md for the hero's generation prompt. Font licenses are included beside their font files.
