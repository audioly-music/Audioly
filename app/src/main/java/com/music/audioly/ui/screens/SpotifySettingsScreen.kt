package com.music.audioly.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.music.audioly.data.settings.AppSettings
import com.music.audioly.data.spotify.SpotifyIntegration as Integration
import com.music.audioly.ui.components.SpotifyImportLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SpotifySettingsScreen(onClose: () -> Unit) {
    val cookie by AppSettings.spotifySpdcToken.collectAsState()
    val home by Integration.homeEnabled.collectAsState()
    val search by Integration.searchEnabled.collectAsState()
    val only by Integration.spotifyOnly.collectAsState()
    val queue by Integration.smartQueue.collectAsState()
    val library by Integration.libraryEnabled.collectAsState()
    val likes by Integration.syncLikes.collectAsState()
    val hideLikes by Integration.hideYouTubeLikes.collectAsState()
    val canvas by AppSettings.animatedCanvas.collectAsState()
    val cellular by AppSettings.canvasOverCellular.collectAsState()
    var login by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var disconnect by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Spotify", style = MaterialTheme.typography.headlineLarge)
                TextButton(onClick = onClose) { Text("Done") }
            }
            SettingsGroup(header = "Account") {
                SettingsRow(icon = Icons.Rounded.AccountCircle, title = if (cookie.isBlank()) "Connect Spotify" else "Spotify connected",
                    subtitle = if (cookie.isBlank()) "Sign in to use your playlists and listening profile" else "Reconnect or switch your Spotify account",
                    onClick = { login = true })
                if (cookie.isNotBlank()) {
                    RowDivider()
                    SettingsRow(icon = Icons.Rounded.Logout, title = "Disconnect Spotify", onClick = { disconnect = true })
                }
            }
            SettingsGroup(header = "Sources", footer = "Spotify supplies the music catalog. Audioly finds playable audio on YouTube Music.") {
                SpotifySetting(Icons.Rounded.Search, "Spotify as search source", "Search Spotify songs, artists, albums and playlists", search) { Integration.settings(search = it) }
                RowDivider()
                SpotifySetting(Icons.Rounded.Home, "Spotify as home source", "Your top tracks, artists, playlists and new releases", home) { Integration.settings(home = it, only = only && it) }
                RowDivider()
                SpotifySetting(Icons.Rounded.FilterAlt, "Spotify-only mode", "Show only Spotify-powered sections on Home", only) { Integration.settings(only = it, home = home || it) }
            }
            SettingsGroup(header = "Library & recommendations") {
                SpotifySetting(Icons.Rounded.LibraryMusic, "Spotify library sync", "Show your Spotify playlists and liked songs in Library", library, Integration::setLibraryEnabled)
                RowDivider()
                SpotifySetting(Icons.Rounded.AutoAwesome, "Smart queue generation", "Build stations using your listening history, artist affinity, genres and available popularity data", queue, Integration::setSmartQueue)
                RowDivider()
                SpotifySetting(Icons.Rounded.Favorite, "Sync Audioly likes to Spotify", "For matched Spotify songs, send Audioly heart changes to Spotify", likes, Integration::setSyncLikes)
                RowDivider()
                SpotifySetting(Icons.Rounded.VisibilityOff, "Hide YouTube Liked Music", "Keep Spotify liked songs as your visible liked collection", hideLikes, Integration::setHideYouTubeLikes)
                RowDivider()
                SettingsRow(icon = Icons.Rounded.Sync, title = "Refresh Spotify data", subtitle = "Refresh your library and listening profile",
                    enabled = cookie.isNotBlank() && !busy, onClick = {
                        busy = true; status = null
                        scope.launch {
                            try {
                                val result = Integration.library(force = true)
                                val profile = Integration.profile(force = true)
                                status = "Synced ${result.playlists.size} playlists and ${result.likedTotal} liked songs." +
                                    if (System.currentTimeMillis() - profile.updated > 60_000) " Home is using your saved profile until Spotify is available." else ""
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { status = e.message ?: "Could not refresh Spotify. Try again." }
                            finally { busy = false }
                        }
                    })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            status?.let { Text(it, Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium) }
            SettingsGroup(header = "Playback & browsing") {
                SettingsRow(icon = Icons.Rounded.Link, title = "Spotify-to-YouTube matching", subtitle = "Automatic title, artist and duration matching. Resolved tracks are cached on this device.")
                RowDivider()
                SettingsRow(icon = Icons.Rounded.Edit, title = "Manual match override", subtitle = "Open a Spotify song's menu and choose Fix playback match. Paste the correct YouTube link; your saved correction takes priority, including after reconnecting.")
                RowDivider()
                SettingsRow(icon = Icons.Rounded.Album, title = "Spotify album browsing", subtitle = "Tap an album in Home or Search to see its full tracklist, release details and Play all.")
                RowDivider()
                SettingsRow(icon = Icons.Rounded.Person, title = "Artist navigation", subtitle = "Tap a Spotify artist to open their YouTube Music artist page.")
            }
            SettingsGroup(header = "Cache & artwork") {
                SettingsRow(icon = Icons.Rounded.Storage, title = "Hybrid profile cache", subtitle = "Open Home quickly using your saved Spotify profile. Keep your last successful update when Spotify is unavailable; missing artist artwork fills in automatically.")
                RowDivider()
                SpotifySetting(Icons.Rounded.Animation, "Animated cover art", "Use Spotify Canvas when available", canvas, AppSettings::setAnimatedCanvas)
                if (canvas) {
                    RowDivider()
                    SpotifySetting(Icons.Rounded.NetworkCell, "Canvas over cellular", "Allow animated artwork on mobile data", cellular, AppSettings::setCanvasOverCellular)
                }
            }
        }
    }
    if (login) SpotifyImportLogin(onDismiss = { login = false }, onConnected = { login = false; status = "Connected. Your Spotify collections will appear in Library." })
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect Spotify?") },
        text = { Text("Your Spotify playlists stay on Spotify. Saved playback corrections stay on this device.") },
        confirmButton = { TextButton(onClick = { Integration.disconnect(); Integration.settings(false, false, false); Integration.setSyncLikes(false); Integration.setHideYouTubeLikes(false); disconnect = false }) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}

@Composable
private fun SpotifySetting(icon: ImageVector, title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    SettingsRow(icon = icon, title = title, subtitle = subtitle, onClick = { change(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = change) })
}
