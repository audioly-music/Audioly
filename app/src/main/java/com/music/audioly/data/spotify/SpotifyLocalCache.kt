package com.music.audioly.data.spotify

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Small account-scoped metadata snapshots; never contains credentials or audio. */
internal class SpotifyLocalCache(context: Context) : SQLiteOpenHelper(context, "spotify_metadata.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE snapshots (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun get(id: String): String? = readableDatabase.query("snapshots", arrayOf("payload"), "id = ?",
        arrayOf(id), null, null, null).use { if (it.moveToFirst()) it.getString(0) else null }
    fun put(id: String, payload: String) {
        writableDatabase.insertWithOnConflict("snapshots", null, ContentValues().apply {
            put("id", id); put("payload", payload)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun remove(id: String) { writableDatabase.delete("snapshots", "id = ?", arrayOf(id)) }
}
