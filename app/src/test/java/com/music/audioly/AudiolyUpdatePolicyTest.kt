package com.music.audioly

import com.music.audioly.data.AudiolyUpdatePolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class AudiolyUpdatePolicyTest {
    @Test fun migrationIsRequiredEvenAtTheSameDevVersion() {
        assertTrue(AudiolyUpdatePolicy.shouldOffer("2.0.0", "2.0.0", true))
        assertFalse(AudiolyUpdatePolicy.shouldOffer("1.10.0", "1.9.3", true))
    }
    @Test fun productionUpdatesAreOrderedAcrossTheRename() {
        assertTrue(AudiolyUpdatePolicy.shouldOffer("2.0.0", "1.10.0", false))
        assertTrue(AudiolyUpdatePolicy.shouldOffer("3.0.0", "2.9.9", false))
        assertFalse(AudiolyUpdatePolicy.shouldOffer("2.0.0", "2.0.0", false))
        assertTrue(AudiolyUpdatePolicy.shouldOffer("2.0.0", "2.0.0-beta1", false))
    }
    @Test fun migrationApkIsNeverSelectedForProduction() {
        val release = Json.parseToJsonElement("""{"assets":[
          {"name":"Audioly-Dev-Migration.apk","state":"uploaded","browser_download_url":"wrong"},
          {"name":"Audioly.apk","state":"uploaded","browser_download_url":"production"}
        ]}""").jsonObject
        assertEquals("production", AudiolyUpdatePolicy.productionApkUrl(release))
    }
    @Test fun incompleteReleaseHasNoInstallableProductionApk() {
        val release = Json.parseToJsonElement("""{"assets":[{"name":"Audioly.apk","state":"new","browser_download_url":"pending"}]}""").jsonObject
        assertNull(AudiolyUpdatePolicy.productionApkUrl(release))
    }
}
