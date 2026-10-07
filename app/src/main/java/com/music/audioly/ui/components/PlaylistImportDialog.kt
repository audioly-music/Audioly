package com.music.audioly.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.audioly.data.model.Song
import com.music.audioly.data.playlistimport.PlaylistImport
import com.music.audioly.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** No playlist is written until the listener reviews and confirms the selected matches. */
@Composable
internal fun PlaylistImportDialog(
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, List<Song>, (Result<String>) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cookie by AppSettings.spotifySpdcToken.collectAsState()
    var input by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<PlaylistImport.Playlist?>(null) }
    var matches by remember { mutableStateOf<List<PlaylistImport.Match>?>(null) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun runTask(task: suspend () -> Unit) {
        busy = true
        error = null
        job = scope.launch {
            try { task() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not import this playlist. Please try again." }
            finally { busy = false; progress = "" }
        }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !saving, dismissOnClickOutside = false,
    )) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(12.dp),
            shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("Import playlist", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("Bring your songs into Audioly. Review the matches, then save a private playlist to your signed-in YouTube Music library.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!signedIn && !saved) item {
                        Text("Sign in to Audioly before starting so you can save the imported playlist.")
                        TextButton(onClick = onSignIn, enabled = !busy && !saving) { Text("Sign in to Audioly") }
                    }
                    if (playlist == null) {
                        item {
                            OutlinedTextField(input, { input = it }, label = { Text("Playlist link") },
                                placeholder = { Text("https://open.spotify.com/playlist/…") },
                                modifier = Modifier.fillMaxWidth(), enabled = !busy, maxLines = 3)
                        }
                        item {
                            Text("Spotify: connect your account for full and private playlists. Without a connection, only the public preview is available.\n\nApple Music: public playlist links only.",
                                style = MaterialTheme.typography.bodySmall)
                            if (cookie.isBlank()) {
                                OutlinedButton(onClick = { login = true }, enabled = !busy) { Text("Connect Spotify") }
                            } else {
                                Text("Spotify connected", color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = {
                                    AppSettings.setSpotifySpdcToken("")
                                    clearSpotifySessionCookie()
                                }, enabled = !busy) { Text("Disconnect Spotify") }
                            }
                        }
                        item {
                            Button(onClick = {
                                progress = "Reading playlist…"
                                runTask {
                                    val loaded = PlaylistImport.load(input)
                                    playlist = loaded
                                    name = loaded.name
                                }
                            }, enabled = input.isNotBlank() && !busy && signedIn) { Text("Read playlist") }
                        }
                    }
                    playlist?.let { source ->
                        item {
                            OutlinedTextField(name, { name = it }, label = { Text("Playlist name") },
                                modifier = Modifier.fillMaxWidth(), enabled = !busy && !saving && !saved, singleLine = true)
                            Text("${source.tracks.size} source songs", style = MaterialTheme.typography.labelLarge)
                        }
                        source.warning?.let { warning -> item {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                                Text(warning, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        } }
                        if (matches == null && !saved) {
                            item {
                                Button(onClick = {
                                    runTask {
                                        val result = PlaylistImport.match(source) { count, total -> progress = "Matching $count of $total…" }
                                        matches = result
                                        selected = result.indices.filter { result[it].song != null }.toSet()
                                    }
                                }, enabled = !busy) { Text("Find matching songs") }
                            }
                            itemsIndexed(source.tracks) { index, track ->
                                Text("${index + 1}. ${track.title}\n${track.artist}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    matches?.let { result ->
                        item {
                            val missing = result.count { it.song == null }
                            Text("${selected.size} selected · $missing unmatched", style = MaterialTheme.typography.titleMedium)
                            Text("Check the recording and artist below. Unmatched songs will be skipped.", style = MaterialTheme.typography.bodySmall)
                        }
                        itemsIndexed(result) { index, match ->
                            Row(Modifier.fillMaxWidth().toggleable(value = index in selected,
                                enabled = match.song != null && !saving && !saved, role = Role.Checkbox,
                                onValueChange = { checked -> selected = if (checked) selected + index else selected - index })
                                .padding(vertical = 8.dp)) {
                                Checkbox(checked = index in selected, onCheckedChange = null, enabled = match.song != null && !saving && !saved)
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(match.source.title, style = MaterialTheme.typography.titleSmall)
                                    Text(match.source.artist, style = MaterialTheme.typography.bodySmall)
                                    Text(match.song?.let { "Match: ${it.title} · ${it.artist}" } ?: "No reliable match found",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text(progress.ifBlank { "Finding matches…" }, style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
                if (saved) Text("Playlist saved to your library.", color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (busy) job?.cancel() else onDismiss() }, enabled = !saving) {
                        Text(if (busy) "Cancel" else if (saved) "Done" else "Close")
                    }
                    if (playlist != null && !saved) TextButton(onClick = {
                        playlist = null; matches = null; selected = emptySet(); error = null
                    }, enabled = !busy && !saving) { Text("Start over") }
                    if (matches != null && !saved) Button(onClick = {
                        val songs = matches.orEmpty().mapIndexedNotNull { i, row -> row.song?.takeIf { i in selected } }
                        saving = true
                        error = null
                        onSave(name.trim(), songs) { result ->
                            saving = false
                            result.fold(onSuccess = { saved = true }, onFailure = {
                                error = "Could not confirm the save. Check your library before retrying to avoid a duplicate playlist."
                            })
                        }
                    }, enabled = !saving && !busy && selected.isNotEmpty() && name.isNotBlank() && signedIn) {
                        Text(if (saving) "Saving…" else "Save")
                    }
                }
            }
        }
    }
    if (login) SpotifyImportLogin(onDismiss = { login = false }, onConnected = { login = false })
}

private fun clearSpotifySessionCookie() {
    val manager = CookieManager.getInstance()
    manager.setCookie("https://open.spotify.com", "sp_dc=; Domain=.spotify.com; Path=/; Max-Age=0; Secure")
    manager.setCookie("https://open.spotify.com", "sp_dc=; Path=/; Max-Age=0; Secure")
    manager.flush()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SpotifyImportLogin(onDismiss: () -> Unit, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<com.music.audioly.data.spotify.models.SpotifyUser?>(null) }
    var candidateCookie by remember { mutableStateOf("") }
    var pageReady by remember { mutableStateOf(false) }
    fun browserCookie(): String = CookieManager.getInstance().getCookie("https://open.spotify.com").orEmpty()
        .split(';').map { it.trim() }.firstOrNull { it.startsWith("sp_dc=") }?.substringAfter('=').orEmpty()
    fun preview() {
        if (checking) return
        val token = browserCookie()
        if (token.isBlank()) { message = "Finish signing in to Spotify, then try again."; return }
        if (profile != null && candidateCookie == token) return
        checking = true; message = null
        scope.launch {
            try {
                val account = com.music.audioly.data.spotify.SpotifyIntegration.previewSession(token)
                candidateCookie = token; profile = account
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Couldn't check this profile. Please try again." }
            finally { checking = false }
        }
    }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val host = android.net.Uri.parse(url.orEmpty()).host
                    if (host == "accounts.spotify.com") view?.evaluateJavascript(SPOTIFY_LOGIN_LAYOUT_FIX, null)
                    pageReady = browserCookie().isNotBlank()
                    if (host == "open.spotify.com" && pageReady) preview()
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) message = "Spotify couldn't load. Check your connection and reload."
                }
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: android.webkit.WebResourceResponse?) {
                    if (request?.isForMainFrame == true) message = "Spotify couldn't load (${response?.statusCode}). Please reload."
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    val host = uri.host.orEmpty().lowercase()
                    return uri.scheme != "https" || !listOf("spotify.com", "google.com", "facebook.com", "apple.com")
                        .any { domain -> host == domain || host.endsWith(".$domain") }
                }
            }
            loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
        }
    }
    DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Close, contentDescription = "Close")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (profile == null) "Sign in to Spotify" else "Choose profile", style = MaterialTheme.typography.titleMedium)
                        Text(if (profile == null) "Use your Spotify account in Audioly" else "Confirm the account you want to use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (pageReady && profile == null) TextButton(onClick = ::preview, enabled = !checking) {
                        Text(if (checking) "Checking…" else "Use this profile")
                    }
                }
                if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { text ->
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { message = null; webView.reload() }) { Text("Reload") }
                    }
                }
                val account = profile
                if (account == null) {
                    AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        if (account.images.isNotEmpty()) coil3.compose.AsyncImage(account.images.first().url, contentDescription = null,
                            modifier = Modifier.size(104.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else Surface(Modifier.size(104.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Text((account.displayName ?: account.id).take(1).uppercase(), style = MaterialTheme.typography.displayMedium) }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(account.displayName ?: account.id, style = MaterialTheme.typography.headlineMedium)
                        Text(account.email ?: "Spotify profile", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        Text("Your playlists, liked songs and listening profile will appear in Audioly.", style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(28.dp))
                        Button(onClick = {
                            if (browserCookie() != candidateCookie) { profile = null; preview() }
                            else {
                                AppSettings.setSpotifySpdcToken(candidateCookie)
                                CookieManager.getInstance().flush()
                                onConnected()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Use this profile") }
                        TextButton(onClick = {
                            profile = null; candidateCookie = ""; pageReady = false
                            val manager = CookieManager.getInstance()
                            for (host in listOf("open.spotify.com", "accounts.spotify.com")) {
                                manager.setCookie("https://$host", "sp_dc=; Domain=.spotify.com; Path=/; Max-Age=0; Secure")
                                manager.setCookie("https://$host", "sp_dc=; Path=/; Max-Age=0; Secure")
                            }
                            manager.flush()
                            webView.loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                        }) { Text("Use another account") }
                    }
                }
            }
        }
    }
}

// Adapted from Meld's SpotifyLoginScreen (GPL-3.0). Spotify's responsive main
// can collapse to 48px in WebView even when the form is hundreds of pixels tall.
// Restrict this stylesheet to Spotify Accounts; never read credential fields.
internal val SPOTIFY_LOGIN_LAYOUT_FIX = """
    (function () {
      if (location.hostname !== 'accounts.spotify.com') return;
      var id = 'audioly-login-layout-fix';
      if (document.getElementById(id)) return;
      var style = document.createElement('style');
      style.id = id;
      style.textContent =
        'html, body { height: auto !important; min-height: 100% !important; overflow: visible !important; }' +
        'body > div { height: auto !important; min-height: 100% !important; }' +
        'main { position: static !important; height: auto !important; min-height: 100dvh !important; max-height: none !important; overflow: visible !important; }';
      document.head.appendChild(style);
    })();
""".trimIndent()
