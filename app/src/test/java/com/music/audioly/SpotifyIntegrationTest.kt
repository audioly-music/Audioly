package com.music.audioly

import com.music.audioly.data.spotify.SpotifyAuth
import com.music.audioly.data.spotify.SpotifyHashProvider
import com.music.audioly.data.spotify.SpotifyIntegration
import com.music.audioly.data.spotify.SpotifyRecommendationEngine
import com.music.audioly.data.spotify.models.SpotifyTrack
import com.music.audioly.data.spotify.models.SpotifyPaging
import com.music.audioly.data.spotify.models.nextPageOffset
import com.music.audioly.data.spotify.displaySong
import com.music.audioly.data.spotify.homeShelves
import com.music.audioly.data.spotify.playbackSong
import com.music.audioly.data.model.Song
import com.music.audioly.data.spotify.models.SpotifyArtist
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SpotifyIntegrationTest {
    @Test fun `personalized home preserves every section title order and cached item identity`() {
        val artist = com.music.audioly.data.spotify.models.SpotifyHomeFeedItem.Artist("spotify:artist:abc", "abc", "Artist", "https://example.com/art.jpg")
        val sections = listOf("Jump back in", "Your daily mixes", "More like Artist", "Recently played", "Discover something new").mapIndexed { index, title ->
            com.music.audioly.data.spotify.models.SpotifyHomeFeedSection("section:$index", title, "Music", 1, listOf(artist))
        }
        val profile = SpotifyIntegration.Profile(homeSections = sections)
        val restored = Json.decodeFromString<SpotifyIntegration.Profile>(Json.encodeToString(profile))
        assertEquals(profile, restored)
        assertEquals(sections.map { it.title }, restored.homeShelves().map { it.title })
        assertTrue(restored.homeShelves().all { it.items.single().browseId == "spotify:artist:abc" && it.items.single().videoId == null })
    }
    @Test fun `legacy profile uses fallback shelves until personalized feed is fetched`() {
        val legacy = Json.decodeFromString<SpotifyIntegration.Profile>("""{"tracks":[],"playlists":[],"artists":[],"releases":[]}""")
        assertTrue(legacy.homeSections.isEmpty())
        assertTrue(legacy.homeShelves().isEmpty())
    }

    @Test fun `playback keeps the resolved audio id and Spotify display identity`() {
        val track = SpotifyTrack(id = "spotify-id", name = "Catalog title")
        val result = track.playbackSong(Song("abcdefghijk", "Video title", "Artist", null))
        assertEquals("abcdefghijk", result.videoId)
        assertEquals("Catalog title", result.title)
        try {
            track.playbackSong(track.displaySong())
            fail("Display-only Spotify ids must never enter playback")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun `native rows retain playlist entry identity without pretending to be playable`() {
        val track = SpotifyTrack(id = "track123", name = "Song", durationMs = 183000, explicit = true)
        val first = track.displaySong("entry-one")
        val second = track.displaySong("entry-two")
        assertEquals("spotify:track:track123", first.videoId)
        assertEquals(first.videoId, second.videoId)
        assertNotEquals(first.setVideoId, second.setVideoId)
        assertEquals("3:03", first.durationText)
        assertEquals(true, first.isExplicit)
    }
    @Test fun `native home artist cards navigate as artists rather than audio`() {
        val profile = SpotifyIntegration.Profile(artists = listOf(SpotifyArtist(id = "artist123", name = "Artist")))
        val card = profile.homeShelves().single().items.single()
        assertEquals("spotify:artist:artist123", card.browseId)
        assertNull(card.videoId)
        assertEquals("Artist", card.title)
    }
    @Test fun `library pages advance past non-playlist wrappers without repeating rows`() {
        val page = SpotifyPaging(items = listOf("playlist"), total = 53, offset = 0, consumed = 50)
        assertEquals(50, page.nextPageOffset())
        val filtered = SpotifyPaging<String>(total = 53, offset = 50, consumed = 3)
        assertNull(filtered.nextPageOffset())
        assertEquals(50, SpotifyPaging<String>(total = 60, offset = 0, consumed = 50).nextPageOffset())
    }
    @Test fun `truncated source page fails instead of looping forever`() {
        try {
            SpotifyPaging<String>(total = 53, offset = 50, consumed = 0).nextPageOffset()
            fail("Expected an incomplete-page failure")
        } catch (_: IllegalStateException) { }
    }
    @Test fun `unavailable popularity does not penalize graphQL tracks`() {
        val engine = SpotifyRecommendationEngine
        assertNull(engine.popularitySimilarity(70, null))
        assertNull(engine.popularitySimilarity(null, 70))
        assertEquals(0.8f, engine.blendPopularity(0.8f, null), 0.0001f)
        val close = engine.blendPopularity(0.8f, engine.popularitySimilarity(70, 72))
        val distant = engine.blendPopularity(0.8f, engine.popularitySimilarity(70, 10))
        assertTrue(close > distant)
    }
    @Test fun `old profile snapshots load without fabricating recent favorites`() {
        val legacy = Json.decodeFromString<SpotifyIntegration.Profile>("""{"updated":123,"tracks":[],"artists":[],"playlists":[],"releases":[]}""")
        assertEquals(123L, legacy.updated)
        assertTrue(legacy.recentArtistIds.isEmpty())
        assertTrue(legacy.artistWeights.isEmpty())
        val enriched = legacy.copy(recentArtistIds = setOf("short-term-artist"), artistWeights = mapOf("short-term-artist" to 1.7f), trackWeights = mapOf("track" to 0.7f))
        assertEquals(enriched, Json.decodeFromString<SpotifyIntegration.Profile>(Json.encodeToString(enriched)))
    }
    @Test fun `library snapshots preserve total likes separately from first page`() {
        val snapshot = SpotifyIntegration.Library(updated = 123, liked = listOf(SpotifyTrack(id = "first-page-track")), likedTotal = 153)
        val restored = Json.decodeFromString<SpotifyIntegration.Library>(Json.encodeToString(snapshot))
        assertEquals(153, restored.likedTotal)
        assertEquals(1, restored.liked.size)
        assertEquals("first-page-track", restored.liked.single().id)
    }
    @Test fun `manual matches only accept YouTube video links`() {
        val id = "abcdefghijk"
        assertEquals(id, SpotifyIntegration.youtubeId("https://youtu.be/$id?si=abc"))
        assertEquals(id, SpotifyIntegration.youtubeId("https://music.youtube.com/watch?v=$id&list=123"))
        for (url in listOf("https://youtube.com.evil.test/watch?v=$id", "https://attacker@youtube.com/watch?v=$id",
            "http://youtube.com/watch?v=$id", "https://youtube.com/watch?v=short", "https://spotify.com/watch?v=$id")) {
            assertNull(url, SpotifyIntegration.youtubeId(url))
        }
    }
    @Test fun `TOTP matches RFC 6238 SHA1 vectors truncated to six digits`() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("287082", SpotifyAuth.generateTotp(secret, 59))
        assertEquals("081804", SpotifyAuth.generateTotp(secret, 1111111109))
        assertEquals("050471", SpotifyAuth.generateTotp(secret, 1111111111))
    }
    @Test fun `query hash registry covers all Spotify operations`() {
        val operations = listOf("profileAttributes", "libraryV3", "fetchPlaylist", "fetchLibraryTracks", "searchDesktop",
            "queryArtistOverview", "getAlbum", "queryWhatsNewFeed", "home", "addToPlaylist", "removeFromPlaylist",
            "moveItemsInPlaylist", "editPlaylistAttributes", "addToLibrary", "removeFromLibrary")
        operations.forEach { assertTrue(it, Regex("[a-f0-9]{64}").matches(SpotifyHashProvider.getHash(it))) }
    }
    @Test fun `Spotify metadata preserves track identity through caching`() {
        val json = Json { ignoreUnknownKeys = true }
        val track = json.decodeFromString<SpotifyTrack>("""{"id":"spotify-id","name":"Song","duration_ms":183500,
            "explicit":true,"artists":[{"id":"artist-id","name":"Artist"}],"unknown":123}""")
        assertEquals("spotify-id", track.id)
        assertEquals(183500, track.durationMs)
        assertTrue(track.explicit)
        assertEquals("artist-id", track.artists.single().id)
    }
}
