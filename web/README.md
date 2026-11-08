# Audioly Web

Live player: https://open.audioly.me

The React player is served as static files from Audioly's VPS. YouTube playback and signed-in integrations require the Audioly companion extension. Chromium users load the unpacked extension from the download on `/extension`; Firefox's unsigned build is a temporary add-on. Mobile Safari/iOS cannot use this playback bridge. This is not an extension-free player.

## Build for the VPS

Run from `web/` with Node 22+:

```powershell
corepack pnpm install --frozen-lockfile
$env:VITE_PARTY_SERVER_URL="https://api.audioly.me"
corepack pnpm typecheck
corepack pnpm test
corepack pnpm --filter @audioly/extension icons
corepack pnpm build:site
```

Deploy `app/dist/` with an SPA fallback to `index.html`. Serve `/assets/`, `/models/` and `/downloads/` as real files (missing files must return 404). Build locally; the 1 GB VPS only serves the output. Caddy handles HTTPS. Preserve the existing main website and party backend.

The extension trusts `https://open.audioly.me` and the local development origin. Its public development key pins Audioly's own Chromium extension ID; no private key is retained. Build and deploy the site and extension together if that public key changes.

The party backend accepts `audioly.v1` and `audioly.token.<token>` WebSocket subprotocols, with `https://open.audioly.me` in `JAM_ALLOWED_ORIGINS`. Native clients retain bearer-header authentication. Do not put tokens in URLs.

## Upstream development reference

The web player and its companion browser extension. The plan, and the decisions behind it, are in [`docs/web-app-build-prompt.md`](../docs/web-app-build-prompt.md).

| Package | What it is |
|---|---|
| `app/` | The site: Vite, React 19, TypeScript, React Router, TanStack Query, Zustand, Tailwind CSS v4 |
| `extension/` | Manifest V3 extension for Chromium and Firefox, built with WXT |
| `shared/` | The page ↔ extension bridge contract and client, used by both |
| `docs/` | `apple-music-dom.md` (the design measurements), `kira-notes.md` (how the reference YouTube web client plays), `youtube-playback.md`, `youtube-signin.md`, `queue-and-player.md`, `lyrics.md`, `spotify-and-discord.md` |
| `fixtures/` | Real captured responses for parser tests (see its README) |
| `scripts/` | `capture-fixtures.mjs` |

## Setup

Node 22 or newer. pnpm comes through corepack, so nothing needs a global install:

```bash
corepack pnpm --dir web install
```

## Run the site

```bash
corepack pnpm --dir web/app dev
```

It serves on `http://localhost:5173`, and only there (`strictPort`): the extension trusts that exact origin.

## Run the extension

Build both targets:

```bash
corepack pnpm --dir web/extension build
```

- **Chrome, Edge, Brave:** `chrome://extensions` → Developer mode → *Load unpacked* → `web/extension/.output/chrome-mv3`. The id is always `pkddffeebcmhhanjcigilehaeonkkfih`, pinned by the manifest `key` (`extension/dev-key/`), so the site finds it without configuration.
- **Firefox:** `about:debugging#/runtime/this-firefox` → *Load Temporary Add-on* → `web/extension/.output/firefox-mv3/manifest.json`.

To offer the extension as a download from the site's Extension page, package it:

```bash
corepack pnpm --dir web/extension package
```

This builds both targets and writes three files to `app/public/downloads/` (git-ignored):

- `audioly-extension-chrome.zip` and `audioly-extension-firefox.zip`. Each holds a single folder (`audioly-extension-<browser>/`), so extracting it in place leaves one folder to load unpacked.
- `extension.json`, with the version. The site compares it with the installed extension's version and shows **Update** in the sidebar and an update row on the Extension page when the zip is newer. Bump `version` in `extension/package.json` to publish an update.

`VITE_EXTENSION_CHROME_URL`, `VITE_EXTENSION_FIREFOX_URL` and `VITE_EXTENSION_MANIFEST_URL` override those links, for example to point at store listings.

The toolbar icons (`extension/public/icon/`) are rendered from `app/public/favicon.svg` by `corepack pnpm --dir web/extension icons`.

Then open `http://localhost:5173/extension`. It shows "Installed", with the version and the transport: **Direct** on Chromium (`externally_connectable`), **Page relay** on Firefox (content script).

## Configuration

| Variable | Where | Default |
|---|---|---|
| `AUDIOLY_SITE_ORIGINS` | extension build | `https://open.audioly.me,https://open.audioly.me,http://localhost:5173` (store builds leave out localhost). Comma-separated origins the extension serves; replaces the default |
| `AUDIOLY_STORE_BUILD=1` | extension build | Leaves out the dev `key` for a Chrome Web Store upload |
| `VITE_EXTENSION_ID_CHROMIUM` | app (`app/.env.local`) | The pinned dev id. Set it for a store-built extension |
| `VITE_ANDROID_APP_URL` | app | The GitHub releases page |
| `VITE_PARTY_SERVER_URL` | app (`app/.env.local`, Pages env) | None. The Listen Together server; `vite build` fails without it |

## Deploy (Cloudflare Pages)

| Setting | Value |
| --- | --- |
| Root directory | `web` |
| Build command | `pnpm build:site` |
| Output directory | `app/dist` |
| Environment | `NODE_VERSION=22`, `VITE_PARTY_SERVER_URL` |

`build:site` packages the extension first, so each deploy publishes the zips and `downloads/extension.json` with the version from `web/extension/package.json`. The Extension page polls that file and offers the update when it's newer than the installed one. Bump that version to make an update show.

## Checks

```bash
corepack pnpm --dir web -r typecheck
corepack pnpm --dir web/app build
```
