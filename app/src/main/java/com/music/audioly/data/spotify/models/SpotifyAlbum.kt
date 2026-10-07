// Adapted from Meld (FrancescoGrazioso), commit 766dce4937f2b314c2e580444601fd4e9dd42684. GPL-3.0.
package com.music.audioly.data.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyAlbum(
    val id: String = "",
    val name: String = "",
    @SerialName("album_type") val albumType: String? = null,
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    val tracks: SpotifyPaging<SpotifyTrack>? = null,
    val uri: String? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
)

@Serializable
data class SpotifySavedAlbum(
    @SerialName("added_at") val addedAt: String? = null,
    val album: SpotifyAlbum,
)
