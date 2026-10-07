package com.music.audioly

import com.music.audioly.data.model.Song
import com.music.audioly.data.spotify.loadSpotifyQueue
import com.music.audioly.data.spotify.models.SpotifyTrack
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test

class SpotifyQueueLoaderTest {
    private fun track(id: String) = SpotifyTrack(id = id, name = id)
    private fun song(track: SpotifyTrack) = Song(track.id, track.name, "Artist", null)
    @Test fun `tail is appended only after asynchronous queue installation finishes`() = runBlocking {
        var installed = false
        val events = mutableListOf<String>()
        loadSpotifyQueue(listOf(track("before"), track("selected"), track("next"), track("last")), 1, false,
            resolve = { events += "resolve:${it.id}"; song(it) },
            recommend = { error("Playlist playback must not request radio") },
            start = { events += "start"; delay(20); installed = true; events += "installed" },
            append = { assertTrue(installed); events += "append:${it.videoId}"; true }, valid = { true })
        assertEquals(listOf("resolve:selected", "start", "installed", "resolve:next", "append:next", "resolve:last", "append:last"), events)
    }
    @Test fun `replacement playback prevents stale matched tracks entering queue`() = runBlocking {
        var valid = true
        val appended = mutableListOf<Song>()
        loadSpotifyQueue(listOf(track("first"), track("second"), track("third")), 0, false,
            resolve = { if (it.id == "second") valid = false; song(it) }, recommend = { emptyList() },
            start = {}, append = { appended += it; true }, valid = { valid })
        assertTrue(appended.isEmpty())
    }
    @Test fun `playlist duplicates preserve order and unmatched songs are counted`() = runBlocking {
        val appended = mutableListOf<String>()
        val missed = loadSpotifyQueue(listOf(track("a"), track("b"), track("missing"), track("b")), 0, false,
            resolve = { if (it.id == "missing") error("no match"); song(it) }, recommend = { emptyList() },
            start = {}, append = { appended += it.videoId; true }, valid = { true })
        assertEquals(listOf("b", "b"), appended)
        assertEquals(1, missed)
    }
    @Test fun `recommendations cannot delay the selected track starting`() = runBlocking {
        var started = false
        loadSpotifyQueue(listOf(track("a")), 0, true, resolve = { song(it) },
            recommend = { assertTrue(started); emptyList() }, start = { started = true }, append = { true }, valid = { true })
        assertTrue(started)
    }
}
