package com.music.audioly.data.playlistimport

import com.music.audioly.data.Http
import com.music.audioly.data.YtMusicRepository
import com.music.audioly.data.model.SearchFilter
import com.music.audioly.data.model.SearchResult
import com.music.audioly.data.model.Song
import com.music.audioly.data.settings.AppSettings
import com.music.audioly.data.sources.TrackMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Metadata only: audio is resolved by Audioly after the user reviews matches. */
internal object PlaylistImport {
    const val MAX_TRACKS = 500
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy { Http.client.newBuilder().callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build() }
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    enum class Provider { SPOTIFY, APPLE }
    data class Link(val provider: Provider, val id: String, val url: String)
    data class Track(val title: String, val artist: String, val seconds: Int? = null)
    data class Playlist(val name: String, val tracks: List<Track>, val warning: String? = null)
    data class Match(val source: Track, val song: Song?)

    fun parseLink(input: String): Link {
        val raw = input.trim()
        if (Regex("spotify:playlist:[A-Za-z0-9]{22}").matches(raw)) {
            val id = raw.substringAfterLast(':')
            return Link(Provider.SPOTIFY, id, "https://open.spotify.com/playlist/$id")
        }
        val uri = runCatching { URI(raw) }.getOrNull()
            ?: error("Paste a complete Spotify or Apple Music playlist link.")
        require(uri.scheme == "https" && uri.userInfo == null && uri.port == -1) {
            "Use an HTTPS playlist link from Spotify or Apple Music."
        }
        val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        return when (uri.host?.lowercase()) {
            "open.spotify.com" -> {
                val index = parts.indexOf("playlist")
                val id = parts.getOrNull(index + 1).orEmpty()
                require(index in 0..1 && parts.size == index + 2 && Regex("[A-Za-z0-9]{22}").matches(id)) {
                    "This is not a Spotify playlist link. Use Share → Copy link on the playlist."
                }
                Link(Provider.SPOTIFY, id, "https://open.spotify.com/playlist/$id")
            }
            "music.apple.com" -> {
                val id = parts.lastOrNull().orEmpty()
                require(parts.size in 3..4 && parts[1] == "playlist" &&
                    Regex("[a-zA-Z]{2}").matches(parts[0]) && Regex("pl\\.[A-Za-z0-9.-]+").matches(id)) {
                    "This is not a public Apple Music playlist link."
                }
                Link(Provider.APPLE, id, "https://music.apple.com/" + parts.joinToString("/"))
            }
            else -> error("Use the full open.spotify.com or music.apple.com playlist link, not a shortened link.")
        }
    }

    suspend fun load(input: String): Playlist = withContext(Dispatchers.IO) {
        val link = parseLink(input)
        currentCoroutineContext().ensureActive()
        when (link.provider) {
            Provider.APPLE -> parseApple(get(link.url))
            Provider.SPOTIFY -> {
                if (AppSettings.spotifySpdcToken.value.isNotBlank()) {
                    val details = com.music.audioly.data.spotify.SpotifyIntegration.call {
                        com.music.audioly.data.spotify.Spotify.playlist(link.id)
                    }
                    val rows = com.music.audioly.data.spotify.SpotifyIntegration.playlistTracks(link.id, MAX_TRACKS)
                    val tracks = rows.mapNotNull { it.track }.filter { it.name.isNotBlank() && !it.isLocal }.map {
                        Track(it.name, it.artists.joinToString(", ") { artist -> artist.name }, it.durationMs.takeIf { ms -> ms > 0 }?.div(1000))
                    }
                    require(tracks.isNotEmpty()) { "No importable songs were found in this playlist." }
                    Playlist(details.name, tracks, if (tracks.size != rows.size) "${rows.size - tracks.size} unavailable items were skipped." else null)
                } else parseSpotifyEmbed(get("https://open.spotify.com/embed/playlist/${link.id}"))
            }
        }
    }

    private fun get(url: String): String = request(Request.Builder().url(url).header("User-Agent", UA).build())
    private fun request(request: Request): String = client.newCall(request).execute().use { response ->
        check(response.isSuccessful) {
            when(response.code) {
                401, 403 -> "Playlist access was denied. Check the playlist visibility and your Spotify connection."
                404 -> "Playlist not found. Check the link and whether it is public or belongs to your signed-in Spotify account."
                429 -> "The music service is limiting requests. Wait a little and try again."
                in 300..399 -> "The playlist redirected. Open it in your browser and copy the final playlist URL."
                else -> "The music service could not load this playlist (HTTP ${response.code})."
            }
        }
        val body = response.body ?: error("The music service returned an empty response.")
        check(body.contentLength() <= 10_000_000) { "This playlist page is too large to import." }
        val buffer = okio.Buffer()
        val source = body.source()
        while (buffer.size <= 10_000_000L) {
            if (source.read(buffer, minOf(8192L, 10_000_001L - buffer.size)) == -1L) break
        }
        check(buffer.size <= 10_000_000L) { "This playlist page is too large to import." }
        buffer.readUtf8()
    }

    internal fun parseSpotifyTrack(raw: JsonObject): Track? {
        val data = raw.obj("trackV2")?.obj("data") ?: raw
        if (data.text("__typename").let { it.isNotEmpty() && it != "Track" }) return null
        val name = data.text("name")
        val artists = data.obj("artists")?.array("items").orEmpty().mapNotNull {
            (it as? JsonObject)?.obj("profile")?.text("name")?.takeIf(String::isNotBlank)
        }.joinToString(", ")
        if (name.isBlank() || artists.isBlank()) return null
        val duration = data.obj("duration") ?: data.obj("trackDuration")
        val milliseconds = (duration?.get("totalMilliseconds") as? JsonPrimitive)?.intOrNull
            ?: (duration?.get("totalMs") as? JsonPrimitive)?.intOrNull
        return Track(name, artists, milliseconds?.takeIf { it > 0 }?.div(1000))
    }

    fun parseSpotifyEmbed(html: String): Playlist {
        val doc = Jsoup.parse(html)
        val script = doc.selectFirst("script#__NEXT_DATA__")?.data()
            ?: error("Spotify did not expose this playlist. Connect Spotify for private playlists.")
        val entity = json.parseToJsonElement(script).jsonObject.obj("props")?.obj("pageProps")
            ?.obj("state")?.obj("data")?.obj("entity") ?: error("Spotify's public playlist format has changed.")
        val tracks = entity.array("trackList").mapNotNull {
            val row = it as? JsonObject ?: return@mapNotNull null
            val title = row.text("title"); val artist = row.text("subtitle")
            if (title.isBlank() || artist.isBlank()) null else Track(title, artist,
                row["duration"]?.jsonPrimitive?.intOrNull?.div(1000))
        }
        require(tracks.isNotEmpty()) { "No songs are visible. Connect Spotify or make the playlist public." }
        require(tracks.size <= MAX_TRACKS) { "Import supports up to $MAX_TRACKS tracks." }
        return Playlist(entity.text("name").ifBlank { "Spotify playlist" }, tracks,
            "Spotify's public preview may contain only part of the playlist. Connect Spotify for the full list, or import only the tracks shown below.")
    }

    fun parseApple(html: String): Playlist {
        val doc = Jsoup.parse(html)
        val schemas = doc.select("script[type=application/ld+json]").mapNotNull {
            runCatching { json.parseToJsonElement(it.data()) as? JsonObject }.getOrNull()
        }
        val schema = schemas.firstOrNull { it.text("@type") == "MusicPlaylist" }
            ?: error("Apple Music did not expose a public playlist. Private Apple Music playlists are not supported by this importer.")
        val serialized = doc.selectFirst("script#serialized-server-data")?.data()
            ?: error("Apple Music did not include its song list.")
        val rows = mutableListOf<JsonObject>()
        fun visit(value: JsonElement) {
            when(value) {
                is JsonObject -> if (value.text("artistName").isNotBlank() && value.text("title").isNotBlank() && value.containsKey("playAction")) rows += value
                    else value.values.forEach(::visit)
                is JsonArray -> value.forEach(::visit)
                else -> Unit
            }
        }
        visit(json.parseToJsonElement(serialized))
        val durationByTitle = schema.array("track").mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            row.text("name") to runCatching { Duration.parse(row.text("duration")).seconds.toInt() }.getOrNull()
        }.toMap()
        val tracks = rows.map { Track(it.text("title"), it.text("artistName"), durationByTitle[it.text("title")]) }
        require(tracks.isNotEmpty()) { "No public songs were found in this Apple Music playlist." }
        require(tracks.size <= MAX_TRACKS) { "Import supports up to $MAX_TRACKS tracks." }
        val expected = schema["numTracks"]?.jsonPrimitive?.intOrNull
        return Playlist(schema.text("name").ifBlank { "Apple Music playlist" }, tracks,
            if (expected == null || expected != tracks.size) "Apple Music exposed ${tracks.size} tracks${expected?.let { " out of $it" }.orEmpty()}. Only the tracks shown will be imported." else null)
    }

    suspend fun match(playlist: Playlist, progress: (Int, Int) -> Unit): List<Match> {
        val results = mutableListOf<Match>()
        for ((index, track) in playlist.tracks.withIndex()) {
            currentCoroutineContext().ensureActive()
            val target = TrackMatcher.Target(track.title, track.artist, track.seconds)
            val candidates = YtMusicRepository.search("${track.title} ${track.artist}", SearchFilter.SONGS).getOrThrow()
                .mapNotNull { when(it) { is SearchResult.Track -> it.song; is SearchResult.TopTrack -> it.song; else -> null } }
            results += Match(track, TrackMatcher.best(candidates, target))
            progress(index + 1, playlist.tracks.size)
        }
        return results
    }
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}
