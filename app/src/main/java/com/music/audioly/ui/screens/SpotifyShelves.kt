package com.music.audioly.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.audioly.data.model.HomeShelf
import com.music.audioly.data.model.ShelfItem
import com.music.audioly.data.model.Song
import com.music.audioly.data.settings.AppSettings
import com.music.audioly.data.spotify.SpotifyIntegration as Integration
import com.music.audioly.data.spotify.homeShelves
import com.music.audioly.data.spotify.playbackSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SpotifyLibraryShelf(onOpen: (SpotifyRoute) -> Unit, refreshing: Boolean) {
    val cookie by AppSettings.spotifySpdcToken.collectAsState()
    val enabled by Integration.libraryEnabled.collectAsState()
    val revision by Integration.libraryRevision.collectAsState()
    var library by remember(cookie) { mutableStateOf(if (cookie.isBlank()) null else Integration.cachedLibrary()) }
    var error by remember(cookie) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(cookie, enabled, refreshing, retry) {
        if (cookie.isBlank() || !enabled) return@LaunchedEffect
        loading = true
        try { library = Integration.library(force = refreshing || retry > 0); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not sync Spotify." }
        finally { loading = false }
    }
    LaunchedEffect(revision) { if (cookie.isNotBlank()) library = Integration.cachedLibrary() }
    if (cookie.isBlank() || !enabled) return
    val rows = library?.playlists.orEmpty()
    val shelf = HomeShelf(title = "Spotify", subtitle = "Your playlists & liked songs", items = listOf(
        ShelfItem(title = "Liked songs", subtitle = library?.let { "${it.likedTotal} songs · Spotify" } ?: "Spotify",
            thumbnailUrl = library?.liked?.firstOrNull()?.album?.images?.firstOrNull()?.url, videoId = null, browseId = "spotify:liked")
    ) + rows.map { ShelfItem(title = it.name, subtitle = it.owner?.displayName ?: "Spotify playlist",
        thumbnailUrl = it.images.firstOrNull()?.url, videoId = null, browseId = "spotify:playlist:${it.id}") })
    fun open(item: ShelfItem) {
        if (item.browseId == "spotify:liked") onOpen(SpotifyRoute.Liked)
        else rows.firstOrNull { "spotify:playlist:${it.id}" == item.browseId }?.let { onOpen(SpotifyRoute.Playlist(it)) }
    }
    Column {
        LibraryGridShelf(shelf = shelf, onItemClick = ::open, onItemLongPress = ::open, onShowAll = { onOpen(SpotifyRoute.Library) })
        if (loading && library == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        error?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(if (library != null) "Showing saved Spotify collections. $it" else it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { retry++ }, enabled = !loading) { Text("Retry") }
            }
        }
    }
}

@Composable
internal fun SpotifyHomePreview(onOpen: (SpotifyRoute) -> Unit, onYouTubeArtist: (String) -> Unit, onPlay: (List<Song>, Int) -> Unit, onSpotifyPlay: (List<com.music.audioly.data.spotify.models.SpotifyTrack>, Int, Boolean, String) -> Unit) {
    val cookie by AppSettings.spotifySpdcToken.collectAsState()
    var profile by remember(cookie) { mutableStateOf(if (cookie.isBlank()) null else Integration.cachedProfile()) }
    var message by remember(cookie) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(cookie) {
        if (cookie.isNotBlank()) try { profile = Integration.profile() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message }
    }
    fun open(item: ShelfItem) {
        val data = profile ?: return
        val id = item.browseId.orEmpty()
        when {
            item.videoId != null -> data.tracks.firstOrNull { "spotify:track:${it.id}" == item.videoId }?.let { track ->
                onSpotifyPlay(listOf(track), 0, Integration.smartQueue.value, "Spotify")
            }
            id.startsWith("spotify:playlist:") -> data.playlists.firstOrNull { "spotify:playlist:${it.id}" == id }?.let { onOpen(SpotifyRoute.Playlist(it)) }
            id.startsWith("spotify:album:") -> data.releases.firstOrNull { "spotify:album:${it.id}" == id }?.let { onOpen(SpotifyRoute.Album(it)) }
            id.startsWith("spotify:artist:") -> onYouTubeArtist(item.title)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (cookie.isBlank()) TextButton(onClick = { onOpen(SpotifyRoute.Home) }, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Connect Spotify") }
        profile?.homeShelves()?.forEachIndexed { index, shelf ->
            val longPress: (ShelfItem) -> Unit = { item ->
                if (item.videoId != null) profile?.let { onOpen(SpotifyRoute.Tracks("Your top songs", it.tracks)) }
                else open(item)
            }
            if (index == 0) HeroShelf(shelf, onItemClick = ::open, onItemLongPress = longPress)
            else Shelf(shelf, onItemClick = ::open, onItemLongPress = longPress)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        message?.let { Text(it, Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall) }
    }
}
