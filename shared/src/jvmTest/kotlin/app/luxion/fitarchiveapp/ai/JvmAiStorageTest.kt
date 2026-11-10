package app.luxion.fitarchiveapp.ai

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmAiStorageTest {
    private val secret = "sk-ant-api03-ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun tempDir(): File = Files.createTempDirectory("easyfood-ai-test").toFile().also { it.deleteOnExit() }

    @Test
    fun keyFileVaultRoundTripsAndDoesNotStorePlaintext() {
        val dir = tempDir()
        val vault = JvmAiKeyVault(File(dir, "api-key.bin"), KeyFileProtector(File(dir, "local.key")))
        assertNull(vault.read())
        vault.write(secret)
        assertEquals(secret, vault.read())
        val onDisk = File(dir, "api-key.bin").readText()
        assertFalse(onDisk.contains(secret))
        assertFalse(String(java.util.Base64.getDecoder().decode(onDisk.trim()), Charsets.ISO_8859_1).contains("sk-ant"))
        vault.clear()
        assertNull(vault.read())
    }

    @Test
    fun corruptedVaultReadsAsNoKey() {
        val dir = tempDir()
        val vault = JvmAiKeyVault(File(dir, "api-key.bin"), KeyFileProtector(File(dir, "local.key")))
        vault.write(secret)
        File(dir, "api-key.bin").writeText("AAAA")
        assertNull(vault.read())
    }

    @Test
    fun dpapiVaultRoundTripsOnWindows() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val dir = tempDir()
        val vault = JvmAiKeyVault(File(dir, "api-key.bin"), DpapiProtector())
        vault.write(secret)
        assertEquals(secret, vault.read())
        assertFalse(File(dir, "api-key.bin").readText().contains(secret))
    }

    @Test
    fun preferencesPersistAcrossInstances() {
        val file = File(tempDir(), "settings.properties")
        JvmAiPreferences(file).apply { putString("ai.model", "claude-sonnet-5-5"); putString("x", "1"); remove("x") }
        val again = JvmAiPreferences(file)
        assertEquals("claude-sonnet-5-5", again.getString("ai.model"))
        assertNull(again.getString("x"))
        // La clave de API nunca va a las preferencias.
        assertFalse(file.readText().contains("sk-ant"))
    }

    private fun png(width: Int, height: Int, alpha: Boolean = false): ByteArray {
        val img = BufferedImage(width, height, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    @Test
    fun largeImageIsScaledToMaxSideAsJpeg() {
        val jpeg = assertNotNull(downscaleToJpeg(png(4000, 3000)))
        assertTrue(jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte(), "no es JPEG")
        val result = ImageIO.read(ByteArrayInputStream(jpeg))
        assertEquals(1024 to 768, result.width to result.height)
    }

    @Test
    fun smallTransparentImageKeepsSizeAndBecomesJpeg() {
        val jpeg = assertNotNull(downscaleToJpeg(png(200, 100, alpha = true)))
        val result = ImageIO.read(ByteArrayInputStream(jpeg))
        assertEquals(200 to 100, result.width to result.height)
    }

    @Test
    fun garbageIsNotAnImage() {
        assertNull(downscaleToJpeg(byteArrayOf(1, 2, 3, 4)))
        assertNull(downscaleToJpeg(ByteArray(0)))
    }
}
