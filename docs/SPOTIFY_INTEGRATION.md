# Spotify integration in Audioly

Audioly 1.10.0 replaces its former extended Spotify implementation with the upstream Android Spotify library and playlist importer. The former Spotify Home/search sources, Spotify-only mode, recommendation engine and manual-match overrides are removed.

## Connect and browse

Open Settings and find the Spotify integration/Canvas setup. Follow its instructions to save your Spotify web-player `sp_dc` session cookie. Treat that cookie as a password and never share it in logs or GitHub issues. Audioly uses it to obtain a Spotify access token in an offscreen WebView. Library offers Spotify playlists and Liked Songs. Playlist details load progressively and use the app's regular detail stack. Tracks are matched to YouTube Music for playback; Spotify audio is not streamed.

Public Spotify playlist imports use the upstream embed-page importer and existing track matcher. Imported collections can be saved locally when YouTube playlist creation is unavailable. Private collections require a connected Spotify session. Provider changes can affect token harvesting and GraphQL queries.

Desktop includes the upstream Spotify Canvas cookie integration. The upstream desktop implementation does not yet offer the Android Spotify playlist/library browser; desktop and Android capabilities differ.

See [desktop build notes](../DESKTOP.md) and [license notices](../NOTICE.md).
