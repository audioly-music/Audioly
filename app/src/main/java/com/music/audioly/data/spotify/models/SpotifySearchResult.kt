// Adapted from Meld (FrancescoGrazioso), commit 766dce4937f2b314c2e580444601fd4e9dd42684. GPL-3.0.
package com.music.audioly.data.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifySearchResult(
    val tracks: SpotifyPaging<SpotifyTrack>? = null,
    val playlists: SpotifyPaging<SpotifyPlaylist>? = null,
    val albums: SpotifyPaging<SpotifyAlbum>? = null,
    val artists: SpotifyPaging<SpotifyArtist>? = null,
)
