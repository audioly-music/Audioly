// Adapted from Meld (FrancescoGrazioso), commit 766dce4937f2b314c2e580444601fd4e9dd42684. GPL-3.0.
package com.music.audioly.data.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyUser(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val product: String? = null,
    val country: String? = null,
)

@Serializable
data class SpotifyImage(
    val url: String = "",
    val height: Int? = null,
    val width: Int? = null,
)
