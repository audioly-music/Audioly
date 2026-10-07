// Adapted from Meld (FrancescoGrazioso), commit 766dce4937f2b314c2e580444601fd4e9dd42684. GPL-3.0.
package com.music.audioly.data.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPaging<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val href: String? = null,
    /** Number of source rows consumed before filtering unsupported wrapper types. */
    val consumed: Int? = null,
)

internal fun <T> SpotifyPaging<T>.nextPageOffset(): Int? {
    val count = consumed ?: items.size
    val end = offset + count
    if (end >= total) return null
    check(count > 0) { "Spotify returned an incomplete page. Please retry." }
    return end
}
