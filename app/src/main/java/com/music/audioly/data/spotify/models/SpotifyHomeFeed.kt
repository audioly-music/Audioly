// Adapted from Meld (FrancescoGrazioso), commit 766dce4937f2b314c2e580444601fd4e9dd42684. GPL-3.0.
package com.music.audioly.data.spotify.models

import kotlinx.serialization.Serializable

/**
 * Personalized Spotify home feed returned by the `home` GQL operation.
 * Mirrors what open.spotify.com shows on its landing page: Daily Mix,
 * Discover Weekly, Release Radar, Jump back in, recently played, etc.
 */
@Serializable
data class SpotifyHomeFeed(
    val greeting: String?,
    val sections: List<SpotifyHomeFeedSection>,
)

@Serializable
data class SpotifyHomeFeedSection(
    val sectionUri: String,
    val title: String?,
    val typename: String,
    val totalCount: Int,
    val items: List<SpotifyHomeFeedItem>,
)

@Serializable
sealed class SpotifyHomeFeedItem {
    abstract val uri: String

    @Serializable
data class Playlist(
        override val uri: String,
        val id: String,
        val name: String,
        val description: String?,
        val format: String?,
        val totalCount: Int,
        val imageUrl: String?,
        val extractedColorHex: String?,
        val ownerName: String?,
        val madeForUsername: String?,
    ) : SpotifyHomeFeedItem()

    @Serializable
data class Album(
        override val uri: String,
        val id: String,
        val name: String,
        val albumType: String?,
        val artists: List<SpotifySimpleArtist>,
        val imageUrl: String?,
    ) : SpotifyHomeFeedItem()

    @Serializable
data class Artist(
        override val uri: String,
        val id: String,
        val name: String,
        val imageUrl: String?,
    ) : SpotifyHomeFeedItem()
}
