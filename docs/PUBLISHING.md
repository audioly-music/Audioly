# Publish Audioly on GitHub

This folder contains the prepared Audioly source. Its local Git configuration already points to `https://github.com/audioly-music/Audioly`. No code was pushed during this preparation.

1. Confirm that you own the configured repository and that it exists on GitHub. If creating it, do not generate another README or license there.
2. Review the staged files and `NOTICE.md`. Keep `local.properties`, `.env` files and signing material out of Git.
3. Commit and push from this folder:

```bash
git add .
git diff --cached --stat
git commit -m "Prepare Audioly public source"
git remote -v
git push -u origin main
```

If publishing to a different repository, update `origin` with `git remote set-url origin YOUR_REPOSITORY_URL` before pushing. GitHub Desktop can publish the local repository instead. Select **Public** only when you are ready to share all included source.

## Release checklist

- Run Android tests and build checks; test playback, downloads, login and parties on real devices.
- Set the repository description to: “A personal Android music app with playlist integrations, lyrics, downloads and shared listening.”
- Set the website to `https://audioly.me`; useful topics: `android`, `kotlin`, `jetpack-compose`, `music-player`.
- Upload `docs/images/social-preview.png` in GitHub Settings → General → Social preview.
- Build and sign the production APK with your own private key. Attach APKs and checksums to a GitHub Release rather than committing binaries.
- Update README release links once the repository URL exists.
- Configure optional API keys as repository secrets, never source text.

## Branding compatibility

Visible branding, generated artifacts and new internal identifiers use Audioly. `NOTICE.md` preserves original attribution. `LegacyStorageMigration.kt` and Android backup exclusions retain old identifiers only to recognize existing local data and previous server settings; deleting those strings would break compatibility. Original source notices remain intact.

Screenshots show real app UI with Sunflower (Spider-Man: Into the Spider-Verse), its search results and related English pop tracks. They are documentation assets, not bundled music or endorsements. Review them before publishing; device status bars are present in the raw captures.

## Source preparation checks

The Android development build and 880 unit tests passed during preparation. The Android build also compiles the renamed native libraries. Backend Go tests were not run locally.

Private-key and token-pattern checks found no personal credentials in the publishable files. The pre-existing Google web-client API key in `PoTokenWebView.kt` is a client constant used by the BotGuard integration, not a personal deployment secret. Signing files, local configuration, generated builds and environment files are excluded. Review any future additions before publishing.
