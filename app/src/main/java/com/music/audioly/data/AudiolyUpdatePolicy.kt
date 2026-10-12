package com.music.audioly.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal object AudiolyUpdatePolicy {
    const val PRODUCTION_PACKAGE = "com.music.audioly"
    const val MIGRATION_VERSION = "2.0.0"

    fun compare(left: String, right: String): Int {
        fun parts(value: String) = value.removePrefix("v").substringBefore('-')
            .split('.').map { it.toIntOrNull() ?: -1 }
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val result = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return ('-' in right).compareTo('-' in left)
    }

    fun shouldOffer(latest: String, current: String, migration: Boolean): Boolean =
        if (migration) compare(latest, MIGRATION_VERSION) >= 0
        else compare(latest, current) > 0

    /** Never send a production user to an APK for the retired Dev package. */
    fun productionApkUrl(release: JsonObject): String? = runCatching {
        release["assets"]?.jsonArray?.mapNotNull { it as? JsonObject }
            ?.firstOrNull {
                it["name"]?.jsonPrimitive?.contentOrNull == "Audioly.apk" &&
                    it["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
