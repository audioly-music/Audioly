package com.music.audioly.desktop

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopTrayIconTest {
    @Test
    fun pngColorsAndTransparencyReachTheTray() {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFF87F4D4.toInt())
        image.setRGB(1, 0, 0xFF5148FF.toInt())
        val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val pixmap = assertNotNull(renderTrayIcon(png, 2))
        assertEquals(2, pixmap.width)
        assertEquals(2, pixmap.height)
        assertContentEquals(
            byteArrayOf(-1, 0x87.toByte(), 0xF4.toByte(), 0xD4.toByte(), -1, 0x51, 0x48, -1, 0, 0, 0, 0, 0, 0, 0, 0),
            pixmap.data,
        )
    }

    @Test
    fun invalidArtworkDoesNotCrashTheTray() {
        assertNull(renderTrayIcon(byteArrayOf(1, 2, 3), 22))
    }
}
