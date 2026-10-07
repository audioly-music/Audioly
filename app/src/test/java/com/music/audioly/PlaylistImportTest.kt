package com.music.audioly

import com.music.audioly.data.playlistimport.PlaylistImport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PlaylistImportTest {
    private val id = "37i9dQZF1DXcBWIGoYBM5M"

    @Test fun `spotify links strip tracking and accept locale and URI`() {
        for (link in listOf("https://open.spotify.com/playlist/$id?si=tracking",
            "https://open.spotify.com/intl-en/playlist/$id", "spotify:playlist:$id")) {
            assertEquals("https://open.spotify.com/playlist/$id", PlaylistImport.parseLink(link).url)
        }
    }

    @Test fun `reject arbitrary hosts credentials non playlists and malformed links`() {
        for (link in listOf("http://open.spotify.com/playlist/$id", "https://open.spotify.com.evil.test/playlist/$id",
            "https://evil.test@open.spotify.com/playlist/$id", "https://open.spotify.com:8443/playlist/$id",
            "https://open.spotify.com/track/$id", "https://open.spotify.com/playlist/short",
            "https://music.apple.com/us/album/example/123", "https://spotify.link/abc", "not a url")) {
            assertTrue(link, runCatching { PlaylistImport.parseLink(link) }.isFailure)
        }
    }

    @Test fun `apple shared links supported`() {
        val link = PlaylistImport.parseLink("https://music.apple.com/in/playlist/my-mix/pl.u-123Ab?l=en")
        assertEquals(PlaylistImport.Provider.APPLE, link.provider)
        assertEquals("pl.u-123Ab", link.id)
    }

    @Test fun `spotify public preview preserves order and warns it may be incomplete`() {
        val parsed = PlaylistImport.parseSpotifyEmbed("""<script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{"name":"My mix","trackList":[
            {"title":"First","subtitle":"Artist A","duration":185000},
            {"title":"Second","subtitle":"Artist B","duration":200000}]}}}}}}</script>""")
        assertEquals(listOf("First", "Second"), parsed.tracks.map { it.title })
        assertEquals(185, parsed.tracks.first().seconds)
        assertNotNull(parsed.warning)
    }

    @Test fun `private or changed public page fails rather than importing empty`() {
        assertTrue(runCatching { PlaylistImport.parseSpotifyEmbed("<html>Sign in</html>") }.isFailure)
        assertTrue(runCatching { PlaylistImport.parseApple("<html>Sign in</html>") }.isFailure)
    }

    @Test fun `spotify session tracks preserve artists and reject episodes`() {
        val data = Json.parseToJsonElement("""{"__typename":"Track","name":"Song",
            "artists":{"items":[{"profile":{"name":"A"}},{"profile":{"name":"B"}}]},
            "duration":{"totalMilliseconds":201234}}""").jsonObject
        assertEquals(PlaylistImport.Track("Song", "A, B", 201), PlaylistImport.parseSpotifyTrack(data))
        val current = Json.parseToJsonElement(data.toString().replace("\"duration\"", "\"trackDuration\"")).jsonObject
        assertEquals(201, PlaylistImport.parseSpotifyTrack(current)?.seconds)
        assertNull(PlaylistImport.parseSpotifyTrack(Json.parseToJsonElement("""{"__typename":"Episode","name":"Podcast"}""").jsonObject))
    }

    private fun apple(total: Int) = """<script type="application/ld+json">
        {"@type":"MusicPlaylist","name":"Apple mix","numTracks":$total,
        "track":[{"name":"Song A","duration":"PT3M10S"}]}</script>
        <script id="serialized-server-data" type="application/json">
        [{"data":{"sections":[{"items":[{"title":"Song A","artistName":"Artist A","playAction":{}},
        {"title":"Album recommendation","artistName":"Artist B"}]}]}}]</script>"""

    @Test fun `apple parser ignores recommendations and checks completeness`() {
        val complete = PlaylistImport.parseApple(apple(1))
        assertEquals(listOf(PlaylistImport.Track("Song A", "Artist A", 190)), complete.tracks)
        assertNull(complete.warning)
        assertTrue(PlaylistImport.parseApple(apple(5)).warning!!.contains("1 tracks out of 5"))
    }

    @Test fun `captured live Apple response can be validated without network`() {
        val path = System.getenv("AUDIOLY_APPLE_PLAYLIST_FIXTURE")
        assumeTrue(!path.isNullOrBlank())
        val parsed = PlaylistImport.parseApple(File(path!!).readText())
        assertTrue(parsed.tracks.size > 1)
        assertTrue(parsed.tracks.all { it.title.isNotBlank() && it.artist.isNotBlank() })
        assertNull(parsed.warning)
    }

    @Test fun `captured live Spotify public preview can be validated without network`() {
        val path = System.getenv("AUDIOLY_SPOTIFY_PLAYLIST_FIXTURE")
        assumeTrue(!path.isNullOrBlank())
        val parsed = PlaylistImport.parseSpotifyEmbed(File(path!!).readText())
        assertTrue(parsed.tracks.size > 1)
        assertTrue(parsed.tracks.all { it.title.isNotBlank() && it.artist.isNotBlank() })
        assertNotNull(parsed.warning)
    }
}
