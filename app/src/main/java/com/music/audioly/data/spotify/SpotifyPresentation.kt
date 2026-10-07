package com.music.audioly.data.spotify

import com.music.audioly.data.model.*
import com.music.audioly.data.spotify.models.*

/** Display-only rows. Resolve through SpotifyIntegration before any player/queue action. */
internal fun SpotifyTrack.displaySong(entryId: String? = null): Song = Song(
    videoId = "spotify:track:$id",
    title = name,
    artist = artists.joinToString { it.name },
    thumbnailUrl = album?.images?.firstOrNull()?.url,
    durationText = durationMs.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60000, it / 1000 % 60) },
    albumId = album?.id?.let { "spotify:album:$it" },
    albumName = album?.name,
    isExplicit = explicit,
    setVideoId = entryId,
)

internal fun SpotifyTrack.playbackSong(resolved: Song): Song {
    require(!resolved.videoId.startsWith("spotify:")) { "Resolve Spotify tracks before playback." }
    return resolved.copy(title = name.ifBlank { resolved.title },
        artist = artists.joinToString { it.name }.ifBlank { resolved.artist },
        thumbnailUrl = album?.images?.firstOrNull()?.url ?: resolved.thumbnailUrl,
        albumName = album?.name ?: resolved.albumName, isExplicit = explicit)
}

internal fun SpotifyIntegration.Profile.homeShelves(): List<HomeShelf> = buildList {
    homeSections.forEach { section ->
        val items = section.items.map { item -> when (item) {
            is SpotifyHomeFeedItem.Playlist -> ShelfItem(item.name, item.ownerName ?: "Spotify playlist", item.imageUrl, null, "spotify:playlist:${item.id}")
            is SpotifyHomeFeedItem.Album -> ShelfItem(item.name, item.artists.joinToString { it.name }, item.imageUrl, null, "spotify:album:${item.id}")
            is SpotifyHomeFeedItem.Artist -> ShelfItem(item.name, "Artist", item.imageUrl, null, "spotify:artist:${item.id}")
        } }
        if (items.isNotEmpty()) add(HomeShelf(title = section.title?.takeIf { it.isNotBlank() } ?: "Picked for you", items = items))
    }
    if (tracks.isNotEmpty()) add(HomeShelf(title = "Your top songs", subtitle = "From Spotify", items = tracks.take(20).map {
        ShelfItem(it.name, it.artists.joinToString { artist -> artist.name }, it.album?.images?.firstOrNull()?.url, "spotify:track:${it.id}", null)
    }))
    if (homeSections.isEmpty() && playlists.isNotEmpty()) add(HomeShelf(title = "Made for you", subtitle = "Playlists from Spotify", items = playlists.map {
        ShelfItem(it.name, it.owner?.displayName ?: "Spotify playlist", it.images.firstOrNull()?.url, null, "spotify:playlist:${it.id}")
    }))
    if (artists.isNotEmpty()) add(HomeShelf(title = "Your artists", subtitle = "From Spotify", items = artists.map {
        ShelfItem(it.name, "Artist", it.images.firstOrNull()?.url, null, "spotify:artist:${it.id}")
    }))
    if (homeSections.isEmpty() && releases.isNotEmpty()) add(HomeShelf(title = "Albums & new releases", subtitle = "From Spotify", items = releases.map {
        ShelfItem(it.name, it.artists.joinToString { artist -> artist.name }, it.images.firstOrNull()?.url, null, "spotify:album:${it.id}")
    }))
}
