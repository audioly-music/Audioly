package com.music.audioly.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.audioly.data.Http
import com.music.audioly.data.YtMusicRepository
import com.music.audioly.data.model.SearchFilter
import com.music.audioly.data.model.SearchResult
import com.music.audioly.data.model.Song
import com.music.audioly.data.settings.AppSettings
import com.music.audioly.data.sources.TrackMatcher
import com.music.audioly.data.spotify.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.Request
import java.net.URI
import java.security.MessageDigest

/** Audioly adapter for Meld's Spotify transport; Spotify IDs never enter the audio queue. */
internal object SpotifyIntegration {
    private lateinit var prefs: SharedPreferences
    private lateinit var cache: SpotifyLocalCache
    private val libraryLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionLock = Mutex()
    private var tokenCookie = ""
    private var expires = 0L
    private var hashesUpdated = 0L
    val pendingQueueSource = MutableStateFlow<String?>(null)
    private val matchLocks = Array(32) { Mutex() }
    val homeEnabled = MutableStateFlow(false)
    val searchEnabled = MutableStateFlow(false)
    val spotifyOnly = MutableStateFlow(false)
    val smartQueue = MutableStateFlow(true)
    val libraryEnabled = MutableStateFlow(true)
    val libraryRevision = MutableStateFlow(0)
    val syncLikes = MutableStateFlow(false)
    val hideYouTubeLikes = MutableStateFlow(false)
    val syncError = MutableStateFlow<String?>(null)
    fun init(context: Context) {
        prefs = context.getSharedPreferences("audioly_spotify", Context.MODE_PRIVATE)
        cache = SpotifyLocalCache(context)
        val migrated = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("match_") && value is String && runCatching { json.decodeFromString<Match>(value).manual }.getOrDefault(false)) {
                val manualKey = "manual_${key.substringAfterLast('_')}"
                if (!prefs.contains(manualKey)) migrated.putString(manualKey, value)
            }
        }
        migrated.apply()
        smartQueue.value = prefs.getBoolean("smartQueue", true)
        libraryEnabled.value = prefs.getBoolean("libraryEnabled", true)
        homeEnabled.value = prefs.getBoolean("home", false)
        searchEnabled.value = prefs.getBoolean("search", false)
        spotifyOnly.value = prefs.getBoolean("only", false)
        syncLikes.value = prefs.getBoolean("syncLikes", false)
        hideYouTubeLikes.value = prefs.getBoolean("hideYouTubeLikes", false)
    }
    fun setSmartQueue(enabled: Boolean) { smartQueue.value = enabled; prefs.edit().putBoolean("smartQueue", enabled).apply() }
    fun setLibraryEnabled(enabled: Boolean) { libraryEnabled.value = enabled; prefs.edit().putBoolean("libraryEnabled", enabled).apply() }
    fun setSyncLikes(enabled: Boolean) { syncLikes.value = enabled; prefs.edit().putBoolean("syncLikes", enabled).apply() }
    fun setHideYouTubeLikes(enabled: Boolean) { hideYouTubeLikes.value = enabled; prefs.edit().putBoolean("hideYouTubeLikes", enabled).apply() }
    suspend fun syncLike(videoId: String, liked: Boolean) {
        if (!syncLikes.value || AppSettings.spotifySpdcToken.value.isBlank()) return
        val id = prefs.getString("reverse_${account()}_$videoId", null) ?: return
        try {
            call(mutation = true) { if (liked) Spotify.addToLibrary(listOf("spotify:track:$id")) else Spotify.removeFromLibrary(listOf("spotify:track:$id")) }
            invalidateLibrary()
            syncError.value = null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { syncError.value = "Your Audioly rating was saved, but Spotify could not sync it. Retry from the song's Spotify menu." }
    }
    fun settings(home: Boolean = homeEnabled.value, search: Boolean = searchEnabled.value, only: Boolean = spotifyOnly.value) {
        homeEnabled.value = home; searchEnabled.value = search; spotifyOnly.value = only
        prefs.edit().putBoolean("home", home).putBoolean("search", search).putBoolean("only", only).apply()
    }
    private fun account(): String = MessageDigest.getInstance("SHA-256")
        .digest(AppSettings.spotifySpdcToken.value.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    /** Preview a browser account without publishing it as the app's active session. */
    suspend fun previewSession(cookie: String): SpotifyUser = sessionLock.withLock {
        require(cookie.isNotBlank()) { "Finish signing in to Spotify first." }
        val previousAccessToken = Spotify.accessToken
        try {
            val minted = SpotifyAuth.fetchAccessToken(cookie).getOrNull()
            currentCoroutineContext().ensureActive()
            Spotify.accessToken = minted?.accessToken
                ?: com.music.audioly.data.canvas.SpotifyToken.previewAccessToken(cookie)
                ?: error("Couldn't check this Spotify profile. Please reload and try again.")
            refreshHashes()
            Spotify.me().getOrThrow().also { currentCoroutineContext().ensureActive() }
        } finally {
            Spotify.accessToken = previousAccessToken
        }
    }

    suspend fun <T> call(mutation: Boolean = false, action: suspend () -> Result<T>): T = sessionLock.withLock {
        currentCoroutineContext().ensureActive()
        val cookie = AppSettings.spotifySpdcToken.value
        require(cookie.isNotBlank()) { "Connect Spotify first." }
        if (cookie != tokenCookie || System.currentTimeMillis() >= expires - 60_000) {
            Spotify.accessToken = null
            val result = SpotifyAuth.fetchAccessToken(cookie)
            currentCoroutineContext().ensureActive()
            val minted = result.getOrNull()
            val token = minted?.accessToken ?: com.music.audioly.data.canvas.SpotifyToken.accessToken()
                ?: error("Spotify could not refresh your session. Reconnect Spotify and try again.")
            check(cookie == AppSettings.spotifySpdcToken.value) { "Spotify account changed. Please retry." }
            Spotify.accessToken = token
            tokenCookie = cookie
            expires = minted?.accessTokenExpirationTimestampMs ?: (System.currentTimeMillis() + 20 * 60_000)
        }
        refreshHashes()
        var result = action()
        currentCoroutineContext().ensureActive()
        val failure = result.exceptionOrNull() as? Spotify.SpotifyException
        if (!mutation && failure?.statusCode == 412) { refreshHashes(force = true); result = action() }
        if (failure?.statusCode == 401) expires = 0
        currentCoroutineContext().ensureActive()
        check(cookie == AppSettings.spotifySpdcToken.value) { "Spotify account changed. Please retry." }
        result.getOrThrow()
    }

    private suspend fun refreshHashes(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - hashesUpdated < 6 * 60 * 60_000) return@withContext
        try {
            val req = Request.Builder().url("https://francescograzioso.github.io/Meld/spotify-gql-hashes.json").build()
            Http.client.newCall(req).execute().use { response ->
                check(response.isSuccessful)
                val text = response.body.string()
                applyHashes(text, SpotifyHashProvider.HashSource.REMOTE)
                prefs.edit().putString("hashes", text).apply()
            }
            hashesUpdated = System.currentTimeMillis()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            prefs.getString("hashes", null)?.let { runCatching { applyHashes(it, SpotifyHashProvider.HashSource.CACHED) } }
            hashesUpdated = System.currentTimeMillis() - 5 * 60 * 60_000
        }
    }
    private fun applyHashes(text: String, source: SpotifyHashProvider.HashSource) {
        val operations = json.parseToJsonElement(text).jsonObject["operations"]!!.jsonObject
        val entries = operations.mapNotNull { (operation, value) ->
            val obj = value.jsonObject
            val hash = obj["hash"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!Regex("[a-f0-9]{64}").matches(hash)) null else operation to SpotifyHashProvider.RemoteHashEntry(
                hash, obj["previous_hash"]?.jsonPrimitive?.contentOrNull?.takeIf { Regex("[a-f0-9]{64}").matches(it) })
        }.toMap()
        SpotifyHashProvider.updateHashes(entries, source)
    }

    @Serializable data class Profile(val updated: Long = 0, val tracks: List<SpotifyTrack> = emptyList(),
        val artists: List<SpotifyArtist> = emptyList(), val playlists: List<SpotifyPlaylist> = emptyList(),
        val releases: List<SpotifyAlbum> = emptyList(), val recentArtistIds: Set<String> = emptySet(),
        val trackWeights: Map<String, Float> = emptyMap(), val artistWeights: Map<String, Float> = emptyMap(), val homeSections: List<SpotifyHomeFeedSection> = emptyList())
    fun cachedProfile(): Profile? = (cache.get("profile_${account()}") ?: prefs.getString("profile_${account()}", null))?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() }
    suspend fun profile(force: Boolean = false): Profile {
        val key = account()
        val cached = cachedProfile()
        if (!force && cached != null && cached.homeSections.isNotEmpty() && System.currentTimeMillis() - cached.updated < 6 * 60 * 60_000) return cached
        return try { call {
            runCatching {
                val tracks = mutableListOf<SpotifyTrack>(); val artists = mutableListOf<SpotifyArtist>()
                val recentArtists = mutableSetOf<String>()
                val trackWeights = mutableMapOf<String, Float>()
                val artistWeights = mutableMapOf<String, Float>()
                val homeResult = Spotify.home(sectionItemsLimit = 50)
                var hasFreshData = homeResult.isSuccess
                val feed = (homeResult.getOrNull()?.sections?.takeIf { it.isNotEmpty() } ?: cached?.homeSections.orEmpty()).flatMap { it.items }
                val feedArtists = feed.filterIsInstance<SpotifyHomeFeedItem.Artist>().map {
                    SpotifyArtist(it.id, it.name, it.imageUrl?.let { url -> listOf(SpotifyImage(url)) }.orEmpty())
                }
                for (range in listOf("short_term", "medium_term", "long_term")) {
                    currentCoroutineContext().ensureActive()
                    val trackResult = Spotify.topTracks(range)
                    hasFreshData = hasFreshData || trackResult.isSuccess
                    val rangeTracks = trackResult.getOrNull()?.items.orEmpty()
                    val weight = when (range) { "short_term" -> 1.0f; "medium_term" -> 0.7f; else -> 0.4f }
                    rangeTracks.forEachIndexed { index, track -> trackWeights[track.id] = (trackWeights[track.id] ?: 0f) + weight * (1f - index.toFloat() / rangeTracks.size.coerceAtLeast(1)) }
                    tracks += rangeTracks
                    val artistResult = Spotify.topArtists(range)
                    hasFreshData = hasFreshData || artistResult.isSuccess
                    val rangeArtists = artistResult.getOrNull()?.items.orEmpty()
                    rangeArtists.forEachIndexed { index, artist -> artistWeights[artist.id] = (artistWeights[artist.id] ?: 0f) + weight * (1f - index.toFloat() / rangeArtists.size.coerceAtLeast(1)) }
                    artists += rangeArtists
                    if (range == "short_term") recentArtists += rangeArtists.map { it.id }
                }
                if (tracks.isEmpty()) tracks += Spotify.likedSongs().getOrNull()?.items.orEmpty().map { it.track }
                if (artists.isEmpty()) artists += Spotify.myArtists().getOrNull()?.items.orEmpty()
                artists += feedArtists
                val mergedArtists = artists.groupBy { it.id }.values.map { variants ->
                    variants.first().copy(images = variants.firstOrNull { it.images.isNotEmpty() }?.images.orEmpty(),
                        genres = variants.flatMap { it.genres }.distinct(), popularity = variants.firstNotNullOfOrNull { it.popularity })
                }
                val enrichment = coroutineScope {
                    val gate = kotlinx.coroutines.sync.Semaphore(4)
                    mergedArtists.filter { it.images.isEmpty() }.take(20).map { artist -> async {
                        gate.acquire()
                        try { Spotify.artist(artist.id).getOrNull()?.takeIf { it.images.isNotEmpty() }?.let { artist.id to it.images } }
                        finally { gate.release() }
                    } }.awaitAll().filterNotNull().toMap()
                }
                val enrichedArtists = mergedArtists.map { artist -> enrichment[artist.id]?.let { artist.copy(images = it) } ?: artist }
                val homePlaylists = feed.filterIsInstance<SpotifyHomeFeedItem.Playlist>().map {
                    SpotifyPlaylist(id = it.id, name = it.name, description = it.description,
                        images = it.imageUrl?.let { url -> listOf(SpotifyImage(url)) }.orEmpty())
                }
                val playlists = (homePlaylists + (Spotify.myPlaylists().getOrNull()?.items ?: cached?.playlists.orEmpty())).distinctBy { it.id }
                val feedAlbums = feed.filterIsInstance<SpotifyHomeFeedItem.Album>().map { item ->
                    SpotifyAlbum(id = item.id, name = item.name, artists = item.artists, albumType = item.albumType,
                        images = item.imageUrl?.let { listOf(SpotifyImage(it)) }.orEmpty())
                }
                val releases = (Spotify.newReleases().getOrNull()?.albums?.items.orEmpty() + feedAlbums).distinctBy { it.id }.ifEmpty { cached?.releases.orEmpty() }
                val profile = Profile(if (hasFreshData) System.currentTimeMillis() else cached?.updated ?: 0L, tracks.distinctBy { it.id }.ifEmpty { cached?.tracks.orEmpty() },
                    enrichedArtists.ifEmpty { cached?.artists.orEmpty() }, playlists, releases, recentArtists.ifEmpty { cached?.recentArtistIds.orEmpty() },
                    trackWeights.ifEmpty { cached?.trackWeights.orEmpty() }, artistWeights.ifEmpty { cached?.artistWeights.orEmpty() }, homeResult.getOrNull()?.sections?.takeIf { it.isNotEmpty() } ?: cached?.homeSections.orEmpty())
                check(profile.tracks.isNotEmpty() || profile.playlists.isNotEmpty() || profile.artists.isNotEmpty()) {
                    "Spotify did not return profile data. Try reconnecting or open your library directly."
                }
                currentCoroutineContext().ensureActive()
                check(key == account())
                cache.put("profile_$key", json.encodeToString(profile))
                profile
            }
        } } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (cached != null && key == account()) cached else throw e }
    }
    @Serializable data class Library(val updated: Long = 0, val playlists: List<SpotifyPlaylist> = emptyList(),
        val liked: List<SpotifyTrack> = emptyList(), val likedTotal: Int = 0)
    fun cachedLibrary(): Library? = cache.get("library_${account()}")?.let {
        runCatching { json.decodeFromString<Library>(it) }.getOrNull()
    }
    fun invalidateLibrary() {
        val key = "library_${account()}"
        cachedLibrary()?.let { cache.put(key, json.encodeToString(it.copy(updated = 0))) }
        libraryRevision.value++
    }
    suspend fun library(force: Boolean = false): Library = libraryLock.withLock {
        val key = account()
        val cached = cachedLibrary()
        if (!force && cached != null && System.currentTimeMillis() - cached.updated < 15 * 60_000) return@withLock cached
        val playlists = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        do {
            val page = call { Spotify.myPlaylists(limit = 50, offset = offset) }
            playlists += page.items
            val next = page.nextPageOffset() ?: break
            check(next > offset) { "Spotify library pagination did not advance. Please retry." }
            offset = next
            check(offset < 10_000) { "This library is too large to sync at once." }
        } while (true)
        val liked = call { Spotify.likedSongs(limit = 50) }
        check(key == account()) { "Spotify account changed. Please retry." }
        val result = Library(System.currentTimeMillis(), playlists.distinctBy { it.id }, liked.items.map { it.track }, liked.total)
        cache.put("library_$key", json.encodeToString(result))
        libraryRevision.value++
        result
    }

    @Serializable data class PlaylistSnapshot(val playlist: SpotifyPlaylist, val items: List<SpotifyPlaylistTrack>, val updated: Long)
    fun cachedPlaylist(id: String): PlaylistSnapshot? = cache.get("playlist_${account()}_$id")?.let {
        runCatching { json.decodeFromString<PlaylistSnapshot>(it) }.getOrNull()
    }
    suspend fun playlistSnapshot(id: String): PlaylistSnapshot {
        val key = account()
        val playlist = call { Spotify.playlist(id) }
        val items = playlistTracks(id)
        check(key == account()) { "Spotify account changed. Please retry." }
        return PlaylistSnapshot(playlist, items, System.currentTimeMillis()).also { cache.put("playlist_${key}_$id", json.encodeToString(it)) }
    }
    suspend fun playlistTracks(id: String, maxTracks: Int = 10_000): List<SpotifyPlaylistTrack> {
        val rows = mutableListOf<SpotifyPlaylistTrack>()
        var offset = 0
        do {
            val page = call { Spotify.playlistTracks(id, limit = 100, offset = offset) }
            require(page.total <= maxTracks) { "This flow supports up to $maxTracks tracks." }
            check(page.items.isNotEmpty() || offset >= page.total) { "Spotify returned an incomplete playlist. Retry." }
            rows += page.items; offset += page.items.size
            if (offset >= page.total) break
            check(offset < 10_000) { "This playlist is too large to load at once." }
        } while (true)
        return rows
    }

    @Serializable private data class Match(val videoId: String, val title: String, val artist: String,
        val image: String? = null, val duration: String? = null, val manual: Boolean = false) {
        fun song() = Song(videoId, title, artist, image, duration)
    }
    fun cachedMatch(track: SpotifyTrack): Song? =
        (prefs.getString("manual_${track.id}", null) ?: prefs.getString("match_${account()}_${track.id}", null))?.let {
            runCatching { json.decodeFromString<Match>(it).song() }.getOrNull()
        }
    suspend fun prepareMatches(tracks: List<SpotifyTrack>) = coroutineScope {
        val gate = kotlinx.coroutines.sync.Semaphore(2)
        tracks.take(12).distinctBy { it.id }.map { track -> launch {
            gate.acquire()
            try { resolve(track) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            finally { gate.release() }
        } }.joinAll()
    }
    suspend fun resolve(track: SpotifyTrack): Song = matchLocks[(track.id.hashCode() and Int.MAX_VALUE) % matchLocks.size].withLock {
        resolveUncached(track)
    }
    private suspend fun resolveUncached(track: SpotifyTrack): Song {
        require(track.id.isNotBlank()) { "This Spotify track has no usable identity." }
        val accountKey = account()
        val key = "match_${accountKey}_${track.id}"
        (prefs.getString("manual_${track.id}", null) ?: prefs.getString(key, null))?.let { runCatching { json.decodeFromString<Match>(it).song() }.getOrNull()?.let { song ->
            prefs.edit().putString("reverse_${account()}_${song.videoId}", track.id).apply()
            return song
        } }
        val candidates = YtMusicRepository.search("${track.name} ${track.artists.joinToString(" ") { it.name }}", SearchFilter.SONGS)
            .getOrThrow().mapNotNull { when (it) { is SearchResult.Track -> it.song; is SearchResult.TopTrack -> it.song; else -> null } }
        val song = TrackMatcher.best(candidates, TrackMatcher.Target(track.name, track.artists.joinToString(", ") { it.name },
            track.durationMs.takeIf { it > 0 }?.div(1000), track.album?.name, track.explicit))
            ?: error("No reliable match for ${track.name}. Use Fix match to choose a YouTube link.")
        check(accountKey == account()) { "Spotify account changed. Please retry." }
        prefs.edit().putString(key, json.encodeToString(Match(song.videoId, song.title, song.artist, song.thumbnailUrl, song.durationText))).apply()
        prefs.edit().putString("reverse_${account()}_${song.videoId}", track.id).apply()
        return song
    }
    fun overrideMatch(track: SpotifyTrack, link: String) {
        require(track.id.isNotBlank()) { "This Spotify track has no usable identity." }
        val id = youtubeId(link) ?: error("Paste a valid YouTube or YouTube Music song link.")
        prefs.edit().putString("manual_${track.id}", json.encodeToString(Match(id, track.name,
            track.artists.joinToString(", ") { it.name }, track.album?.images?.firstOrNull()?.url, manual = true))).apply()
    }
    fun clearMatch(track: SpotifyTrack) { prefs.edit().remove("match_${account()}_${track.id}").remove("manual_${track.id}").apply() }
    fun disconnect() {
        val key = account()
        cache.remove("profile_$key")
        cache.remove("library_$key")
        prefs.edit().remove("profile_$key").apply()
        AppSettings.setSpotifySpdcToken("")
        expires = 0L
        val manager = android.webkit.CookieManager.getInstance()
        for (host in listOf("open.spotify.com", "accounts.spotify.com")) {
            manager.setCookie("https://$host", "sp_dc=; Domain=.spotify.com; Path=/; Max-Age=0; Secure")
            manager.setCookie("https://$host", "sp_dc=; Path=/; Max-Age=0; Secure")
        }
        manager.flush()
    }
    fun youtubeId(link: String): String? = runCatching {
        val uri = URI(link.trim()); if (uri.scheme != "https" || uri.userInfo != null) return null
        val id = when (uri.host?.lowercase()) {
            "youtu.be" -> uri.path.removePrefix("/")
            "youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com" ->
                uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter('=')
            else -> null
        }
        id?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }.getOrNull()

    suspend fun recommendations(seed: SpotifyTrack): List<SpotifyTrack> {
        val taste = profile()
        return call {
            runCatching {
                SpotifyRecommendationEngine.invalidateProfile()
                SpotifyRecommendationEngine.getRecommendations(seed, limit = 20, profile = taste)
            }
        }
    }
}
