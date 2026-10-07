# Publish Audioly on GitHub

This folder is a clean source snapshot. No remote or GitHub repository has been created.

1. Create an empty GitHub repository named `Audioly` under your account. Do not generate a README or license there.
2. Review the staged files and `NOTICE.md`. Keep `local.properties`, `.env` files and signing material out of Git.
3. Commit and push from this folder:

```bash
git add .
git diff --cached --stat
git commit -m "Prepare Audioly public source"
git remote add origin https://github.com/YOUR_USERNAME/Audioly.git
git push -u origin main
```

GitHub Desktop can publish the local repository instead. Select **Public** only when you are ready to share all included source.

## Release checklist

- Run Android tests and build checks; test playback, downloads, login and parties on real devices.
- Set the repository description to: “A personal Android music app with playlist integrations, lyrics, downloads and shared listening.”
- Set the website to `https://audioly.me`; useful topics: `android`, `kotlin`, `jetpack-compose`, `music-player`.
- Upload `docs/images/social-preview.png` in GitHub Settings → General → Social preview.
- Build and sign the production APK with your own private key. Attach APKs and checksums to a GitHub Release rather than committing binaries.
- Update README release links once the repository URL exists.
- Configure optional API keys as repository secrets, never source text.

## Branding compatibility

Visible branding, generated artifacts and new internal identifiers use Audioly. `NOTICE.md` preserves original attribution. `LegacyStorageMigration.kt` retains old identifiers only to recognize existing local data and previous server settings; deleting those strings would break compatibility. Original source notices remain intact.

Screenshots show real app UI with English pop content. They are documentation assets, not bundled music or endorsements. Review them before publishing; device status bars are present in the raw captures.
