# Playlist import

Open **Library → Import from Spotify or Apple Music**. Sign in to Audioly first; the
result is saved as a private playlist in that account's YouTube Music library.

1. Paste a full Spotify or Apple Music playlist URL.
2. For Spotify, choose **Connect Spotify** for complete/private playlists. Sign in
   on Spotify's page and choose **Use this account**. Audioly stores the session on
   this device using the existing Spotify Canvas setting. **Disconnect Spotify**
   removes that connection (including its use by Canvas).
3. Read the playlist and check any warning about incomplete public previews.
4. Choose **Find matching songs**, then review titles and artists. Deselect any
   incorrect matches. Songs without a reliable match are excluded.
5. Name the playlist and choose **Save**. The source playlist is not modified.

## Scope and limits

- Spotify without sign-in uses its public embed preview, which can be incomplete.
  The app always displays that limitation rather than claiming it imported the
  full playlist.
- Spotify with sign-in reads successive pages of the playlist using the web
  player's interface. The signed-in account must have access to the source.
- Apple Music supports public playlist pages only. Private Apple Music library
  access is not implemented. Make a playlist public to use this route.
- Up to 500 source tracks per import. Podcasts and unavailable items are skipped
  with a warning. Matching uses the existing title/artist/duration/version matcher.
- Import transfers playlist metadata and finds playable matches; it does not
  transfer audio files or guarantee the same recording is available.
- Both providers' web formats can change. A failed or expired Spotify session is
  reported rather than silently falling back to an incomplete preview.
- Cancellation before Save does not create a playlist. If a save cannot be
  confirmed because the network drops, check Library before retrying.

## Validation

Run `gradlew.bat :app:testDevDebugUnitTest :app:assembleDevDebug` with JDK 17.
Parser tests cover provider URL validation, preview warnings, track metadata,
Apple page completeness, and unsupported/private responses. Optionally set
`AUDIOLY_APPLE_PLAYLIST_FIXTURE` to a captured public Apple playlist HTML file to
validate a real response without making live network requests in tests.
`AUDIOLY_SPOTIFY_PLAYLIST_FIXTURE` accepts a captured Spotify embed playlist HTML
file for the equivalent public-preview check.

Spotify's signed-in web API is undocumented. Device validation needs a user's
Spotify login, a playlist they can access, and an Audioly account for the final
save. No developer API key or developer membership is used by these import paths.
