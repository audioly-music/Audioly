package com.music.audioly.data.spotify

import com.music.audioly.data.model.Song
import com.music.audioly.data.spotify.models.SpotifyTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Queue installation must finish before any tail is checked or appended. */
internal suspend fun loadSpotifyQueue(
    tracks: List<SpotifyTrack>, index: Int, radio: Boolean,
    resolve: suspend (SpotifyTrack) -> Song,
    recommend: suspend (SpotifyTrack) -> List<SpotifyTrack>,
    start: suspend (Song) -> Unit,
    append: suspend (Song) -> Boolean,
    valid: () -> Boolean,
): Int {
    val selected = tracks.getOrNull(index) ?: return 0
    val first = resolve(selected)
    if (!valid()) return 0
    start(first)
    if (!valid()) return 0
    val remaining = if (radio) {
        try { recommend(selected).filter { it.id != selected.id } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
    } else tracks.drop(index + 1)
    var missed = 0
    for (track in remaining) {
        currentCoroutineContext().ensureActive()
        if (!valid()) break
        val song = try { resolve(track) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { missed++; continue }
        if (!valid() || !append(song)) break
    }
    return missed
}
