package com.fuzzydl.app.shizuku

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExtractorTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private fun writeZip(
        name: String,
        entries: List<Pair<String, ByteArray>>,
        method: Int = ZipOutputStream.DEFLATED,
    ): File {
        val file = File(temp.root, name)
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.setMethod(method)
            for ((entryName, data) in entries) {
                val entry = ZipEntry(entryName)
                if (method == ZipOutputStream.STORED) {
                    val crc = CRC32().apply { update(data) }
                    entry.method = ZipOutputStream.STORED
                    entry.size = data.size.toLong()
                    entry.compressedSize = data.size.toLong()
                    entry.crc = crc.value
                }
                zos.putNextEntry(entry)
                if (!entryName.endsWith("/")) zos.write(data)
                zos.closeEntry()
            }
        }
        return file
    }

    private fun extractor() = ZipExtractor()

    // ------------------------------------------------------------------
    // Casos normais
    // ------------------------------------------------------------------
    @Test
    fun extractsFilesAndDirectories() {
        val zip = writeZip(
            "ok.zip",
            listOf(
                "android/" to ByteArray(0),
                "android/audio.rpf" to "conteudo".toByteArray(),
                "update/android/dlc.rpf" to ByteArray(4096) { it.toByte() },
                "androida.rpf" to "raiz".toByteArray(),
            ),
        )
        val dest = File(temp.root, "out")
        val outcome = extractor().extract(zip, dest)

        assertEquals(3, outcome.files)
        assertEquals(4 + 4096 + 4L, outcome.bytes)
        assertEquals("conteudo", File(dest, "android/audio.rpf").readText())
        assertEquals(4096, File(dest, "update/android/dlc.rpf").length())
        assertTrue(File(dest, "androida.rpf").isFile)
    }

    @Test
    fun stripsSingleGameRoot() {
        val zip = writeZip(
            "game.zip",
            listOf(
                "game/" to ByteArray(0),
                "game/android/audio.rpf" to "a".toByteArray(),
                "game/androida.rpf" to "b".toByteArray(),
            ),
        )
        val dest = File(temp.root, "out")
        val info = extractor().inspect(zip)
        assertTrue(info.singleGameRoot)

        extractor().extract(zip, dest)

        assertTrue(File(dest, "android/audio.rpf").isFile)
        assertTrue(File(dest, "androida.rpf").isFile)
        assertFalse(File(dest, "game").exists())
    }

    @Test
    fun keepsGamePrefixWhenMixedWithOtherRoots() {
        val zip = writeZip(
            "mixed.zip",
            listOf(
                "game/a.rpf" to "a".toByteArray(),
                "android/b.rpf" to "b".toByteArray(),
            ),
        )
        val dest = File(temp.root, "out")
        val info = extractor().inspect(zip)
        assertFalse(info.singleGameRoot)

        extractor().extract(zip, dest)

        assertTrue(File(dest, "game/a.rpf").isFile)
        assertTrue(File(dest, "android/b.rpf").isFile)
    }

    @Test
    fun overwritesExistingFiles() {
        val zip = writeZip("ow.zip", listOf("a.txt" to "novo".toByteArray()))
        val dest = File(temp.root, "out")
        File(dest, "a.txt").apply {
            parentFile.mkdirs()
            writeText("antigo e maior")
        }

        extractor().extract(zip, dest)

        assertEquals("novo", File(dest, "a.txt").readText())
    }

    @Test
    fun detectsZipByMagicBytes() {
        val zip = writeZip("magic.zip", listOf("a" to "1".toByteArray()))
        assertTrue(extractor().isZip(zip))
        assertFalse(extractor().isZip(File(temp.root, "nope.zip")))
        val fake = File(temp.root, "fake.zip").apply { writeText("PK?") }
        assertFalse(extractor().isZip(fake))
    }

    // ------------------------------------------------------------------
    // Segurança
    // ------------------------------------------------------------------
    @Test
    fun rejectsPathTraversal() {
        val zip = writeZip("slip.zip", listOf("../evil.txt" to "x".toByteArray()))
        val dest = File(temp.root, "out")
        val error = assertThrows(IOException::class.java) {
            extractor().extract(zip, dest)
        }
        assertTrue(error.message!!.contains("traversal"))
        assertFalse(File(temp.root, "evil.txt").exists())
    }

    @Test
    fun rejectsNestedTraversal() {
        val zip = writeZip("slip2.zip", listOf("android/../../evil.txt" to "x".toByteArray()))
        assertThrows(IOException::class.java) {
            extractor().extract(zip, File(temp.root, "out"))
        }
    }

    @Test
    fun rejectsAbsolutePath() {
        val zip = writeZip("abs.zip", listOf("/data/local/tmp/evil.txt" to "x".toByteArray()))
        assertThrows(IOException::class.java) {
            extractor().extract(zip, File(temp.root, "out"))
        }
    }

    @Test
    fun rejectsBackslashSeparator() {
        val zip = writeZip("bs.zip", listOf("android\\evil.txt" to "x".toByteArray()))
        assertThrows(IOException::class.java) {
            extractor().extract(zip, File(temp.root, "out"))
        }
    }

    // ------------------------------------------------------------------
    // Integridade
    // ------------------------------------------------------------------
    @Test
    fun detectsCrcMismatch() {
        val payload = "conteudo-unico-1234567890".toByteArray()
        val zip = writeZip("crc.zip", listOf("a.txt" to payload), ZipOutputStream.STORED)
        // Corrompe um byte do conteúdo (entrada STORED = bytes crus no arquivo).
        val bytes = zip.readBytes()
        val offset = bytes.indexOfSubArray(payload)
        assertTrue("payload deveria estar cru no ZIP", offset >= 0)
        bytes[offset + 3] = (bytes[offset + 3].toInt() xor 0xFF).toByte()

        val corrupt = File(temp.root, "crc-corrupt.zip").apply { writeBytes(bytes) }
        val error = assertThrows(IOException::class.java) {
            extractor().extract(corrupt, File(temp.root, "out"))
        }
        assertTrue(error.message!!.contains("CRC"))
    }

    @Test
    fun extractsZip64Archive() {
        val resource = javaClass.classLoader!!.getResourceAsStream("zip64.zip")
            ?: error("fixture zip64.zip ausente")
        val zip = File(temp.root, "zip64.zip")
        resource.use { input -> zip.outputStream().use { input.copyTo(it) } }

        assertTrue(extractor().isZip(zip))
        val info = extractor().inspect(zip)
        assertEquals(2, info.files)

        val dest = File(temp.root, "out64")
        val outcome = extractor().extract(zip, dest)
        assertEquals(2, outcome.files)
        assertEquals("zip64 ok\n", File(dest, "zip64/hello.txt").readText())
        val bytes = File(dest, "zip64/second.bin").readBytes()
        assertEquals(64, bytes.size)
        assertArrayEquals(ByteArray(64) { it.toByte() }, bytes)
    }

    private fun ByteArray.indexOfSubArray(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) return -1
        outer@ for (i in 0..(size - needle.size)) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
