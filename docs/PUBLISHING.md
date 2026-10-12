# Publish Audioly on GitHub

This folder contains the Audioly source. Its Git remote points to `https://github.com/audioly-music/Audioly`.

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
- Set the repository description to: “A personal Android and desktop music app with playlist integrations, lyrics and shared listening.”
- Set the website to `https://audioly.me`; useful topics: `android`, `kotlin`, `jetpack-compose`, `music-player`.
- Upload `docs/images/social-preview.png` in GitHub Settings → General → Social preview.
- Build and sign the production APK with your own private key. Attach APKs and checksums to a GitHub Release rather than committing binaries.
- Update README release links once the repository URL exists.
- Configure optional API keys as repository secrets, never source text.

## Branding compatibility

Visible branding, generated artifacts and new internal identifiers use Audioly. `NOTICE.md` preserves original attribution. `LegacyStorageMigration.kt` and Android backup exclusions retain old identifiers only to recognize existing local data and previous server settings; deleting those strings would break compatibility. Original source notices remain intact.

Screenshots show real app UI with Sunflower (Spider-Man: Into the Spider-Verse), its search results and related English pop tracks. They are documentation assets, not bundled music or endorsements. The README captures were refreshed with the phone status bar hidden; the original phone setting was restored afterward.

## Source preparation checks

The Android suite passed 976 unit tests. The signed production APK builds with package `com.music.audioly`, launcher name **Audioly**, and version 2.0.0. The Android build also compiles the renamed native libraries. Backend Go tests were not run locally.

Desktop passed 342 tests, the shared core passed 43 tests, and the shared UI passed 34 tests: 1,395 passing tests in total. Desktop compilation passed on Windows. Live Spotify account access still needs a device check; the automated tests do not verify a personal account session.

The live Genius provider test is opt-in with `AUDIOLY_LIVE_TESTS=1`.
Normal CI runs its four offline parsing tests and reports the live test as skipped,
so provider outages or runner blocking do not break the deterministic test suite.

The Windows app image and portable archive also build locally. Local packaging skips the Automix analyser and native Windows frame because this PC lacks MinGW/CMake/Ninja. The desktop workflow installs those tools and asserts that the native libraries are bundled. Linux and macOS packages require their workflow jobs; they were not built locally.

Version 2.0.0 ports the upstream desktop/shared modules and replaces the previous Spotify implementation. See DESKTOP.md and docs/SPOTIFY_INTEGRATION.md for platform differences.

Private-key and token-pattern checks found no personal credentials in the publishable files. The pre-existing Google web-client API key in `PoTokenWebView.kt` is a client constant used by the BotGuard integration, not a personal deployment secret. Signing files, local configuration, generated builds and environment files are excluded. Review any future additions before publishing.

## GitHub APK downloads

The release version and Android build number live in `version.properties`.
Use `python scripts/release_version.py bump` for the next patch, or add
`--part minor` / `--part major`. Minor and patch each range from 0 to 9:
2.0.9 becomes 2.1.0, and 2.9.9 becomes 3.0.0. Gradle and release CI reject 2.10.0.

After testing a release, commit the version change and release notes, then push
the matching tag (for example `v2.0.0`). The release workflow validates the tag,
builds the signed production APK and publishes `Audioly.apk` plus its checksum
to GitHub Releases. A manual workflow run builds artifacts without publishing.

The desktop workflow builds Windows installers/portable ZIP, Linux AppImage/deb/rpm,
and macOS DMG/PKG/ZIP for Apple Silicon and Intel. Tagged builds attach all 12 files
and a checksum manifest to the matching production release after every platform
passes. Manual desktop runs can also enable the publish input for an existing release.

### Retired Dev channel

Early Dev APKs had updates disabled; newer Dev builds poll GitHub Releases and
can show a normal update prompt. A release cannot force every existing installation
to change. Share the locally built Dev migration APK separately
with those users, or have them export a backup and install production directly.
The migration build retains `com.dev.audioly` and must use the original Dev
signing key; a fresh GitHub runner's debug key cannot update old Dev installs.
Build it with `gradlew.bat :app:assembleDevDebug` on the original signing machine.
Never upload that key. Do not place a Dev APK among the stable release assets.

Once installed, the migration build requires production 2.0.0 or newer and
provides backup, download/install and Open Audioly actions. It has no Continue
or Later action. Android still requires installation approval. Backups include
settings and listening history, not account sessions, downloaded audio or local
playlists. Keep old data until the move has been checked.

Use **Actions → Build Audioly release → Run workflow** to build the public APK. Configure these repository **Actions secrets** first:

- `ANDROID_KEYSTORE_BASE64`: the base64-encoded contents of your own release keystore.
- `ANDROID_KEYSTORE_PASSWORD`: its store password.
- `ANDROID_KEY_ALIAS`: the signing key alias.
- `ANDROID_KEY_PASSWORD`: the signing key password.

Keep the same keystore for every update. The workflow fails if secrets are missing; it never substitutes a debug key or uploads an unsigned APK. It builds `prodRelease`, verifies the package is `com.music.audioly`, verifies the launcher label is **Audioly**, and verifies the APK signature. Download the `Audioly-release` artifact and attach `Audioly.apk` and its checksum to your GitHub Release. GitHub users should download that APK from **Releases**.

The Android checks workflow runs development tests but does not upload development APKs. The retired Dev variant is now an **Audioly Upgrade** migration helper. Existing GitHub artifacts or release assets, if any, are not changed by these source edits; remove any old development APKs before publishing a release.
