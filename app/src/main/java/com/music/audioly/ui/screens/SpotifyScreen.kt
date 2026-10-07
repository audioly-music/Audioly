package com.music.audioly.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.music.audioly.ui.components.SongActionsSheet
import com.music.audioly.ui.components.BrowseActionsSheet
import com.music.audioly.ui.components.BrowseTarget
import com.music.audioly.ui.components.ActionRow
import com.music.audioly.download.Downloads
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.audioly.data.model.*
import com.music.audioly.data.spotify.displaySong
import com.music.audioly.data.spotify.playbackSong
import com.music.audioly.data.spotify.homeShelves
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.music.audioly.R
import com.music.audioly.ui.components.MessageState
import com.music.audioly.data.settings.AppSettings
import com.music.audioly.data.spotify.Spotify
import com.music.audioly.data.spotify.SpotifyIntegration as Integration
import com.music.audioly.data.spotify.models.*
import kotlinx.coroutines.*

sealed interface SpotifyRoute {
    data object Home : SpotifyRoute
    data object Library : SpotifyRoute
    data object Search : SpotifyRoute
    data object Liked : SpotifyRoute
    data class Folder(val item: SpotifyLibraryFolder) : SpotifyRoute
    data class Playlist(val item: SpotifyPlaylist) : SpotifyRoute
    data class Album(val item: SpotifyAlbum) : SpotifyRoute
    data class Tracks(val title: String, val items: List<SpotifyTrack>) : SpotifyRoute
}

internal fun SpotifyRoute.pageTitle(): String = when (this) {
    SpotifyRoute.Home -> "Listen Now"
    SpotifyRoute.Search -> "Search"
    SpotifyRoute.Library -> "Spotify playlists"
    SpotifyRoute.Liked -> "Liked songs"
    is SpotifyRoute.Folder -> item.name
    is SpotifyRoute.Playlist -> item.name
    is SpotifyRoute.Album -> item.name
    is SpotifyRoute.Tracks -> title
}

internal fun SpotifyRoute.artwork(): String? = when (this) {
    is SpotifyRoute.Playlist -> item.images.firstOrNull()?.url
    is SpotifyRoute.Album -> item.images.firstOrNull()?.url
    is SpotifyRoute.Tracks -> items.firstOrNull()?.album?.images?.firstOrNull()?.url
    SpotifyRoute.Liked -> Integration.cachedLibrary()?.liked?.firstOrNull()?.album?.images?.firstOrNull()?.url
    else -> null
}

/** Retained by the main frame so opening a collection doesn't discard search results. */
internal class SpotifySearchState {
    val query = mutableStateOf("")
    val submittedQuery = mutableStateOf("")
    val filter = mutableStateOf(SearchFilter.ALL)
    val tracks = mutableStateOf<List<SpotifyTrack>>(emptyList())
    val playlists = mutableStateOf<List<SpotifyPlaylist>>(emptyList())
    val albums = mutableStateOf<List<SpotifyAlbum>>(emptyList())
    val artists = mutableStateOf<List<SpotifyArtist>>(emptyList())
    val offset = mutableIntStateOf(0)
    val total = mutableIntStateOf(0)
    var accountCookie = ""
    var loadedFilter = SearchFilter.ALL
    fun reset() {
        query.value = ""; submittedQuery.value = ""
        tracks.value = emptyList(); playlists.value = emptyList(); albums.value = emptyList(); artists.value = emptyList()
        offset.intValue = 0; total.intValue = 0
    }
}

/** Spotify metadata never enters the audio queue until it has a YouTube match. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotifyScreen(
    start: String = "Home",
    initialRoute: SpotifyRoute? = null,
    onPlay: (List<Song>, Int) -> Unit,
    onSpotifyDownload: (List<SpotifyTrack>, String) -> Unit,
    onSpotifyPlay: (List<SpotifyTrack>, Int, Boolean, String) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onYouTubeArtist: (String) -> Unit,
    onOpen: ((SpotifyRoute) -> Unit)? = null,
    retainedSearch: SpotifySearchState? = null,
    onOpenSettings: () -> Unit = {},
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onQueueNext: ((Song) -> Unit)? = null,
    focusRequested: Boolean = false,
    onFocusHandled: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cookie by AppSettings.spotifySpdcToken.collectAsState()
    val syncError by Integration.syncError.collectAsState()
    val initial = initialRoute ?: when (start) { "Search" -> SpotifyRoute.Search; "Library" -> SpotifyRoute.Library; else -> SpotifyRoute.Home }
    var route by remember(initial) { mutableStateOf(initial) }
    val history = remember(initial) { mutableStateListOf<SpotifyRoute>() }
    fun navigate(next: SpotifyRoute) { if (onOpen != null) onOpen(next) else if (route != next) { history.add(route); route = next } }
    BackHandler(history.isNotEmpty()) { route = history.removeAt(history.lastIndex) }
    val pageState = if (initial == SpotifyRoute.Search && retainedSearch != null) retainedSearch else remember(initial) { SpotifySearchState() }
    var query by pageState.query
    var typeahead by remember(cookie) { mutableStateOf<SpotifySearchResult?>(null) }
    var suggestions by remember(cookie) { mutableStateOf<List<String>>(emptyList()) }
    val suggestionCache = remember(cookie) { mutableMapOf<String, SpotifySearchResult>() }
    var filter by pageState.filter
    var searchRevision by remember { mutableIntStateOf(0) }
    var collectionActions by remember { mutableStateOf(false) }
    var homeSections by remember { mutableStateOf<List<SpotifyHomeFeedSection>>(emptyList()) }
    var playlistMetadata by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var submittedQuery by pageState.submittedQuery
    var tracks by pageState.tracks
    var playlists by pageState.playlists
    var albums by pageState.albums
    var artists by pageState.artists
    var folders by remember { mutableStateOf<List<SpotifyLibraryFolder>>(emptyList()) }
    var playlistItems by remember { mutableStateOf<List<SpotifyPlaylistTrack>>(emptyList()) }
    var albumMetadata by remember { mutableStateOf<SpotifyAlbum?>(null) }
    var offset by pageState.offset
    var total by pageState.total
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var actionTrack by remember { mutableStateOf<SpotifyTrack?>(null) }
    var actionUid by remember { mutableStateOf<String?>(null) }
    var fixTrack by remember { mutableStateOf<SpotifyTrack?>(null) }
    var link by remember { mutableStateOf("") }
    var fixError by remember { mutableStateOf<String?>(null) }
    var addTrack by remember { mutableStateOf<SpotifyTrack?>(null) }
    var destinations by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var rename by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        job = scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Spotify could not complete this request." }
            finally { busy = false }
        }
    }
    fun showProfile(profile: Integration.Profile) {
        homeSections = profile.homeSections; tracks = profile.tracks; playlists = profile.playlists; artists = profile.artists; albums = profile.releases
    }
    suspend fun loadPage(more: Boolean = false) {
        val pageOffset = if (more) offset else 0
        when (val page = route) {
            SpotifyRoute.Home -> showProfile(Integration.profile(force = refresh > 0))
            SpotifyRoute.Library, is SpotifyRoute.Folder -> {
                val data = Integration.call { Spotify.myLibraryNode((page as? SpotifyRoute.Folder)?.item?.uri, limit = 50, offset = pageOffset) }
                playlists = (if (more) playlists else emptyList()) + data.items.filterIsInstance<SpotifyLibraryItem.Playlist>().map { it.playlist }
                folders = (if (more) folders else emptyList()) + data.items.filterIsInstance<SpotifyLibraryItem.Folder>().map { it.folder }
                offset = data.nextPageOffset() ?: data.total; total = data.total
            }
            SpotifyRoute.Liked -> {
                val data = Integration.call { Spotify.likedSongs(limit = 50, offset = pageOffset) }
                check(data.items.isNotEmpty() || pageOffset >= data.total) { "Spotify returned an incomplete liked collection. Retry." }
                tracks = (if (more) tracks else emptyList()) + data.items.map { it.track }
                offset = pageOffset + data.items.size; total = data.total
            }
            SpotifyRoute.Search -> {
                if (!more) { submittedQuery = query.trim(); suggestions = emptyList() }
                if (submittedQuery.isBlank()) return
                pageState.loadedFilter = filter
                val types = when (filter) {
                    SearchFilter.SONGS -> listOf("track")
                    SearchFilter.ARTISTS -> listOf("artist")
                    SearchFilter.ALBUMS -> listOf("album")
                    SearchFilter.PLAYLISTS -> listOf("playlist")
                    else -> listOf("track", "artist", "album", "playlist")
                }
                val data = Integration.call { Spotify.search(submittedQuery, types, 20, pageOffset) }
                tracks = (if (more) tracks else emptyList()) + data.tracks?.items.orEmpty()
                playlists = (if (more) playlists else emptyList()) + data.playlists?.items.orEmpty()
                albums = (if (more) albums else emptyList()) + data.albums?.items.orEmpty()
                artists = (if (more) artists else emptyList()) + data.artists?.items.orEmpty()
                offset = pageOffset + 20
                total = maxOf(data.tracks?.total ?: 0, data.playlists?.total ?: 0, data.albums?.total ?: 0, data.artists?.total ?: 0)
            }
            is SpotifyRoute.Tracks -> tracks = page.items
            is SpotifyRoute.Playlist -> {
                val snapshot = Integration.playlistSnapshot(page.item.id)
                playlistMetadata = snapshot.playlist
                playlistItems = snapshot.items
                tracks = playlistItems.mapNotNull { it.track }
            }
            is SpotifyRoute.Album -> {
                val album = Integration.call { Spotify.album(page.item.id, pageOffset) }
                albumMetadata = album
                val rows = album.tracks?.items.orEmpty()
                check(rows.isNotEmpty() || pageOffset >= album.totalTracks) { "Spotify returned an incomplete album. Retry." }
                val items = rows.map { track -> track.copy(album = track.album ?: SpotifySimpleAlbum(album.id, album.name, album.images, album.releaseDate, album.albumType, album.artists)) }
                tracks = (if (more) tracks else emptyList()) + items
                offset = pageOffset + items.size; total = album.totalTracks
            }
        }
    }
    LaunchedEffect(route) { if (route != SpotifyRoute.Search) listState.scrollToItem(0) }
    LaunchedEffect(route, cookie, refresh) {
        job?.cancelAndJoin(); busy = false; message = null
        val retainSearch = route == SpotifyRoute.Search && pageState.accountCookie == cookie && submittedQuery.isNotBlank()
        pageState.accountCookie = cookie
        if (!retainSearch) { tracks = emptyList(); playlists = emptyList(); albums = emptyList(); artists = emptyList(); offset = 0; total = 0 }
        folders = emptyList(); playlistItems = emptyList(); playlistMetadata = null; albumMetadata = null
        if (cookie.isNotBlank()) {
            (route as? SpotifyRoute.Playlist)?.let { page -> Integration.cachedPlaylist(page.item.id)?.let { saved ->
                playlistMetadata = saved.playlist; playlistItems = saved.items; tracks = saved.items.mapNotNull { it.track }
            } }
            if (route == SpotifyRoute.Home) Integration.cachedProfile()?.let(::showProfile)
            if (route == SpotifyRoute.Liked) Integration.cachedLibrary()?.let { tracks = it.liked; offset = it.liked.size; total = it.likedTotal }
            if (route == SpotifyRoute.Search && !retainSearch && submittedQuery.isNotBlank()) task { loadPage() }
            if (route != SpotifyRoute.Search) task {
                loadPage()
                while (offset in 1 until total) { currentCoroutineContext().ensureActive(); loadPage(true) }
            }
        }
    }
    fun resolveAndPlay(source: List<SpotifyTrack>, radio: Boolean = false, startIndex: Int = 0) {
        onSpotifyPlay(source, startIndex, radio, route.pageTitle())
    }

    val displaySongs = tracks.mapIndexed { index, track ->
        track.displaySong(playlistItems.filter { it.track != null }.getOrNull(index)?.uid)
    }
    fun trackFor(song: Song) = (tracks + typeahead?.tracks?.items.orEmpty()).firstOrNull { "spotify:track:${it.id}" == song.videoId }
    fun songMenu(song: Song) { actionTrack = trackFor(song); actionUid = song.setVideoId }
    fun playRows(rows: List<Song>, index: Int, radio: Boolean = false) {
        val selected = rows.mapNotNull(::trackFor)
        resolveAndPlay(selected, radio, index.coerceIn(0, (selected.size - 1).coerceAtLeast(0)))
    }
    fun openItem(item: ShelfItem) {
        item.videoId?.let { id -> tracks.firstOrNull { "spotify:track:${it.id}" == id }?.let { track ->
            resolveAndPlay(listOf(track), Integration.smartQueue.value); return
        } }
        val id = item.browseId.orEmpty()
        when {
            id == "spotify:liked" -> navigate(SpotifyRoute.Liked)
            id.startsWith("spotify:folder:") -> folders.firstOrNull { "spotify:folder:${it.uri}" == id }?.let { navigate(SpotifyRoute.Folder(it)) }
            id.startsWith("spotify:playlist:") -> (playlists + typeahead?.playlists?.items.orEmpty()).firstOrNull { "spotify:playlist:${it.id}" == id }?.let { navigate(SpotifyRoute.Playlist(it)) }
            id.startsWith("spotify:album:") -> (albums + typeahead?.albums?.items.orEmpty()).firstOrNull { "spotify:album:${it.id}" == id }?.let { navigate(SpotifyRoute.Album(it)) }
            id.startsWith("spotify:artist:") -> onYouTubeArtist(item.title)
        }
    }
    fun browse(item: BrowseItem) = openItem(ShelfItem(item.title, item.subtitle, item.thumbnailUrl, null, item.browseId))
    LaunchedEffect(filter) {
        if (route == SpotifyRoute.Search && submittedQuery.isNotBlank() && filter != pageState.loadedFilter) {
            job?.cancelAndJoin(); busy = false; task { loadPage(); searchRevision++ }
        }
    }
    LaunchedEffect(query, submittedQuery, route, cookie) {
        suggestions = emptyList(); typeahead = null
        val term = query.trim()
        if (route != SpotifyRoute.Search || cookie.isBlank() || term.length < 2 || term == submittedQuery) return@LaunchedEffect
        delay(300)
        try {
            val result = suggestionCache[term] ?: Integration.call { Spotify.search(term, listOf("track", "artist", "album", "playlist"), 5) }.also {
                if (suggestionCache.size >= 30) suggestionCache.remove(suggestionCache.keys.first())
                suggestionCache[term] = it
            }
            typeahead = result
            suggestions = (listOf(term) + result.tracks?.items.orEmpty().map { it.name } + result.artists?.items.orEmpty().map { it.name }).distinct().take(6)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { suggestions = listOf(term) }
    }
    LaunchedEffect(tracks, cookie) {
        if (cookie.isNotBlank()) Integration.prepareMatches(tracks)
    }
    val typeaheadRows = buildList<SearchResult> {
        addAll(typeahead?.tracks?.items.orEmpty().map { SearchResult.Track(it.displaySong()) })
        addAll(typeahead?.artists?.items.orEmpty().map { SearchResult.Browse(BrowseItem("spotify:artist:${it.id}", it.name, "Artist", it.images.firstOrNull()?.url, BrowseType.ARTIST)) })
        addAll(typeahead?.albums?.items.orEmpty().map { SearchResult.Browse(BrowseItem("spotify:album:${it.id}", it.name, "Album", it.images.firstOrNull()?.url, BrowseType.ALBUM)) })
    }
    val searchRows = buildList<SearchResult> {
        if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) addAll(displaySongs.map { SearchResult.Track(it) })
        if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) addAll(artists.map { SearchResult.Browse(BrowseItem("spotify:artist:${it.id}", it.name, "Artist", it.images.firstOrNull()?.url, BrowseType.ARTIST)) })
        if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) addAll(albums.map { SearchResult.Browse(BrowseItem("spotify:album:${it.id}", it.name, it.artists.joinToString { a -> a.name }, it.images.firstOrNull()?.url, BrowseType.ALBUM)) })
        if (filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS) addAll(playlists.map { SearchResult.Browse(BrowseItem("spotify:playlist:${it.id}", it.name, it.owner?.displayName ?: "Spotify playlist", it.images.firstOrNull()?.url, BrowseType.PLAYLIST)) })
    }
    Box(Modifier.fillMaxSize()) {
        if (cookie.isBlank()) {
            Box(Modifier.padding(contentPadding)) { MessageState("Connect Spotify to bring your music here.", actionLabel = "Connect Spotify", onAction = onOpenSettings) }
        } else when (val page = route) {
            SpotifyRoute.Search -> SearchScreen(
                query = query, onQueryChange = { query = it; if (it.isBlank()) { job?.cancel(); pageState.reset(); message = null } }, filter = filter,
                availableFilters = SearchFilter.entries.filter { it != SearchFilter.VIDEOS }, onFilterChange = { filter = it },
                results = when { submittedQuery.isBlank() -> null; busy && searchRows.isEmpty() -> UiState.Loading; message != null && searchRows.isEmpty() -> UiState.Error(message!!); else -> UiState.Success(searchRows) },
                loadingMore = busy, onLoadMore = { if (!busy && message == null && offset in 1 until total) task { loadPage(true) } },
                listState = listState, scrollResetTrigger = searchRevision, focusRequested = focusRequested, onFocusHandled = onFocusHandled,
                onSongClick = { rows, index -> playRows(listOf(rows[index]), 0, Integration.smartQueue.value) },
                onSongLongPress = ::songMenu, onSongSwipe = { song -> task { trackFor(song)?.let { onQueueNext?.invoke(it.playbackSong(Integration.resolve(it))) } } },
                onTopResultPlay = { playRows(listOf(it), 0) }, onTopResultPlaylist = ::songMenu,
                onBrowseClick = ::browse, onBrowseLongPress = ::browse, history = emptyList(), suggestions = suggestions, typeaheadResults = typeaheadRows, onTypeaheadLongPress = ::songMenu,
                onSubmit = { if (query.isNotBlank()) task { loadPage(); searchRevision++ } },
                onSuggestionClick = { query = it; task { loadPage(); searchRevision++ } }, onHistoryClick = {}, onHistoryRemove = {}, onHistoryClear = {},
                contentPadding = contentPadding,
            )
            SpotifyRoute.Home -> {
                val shelves = Integration.Profile(tracks = tracks, playlists = playlists, artists = artists, releases = albums, homeSections = homeSections).homeShelves()
                HomeScreen(state = when { shelves.isNotEmpty() -> UiState.Success(shelves); busy -> UiState.Loading; message != null -> UiState.Error(message!!); else -> UiState.Success(emptyList()) },
                    listState = listState, onItemClick = { item, _ -> openItem(item) },
                    onItemLongPress = { item -> item.videoId?.let { id -> displaySongs.firstOrNull { it.videoId == id }?.let(::songMenu) } ?: openItem(item) },
                    onRetry = { refresh++ }, refreshing = busy, onRefresh = { refresh++ }, pullState = rememberPullToRefreshState(),
                    contentPadding = contentPadding, title = stringResource(R.string.listen_now))
            }
            SpotifyRoute.Library, is SpotifyRoute.Folder -> {
                val shelf = HomeShelf(title = (page as? SpotifyRoute.Folder)?.item?.name ?: "Spotify playlists", items =
                    (if (page == SpotifyRoute.Library) listOf(ShelfItem("Liked songs", "Spotify", Integration.cachedLibrary()?.liked?.firstOrNull()?.album?.images?.firstOrNull()?.url, null, "spotify:liked")) else emptyList()) +
                    folders.map { ShelfItem(it.name, "Folder", null, null, "spotify:folder:${it.uri}") } +
                    playlists.map { ShelfItem(it.name, it.owner?.displayName ?: "Spotify playlist", it.images.firstOrNull()?.url, null, "spotify:playlist:${it.id}") })
                LibraryGridPage(shelf = shelf, gridState = rememberLazyGridState(), onItemClick = ::openItem, onItemLongPress = ::openItem, contentPadding = contentPadding)
            }
            else -> {
                val title = when (page) { is SpotifyRoute.Playlist -> playlistMetadata?.name ?: page.item.name; is SpotifyRoute.Album -> page.item.name; is SpotifyRoute.Tracks -> page.title; else -> "Liked songs" }
                val image = when (page) { is SpotifyRoute.Playlist -> (playlistMetadata ?: page.item).images.firstOrNull()?.url; is SpotifyRoute.Album -> page.item.images.firstOrNull()?.url; else -> tracks.firstOrNull()?.album?.images?.firstOrNull()?.url }
                val subtitle = when (page) { is SpotifyRoute.Playlist -> (playlistMetadata ?: page.item).owner?.displayName ?: "Spotify"; is SpotifyRoute.Album -> "${page.item.artists.joinToString { it.name }} · ${albumMetadata?.releaseDate ?: page.item.releaseDate.orEmpty()}"; else -> "Spotify" }
                val detail = DetailPage(browseId = "spotify:${page.hashCode()}", title = title, subtitle = subtitle, thumbnailUrl = image,
                    songs = if (tracks.isEmpty() && busy) UiState.Loading else if (tracks.isEmpty() && message != null) UiState.Error(message!!) else UiState.Success(displaySongs),
                    type = if (page is SpotifyRoute.Album) BrowseType.ALBUM else BrowseType.PLAYLIST,
                    description = (page as? SpotifyRoute.Playlist)?.let { android.text.Html.fromHtml((playlistMetadata ?: it.item).description.orEmpty(), android.text.Html.FROM_HTML_MODE_LEGACY).toString() })
                DetailScreen(page = detail, currentSong = currentSong, isPlaying = isPlaying,
                    onSongClick = { rows, index -> playRows(rows, index) }, onSongLongPress = ::songMenu,
                    onSongSwipe = { song -> task { trackFor(song)?.let { onQueueNext?.invoke(it.playbackSong(Integration.resolve(it))) } } },
                    onShuffle = { rows -> playRows(rows.shuffled(), 0) }, onSectionItemClick = ::openItem,
                    onArtistClick = { _, name -> onYouTubeArtist(name) }, onAddSuggested = {}, contentPadding = contentPadding,
                    listState = listState, onMore = { collectionActions = true })
            }
        }
        if (busy || message != null || syncError != null) {
            Surface(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding()),
                shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                Column(Modifier.padding(12.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(message ?: syncError ?: "Loading your music…", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { job?.cancel(); message = null; Integration.syncError.value = null }) { Text(if (busy) "Cancel" else "Dismiss") }
                    }
                }
            }
        }
    }
    fun share(url: String) {
        context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, url)
        }, "Share"))
    }
    fun withMatch(track: SpotifyTrack, action: (Song) -> Unit) { actionTrack = null; task { action(track.playbackSong(Integration.resolve(track))) } }
    if (collectionActions) ModalBottomSheet(onDismissRequest = { collectionActions = false }) {
        val target = BrowseTarget(null, route.pageTitle(), "Spotify", route.artwork(),
            if (route is SpotifyRoute.Album) BrowseType.ALBUM else BrowseType.PLAYLIST, displaySongs, fromCard = false)
        fun queueCollection(next: Boolean) { collectionActions = false; task {
            val ordered = if (next) tracks.asReversed() else tracks
            for (track in ordered) {
                val song = track.playbackSong(Integration.resolve(track))
                if (next) onPlayNext(song) else onAddToQueue(song)
            }
        } }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            BrowseActionsSheet(target = target,
                onPlayNext = { queueCollection(true) }, onAddToQueue = { queueCollection(false) },
                onPlay = { collectionActions = false; playRows(displaySongs, 0) },
                onShuffle = { collectionActions = false; playRows(displaySongs.shuffled(), 0) },
                onDownloadAll = { collectionActions = false; onSpotifyDownload(tracks, route.pageTitle()) },
                onShare = when (val page = route) {
                    is SpotifyRoute.Playlist -> ({ share("https://open.spotify.com/playlist/${page.item.id}") })
                    is SpotifyRoute.Album -> ({ share("https://open.spotify.com/album/${page.item.id}") })
                    else -> null
                })
            ActionRow(Icons.Rounded.Refresh, "Refresh") { collectionActions = false; refresh++ }
            (route as? SpotifyRoute.Playlist)?.let { page ->
                ActionRow(Icons.Rounded.Edit, "Rename playlist") { collectionActions = false; playlistName = (playlistMetadata ?: page.item).name; rename = true }
            }
        }
    }
    actionTrack?.let { track ->
        val liked = Integration.cachedLibrary()?.liked?.any { it.id == track.id } == true || route == SpotifyRoute.Liked
        var matched by remember(track.id) { mutableStateOf(Integration.cachedMatch(track)) }
        LaunchedEffect(track.id) {
            try { matched = Integration.resolve(track) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
        ModalBottomSheet(onDismissRequest = { actionTrack = null }) {
            SongActionsSheet(song = (matched?.let { track.playbackSong(it) } ?: track.displaySong(actionUid)).copy(artistId = track.artists.firstOrNull()?.id, albumId = track.album?.id), signedIn = true,
                likeStatus = if (liked) LikeStatus.LIKE else LikeStatus.INDIFFERENT, showDislike = false,
                onPlayNext = { withMatch(track, onPlayNext) }, onAddToQueue = { withMatch(track, onAddToQueue) },
                onStartRadio = { actionTrack = null; resolveAndPlay(listOf(track), radio = true) },
                onDownload = { onSpotifyDownload(listOf(track), route.pageTitle()) },
                onToggleLike = { actionTrack = null; task {
                    Integration.call(mutation = true) { if (liked) Spotify.removeFromLibrary(listOf("spotify:track:${track.id}")) else Spotify.addToLibrary(listOf("spotify:track:${track.id}")) }
                    Integration.invalidateLibrary(); if (route == SpotifyRoute.Liked) loadPage()
                } }, onToggleDislike = {},
                onAddToPlaylist = { actionTrack = null; task { destinations = Integration.library(force = true).playlists; addTrack = track } },
                onOpenAlbum = { actionTrack = null; track.album?.let { album -> navigate(SpotifyRoute.Album(SpotifyAlbum(id = album.id, name = album.name, images = album.images, artists = album.artists))) } },
                onOpenArtist = { actionTrack = null; track.artists.firstOrNull()?.let { onYouTubeArtist(it.name) } },
                onShare = { share("https://open.spotify.com/track/${track.id}") },
                onRemoveFromPlaylist = (route as? SpotifyRoute.Playlist)?.let { page -> actionUid?.let { uid -> ({
                    actionTrack = null; task { Integration.call(mutation = true) { Spotify.removeTracksFromPlaylist(page.item.id, listOf(Spotify.PlaylistItemRef("spotify:track:${track.id}", uid))) }; Integration.invalidateLibrary(); loadPage() }
                }) } },
                extraActions = {
                    ActionRow(Icons.Rounded.Edit, "Fix playback match") { actionTrack = null; fixTrack = track; link = ""; fixError = null }
                    (route as? SpotifyRoute.Playlist)?.let { page -> actionUid?.let { uid ->
                        ActionRow(Icons.Rounded.Edit, "Move to top") { actionTrack = null; task {
                            Integration.call(mutation = true) { Spotify.moveItemsInPlaylist(page.item.id, listOf(uid), playlistItems.firstOrNull()?.uid) }; loadPage()
                        } }
                    } }
                    ActionRow(Icons.Rounded.Refresh, "Reset match") { Integration.clearMatch(track); actionTrack = null; message = "Saved match cleared." }
                })
        }
    }
    fixTrack?.let { track -> AlertDialog(onDismissRequest = { fixTrack = null }, title = { Text("Fix playback match") }, text = {
        Column {
            OutlinedTextField(link, { link = it; fixError = null }, label = { Text("YouTube or YouTube Music link") }, isError = fixError != null)
            fixError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching { Integration.overrideMatch(track, link) }.fold(
        onSuccess = { fixTrack = null; message = "Playback correction saved." }, onFailure = { fixError = it.message }) }) { Text("Save match") } },
        dismissButton = { TextButton(onClick = { fixTrack = null }) { Text("Cancel") } }) }
    addTrack?.let { track -> AlertDialog(onDismissRequest = { addTrack = null }, title = { Text("Add to Spotify playlist") }, text = {
        LazyColumn { itemsIndexed(destinations) { _, destination -> TextButton(onClick = { addTrack = null; task {
            Integration.call(mutation = true) { Spotify.addTracksToPlaylist(destination.id, listOf("spotify:track:${track.id}")) }; Integration.invalidateLibrary(); message = "Added to ${destination.name}."
        } }) { Text(destination.name) } } }
    }, confirmButton = { TextButton(onClick = { addTrack = null }) { Text("Cancel") } }) }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename Spotify playlist") }, text = {
        OutlinedTextField(playlistName, { playlistName = it }, label = { Text("Playlist name") })
    }, confirmButton = { TextButton(onClick = { val page = route as? SpotifyRoute.Playlist; rename = false; if (page != null) task {
        Integration.call(mutation = true) { Spotify.editPlaylistAttributes(page.item.id, newName = playlistName.trim()) }
        Integration.invalidateLibrary(); route = SpotifyRoute.Playlist(page.item.copy(name = playlistName.trim()))
    } }, enabled = playlistName.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } })
}

@Composable
internal fun SpotifyRow(title: String, subtitle: String, image: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        AsyncImage(image, contentDescription = null, modifier = Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}
