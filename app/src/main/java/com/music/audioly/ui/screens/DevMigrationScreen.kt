package com.music.audioly.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audioly.R
import com.music.audioly.data.AppUpdateChecker
import com.music.audioly.data.AudiolyUpdatePolicy
import com.music.audioly.data.stats.Backup
import com.music.audioly.playback.PlaybackService
import kotlinx.coroutines.launch

/** A retired Dev build has no route back into playback; backups remain available. */
@Composable
fun DevMigrationScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val update by AppUpdateChecker.available.collectAsStateWithLifecycle()
    val download by AppUpdateChecker.download.collectAsStateWithLifecycle()
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var productionReady by remember { mutableStateOf(false) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            backupBusy = true
            backupStatus = Backup.exportTo(context, uri).fold(
                onSuccess = { "Backup saved. Restore it from the Data settings in Audioly." },
                onFailure = { "Backup failed: ${it.message ?: "Please try again."}" },
            )
            backupBusy = false
        }
    }
    DisposableEffect(lifecycle) {
        fun refresh() {
            productionReady = runCatching {
                val installed = context.packageManager.getPackageInfo(AudiolyUpdatePolicy.PRODUCTION_PACKAGE, 0)
                AudiolyUpdatePolicy.compare(installed.versionName.orEmpty(), AudiolyUpdatePolicy.MIGRATION_VERSION) >= 0
            }.getOrDefault(false)
        }
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        context.stopService(Intent(context, PlaybackService::class.java))
        AppUpdateChecker.clearCache(context)
        AppUpdateChecker.check()
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_logo), "Audioly", Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Move to Audioly ${AudiolyUpdatePolicy.MIGRATION_VERSION}", style = MaterialTheme.typography.headlineMedium)
            Text("Audioly Dev has been retired. Install the production app to keep listening. This update is required.")
            Text("Before moving", style = MaterialTheme.typography.titleMedium)
            Text("Save your settings and listening history, then restore the backup in Audioly. Account sign-ins, downloaded audio and local playlists are not included. Keep Dev installed until you have checked your data.")
            OutlinedButton(onClick = { backup.launch(Backup.suggestedName()) }, enabled = !backupBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (backupBusy) "Saving backup…" else "Back up settings and history")
            }
            backupStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (!productionReady) when (val state = download) {
                is AppUpdateChecker.DownloadState.Downloading -> {
                    LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading Audioly… ${(state.fraction * 100).toInt()}%")
                    TextButton(onClick = AppUpdateChecker::cancelDownload) { Text("Cancel download") }
                }
                is AppUpdateChecker.DownloadState.Ready -> Button(
                    onClick = { AppUpdateChecker.installApk(context, state.file) }, modifier = Modifier.fillMaxWidth(),
                ) { Text("Install Audioly ${update?.version ?: AudiolyUpdatePolicy.MIGRATION_VERSION}") }
                else -> {
                    if (state is AppUpdateChecker.DownloadState.Failed) Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { scope.launch { AppUpdateChecker.downloadApk(context) } }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state is AppUpdateChecker.DownloadState.Failed) "Retry download" else "Download Audioly ${update?.version ?: AudiolyUpdatePolicy.MIGRATION_VERSION}")
                    }
                }
            }
            if (productionReady) Button(onClick = {
                context.packageManager.getLaunchIntentForPackage(AudiolyUpdatePolicy.PRODUCTION_PACKAGE)?.let(context::startActivity)
            }, modifier = Modifier.fillMaxWidth()) { Text("Open Audioly") }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update?.releaseUrl
                    ?: "https://github.com/audioly-music/Audioly/releases/tag/v2.0.0")))
            }) { Text("Download from GitHub") }
            Text("Android will ask you to approve installation. After opening Audioly, sign in again and restore your backup from Settings.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
