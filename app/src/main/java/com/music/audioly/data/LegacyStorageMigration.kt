package com.music.audioly.data

import android.content.Context
import android.content.SharedPreferences
import com.music.audioly.auth.EncryptedPrefs
import java.io.File

/** Compatibility only. These identifiers are never displayed or used for new data. */
internal object LegacyStorageMigration {
    const val BACKUP_TAG = "bitchord"
    const val WORD_LYRICS = "BITCHORD_LYRICS"
    val partyServers = setOf(
        "https://bitchord-listen-together.onrender.com",
        "https://api.bitchord.kushagrasingh.in",
        "https://bitchord.kushagrasingh.in",
    )
    fun run(context: Context) {
        val marker = context.getSharedPreferences("audioly_migrations", Context.MODE_PRIVATE)
        if (marker.getBoolean("storage_names_v1", false)) return
        val names = listOf("settings", "widget", "listen_together", "original_versions", "last_played", "party_queue_stash")
        for (suffix in names) {
            copy(context.getSharedPreferences("bitchord_$suffix", Context.MODE_PRIVATE), context.getSharedPreferences("audioly_$suffix", Context.MODE_PRIVATE))
        }
        for (suffix in listOf("auth", "sources")) {
            val old = "bitchord_$suffix"
            if (File(context.applicationInfo.dataDir, "shared_prefs/$old.xml").exists() || File(context.applicationInfo.dataDir, "shared_prefs/${old}_plain.xml").exists()) {
                copy(EncryptedPrefs.open(context, old, "${old}_plain"), EncryptedPrefs.open(context, "audioly_$suffix", "audioly_${suffix}_plain"))
            }
        }
        marker.edit().putBoolean("storage_names_v1", true).commit()
    }
    private fun copy(source: SharedPreferences, target: SharedPreferences) {
        val editor = target.edit()
        for ((key, value) in source.all) {
            if (target.contains(key)) continue
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit()) { "Could not migrate saved settings" }
    }
}
