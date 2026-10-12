package com.music.audioly.desktop

import java.util.Properties

internal object DesktopBuildInfo {
    val version: String by lazy {
        System.getProperty("audioly.version") ?: Properties().apply {
            requireNotNull(DesktopBuildInfo::class.java.getResourceAsStream("/audioly-version.properties")) {
                "The packaged Audioly version is missing"
            }.use(::load)
        }.getProperty("version")
    }
}
