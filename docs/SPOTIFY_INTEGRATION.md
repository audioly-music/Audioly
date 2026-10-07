# Spotify integration in Audioly

This port adapts the Spotify transport, models, authentication, query registry,
and recommendation engine from [Meld](https://github.com/FrancescoGrazioso/Meld),
commit `766dce4937f2b314c2e580444601fd4e9dd42684` (GPL-3.0). Original copyright
notices remain in adapted files; Audioly's existing GPL-3.0 license applies.
Audioly supplies its own Compose screens, session-scoped cache, playback adapter,
and matching using its existing recording-aware TrackMatcher.

## Entry points

Open **Settings → Account & integrations → Spotify**. Connection, Home/Search
sources, Spotify-only Home, smart queues, library sync, heart forwarding, and Canvas
controls are together here. The separate playlist-copy importer is also in Account
& integrations. Library no longer has the two full-width Spotify/import buttons.

After connection, Library shows Spotify playlist cards and a Liked songs collection
using Audioly's existing shelf components, independently of YouTube sign-in. Pull
to refresh or use Refresh Spotify data in settings. Library snapshots refresh after
15 minutes; profile snapshots after six hours. A sync failure leaves saved collections
visible with a retry message. Liked songs cache the first page and full count, then
load the remaining pages on demand. Play all fetches every album/liked-song page.
Spotify-only Home replaces the YouTube feed. Mixed Home adds top tracks, artist cards,
playlist cards and releases. Artist cards navigate directly to YouTube Music.

Spotify is a data source for Audioly's existing screens. Home uses HomeScreen's
hero cards and shelves, Search uses SearchScreen and its native filters, and Spotify
playlists/albums use DetailScreen with the same artwork wash, play/shuffle controls,
track rows, mini player and bottom navigation as YouTube collections. There is no
separate Spotify header, tab strip or full-screen browser shell for music browsing.

Display rows carry explicit `spotify:track:` IDs. Every playback and queue callback
resolves these to YouTube IDs before passing songs to the player; Spotify display
metadata stays consistent with the native rows. Playlist-entry IDs remain separate
so duplicate songs can still be removed or moved individually.

The integration provides:

- Personalized Home, top songs/artists across three time ranges, playlists, releases.
- Spotify search across songs, artists, albums and playlists, with more-results loading.
- Library folders, playlists, paginated liked songs, album tracklists and release metadata.
- Automatic YouTube matching and a persistent automatic match cache.
- Per-song manual match correction using a YouTube/YouTube Music URL, and reset. Manual overrides are device-persistent and take priority across reconnections.
- Spotify radio using Meld's candidate scoring, artist affinity, genre neighbors,
  album candidates, personal top tracks across three weighted time ranges, artist caps and source diversification. Popularity similarity contributes only when both tracks provide it; missing popularity is neutral.
- Spotify likes/unlikes, adding/removing playlist items, moving an item to the top,
  and playlist renaming. Spotify still enforces ownership and collaboration rights.
- Optional forwarding of in-app Audioly heart changes to Spotify for mapped tracks.
- Artist navigation to an exact-name YouTube artist result, with search fallback.
- Six-hour profile caching, session refresh, query hash updates and rate-limit handling.

Audio continues through Audioly's player. No Spotify developer registration is
needed by this integration. Apple public playlist import remains separate. Meld's
Qobuz integration and other unrelated features are not part of this port.

## Operational details

Spotify web interfaces are undocumented and may change. Authentication uses Meld's
TOTP flow with Audioly's WebView token flow as fallback. Query hashes are refreshed
from Meld's public registry; cached/hardcoded values remain available when offline.
No account cookies are sent to that registry or the public TOTP metadata source.
Login uses the same compact toolbar pattern as YouTube sign-in. A Spotify account
is verified in a temporary token context and shown with its name/avatar. Only **Use
this profile** persists the selected session; cancelling the preview does not switch
the app's active Spotify connection. The Spotify Accounts layout fix remains active.
Login validates the session before reporting success; account changes separate
profile and match caches. Disconnect removes the active profile/library snapshots and Spotify session; manual corrections remain.

Profile and library snapshots live in an account-scoped SQLite database. GraphQL
Home/library data, REST top tracks/artists across three time ranges, and retained
local snapshots provide the hybrid profile. Missing artist images are enriched
with at most four concurrent requests. REST rate limits defer later REST requests;
long GraphQL Retry-After responses are surfaced without an early retry. Spotify may
restrict some REST data, so available GraphQL/library data and saved metadata remain
important fallbacks. Fresh network failures must not be reported as refreshed data.

The existing Import flow can create a separate private YouTube Music playlist after
a match review. Optional like sync forwards in-app heart changes for matched tracks;
it does not bulk-modify either account's existing likes. Spotify library sync reads
Spotify collections into Audioly, rather than copying them into YouTube Music.

## Validation

Run `gradlew.bat :app:assembleDevDebug`, then `gradlew.bat :app:testDevDebugUnitTest` separately with Java 17.
Tests cover token generation against RFC vectors, query registry coverage, manual
match URL restrictions, metadata parsing, legacy profile-cache compatibility, library count preservation, popularity scoring with missing data, filtered library-page offsets, native row identity, rejection of unresolved playback IDs, and existing playlist parsing/matching.
Live login, personalized results and Spotify mutations require a signed-in account
on the test device; a successful build alone does not verify those service calls.


## 1.9.2: Home, menus and playback startup

- Home retains the music sections returned by Spotify, including their order, titles and artwork, instead of flattening everything into four shelves. Each request asks for up to 50 items per section. Podcasts and other unsupported item types are omitted. Serialized sections survive restarts; older snapshots trigger a fresh feed request.
- Track and collection overflow menus reuse Audioly's native action sheets. Queue and download actions resolve Spotify metadata before passing tracks to the YouTube player. Sharing retains the Spotify catalog link. Spotify has no equivalent to YouTube dislikes, so that row is omitted.
- Playback resolves only the selected track before committing playback. The activity owns the remaining matching job, so navigating away does not cancel the queue. New playback generations invalidate older work; resolved context tracks are inserted before autoplay suggestions. Recommendation requests happen after playback begins. Party playback keeps the existing single-selected-track behavior.
- First-time playback still requires matching and stream lookup. Saved automatic/manual matches bypass the matching search; zero latency is not guaranteed.


## 1.9.3: Queue handoff and preparation

The former activity callback launched queue installation asynchronously. A single `yield()` did not wait for `playSongs`, which switches dispatcher while building media items. Tail validation could run before the new source existed and silently exit. The queue loader now awaits the initial commit before resolving/appending the tail. Regression tests cover that asynchronous boundary, replacement requests, duplicates, unmatched entries and recommendation ordering.

While a Spotify queue is being prepared, the service suppresses unrelated YouTube AutoPlay for that source and rearms it on completion. Playlist snapshots are cached per account for immediate display on reopening, with a refresh in the background. The first twelve tracks shown are prepared with two concurrent matching workers; matching locks prevent duplicate in-flight searches for the same track.

Spotify search now supplies debounced live Spotify results and text suggestions, with a bounded session cache. Download requests go through the activity's native permission/network-policy flow and survive navigation. The open song menu resolves its identity so download progress and failures track the actual YouTube audio id.

Network playback and downloads still require device verification; unit tests cannot verify upstream stream availability or account-specific catalog results.
