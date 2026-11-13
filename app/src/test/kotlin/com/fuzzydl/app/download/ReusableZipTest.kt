package com.fuzzydl.app.download

import com.fuzzydl.app.shizuku.ZipExtractor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Cobre a decisão de reaproveitar um ZIP já baixado em vez de rebaixar a parte
 * inteira (app morto em VERIFYING/EXTRACTING) e a heurística que separa ZIP
 * corrompido de falha ambiental.
 */
class ReusableZipTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun writeZip(name: String, entries: List<Pair<String, ByteArray>>): File {
        val file = File(temp.root, name)
        ZipOutputStream(file.outputStream()).use { zos ->
            for ((entryName, data) in entries) {
                zos.putNextEntry(ZipEntry(entryName))
                if (!entryName.endsWith("/")) zos.write(data)
                zos.closeEntry()
            }
        }
        return file
    }

    private fun zipWith(files: Int = 2): File = writeZip(
        "part01.zip",
        buildList {
            add("android/" to ByteArray(0))
            repeat(files) { add("android/file$it.rpf" to ByteArray(1024) { b -> b.toByte() }) }
        },
    )

    // ------------------------------------------------------------------
    // reusableZip
    // ------------------------------------------------------------------
    @Test
    fun reusesCompleteZipWithoutPartial() {
        val zip = zipWith()
        val partial = File(zip.path + ".part")
        assertTrue(reusableZip(zip, partial, zip.length()))
    }

    @Test
    fun doesNotReuseWhenPartialExists() {
        val zip = zipWith()
        val partial = File(zip.path + ".part").apply { writeBytes(ByteArray(16)) }
        assertFalse(reusableZip(zip, partial, zip.length()))
    }

    @Test
    fun doesNotReuseOnSizeMismatch() {
        val zip = zipWith()
        val partial = File(zip.path + ".part")
        assertFalse(reusableZip(zip, partial, zip.length() + 1))
    }

    @Test
    fun doesNotReuseNonZip() {
        val fake = File(temp.root, "part01.zip").apply { writeText("não é zip") }
        val partial = File(fake.path + ".part")
        assertFalse(reusableZip(fake, partial, fake.length()))
    }

    @Test
    fun doesNotReuseMissingFile() {
        val zip = File(temp.root, "inexistente.zip")
        val partial = File(zip.path + ".part")
        assertFalse(reusableZip(zip, partial, 1024))
    }

    @Test
    fun doesNotReuseZipWithoutFiles() {
        val zip = writeZip("part01.zip", listOf("android/" to ByteArray(0)))
        val partial = File(zip.path + ".part")
        assertFalse(reusableZip(zip, partial, zip.length()))
    }

    // ------------------------------------------------------------------
    // isZipIntegrityFailure
    // ------------------------------------------------------------------
    @Test
    fun integrityFailuresAreDetected() {
        assertTrue(isZipIntegrityFailure(RuntimeException("CRC inválido em android/a.rpf")))
        assertTrue(isZipIntegrityFailure(RuntimeException("Zip Slip bloqueado: ../evil")))
        assertTrue(isZipIntegrityFailure(RuntimeException("o arquivo baixado não é um ZIP válido")))
        assertTrue(isZipIntegrityFailure(RuntimeException("tamanho extraído divergente (10 de 20)")))
        // Mensagens do FileService (processo shell do Shizuku).
        assertTrue(isZipIntegrityFailure(RuntimeException("arquivo não é ZIP (formato não suportado)")))
        assertTrue(isZipIntegrityFailure(RuntimeException("ZIP sem conteúdo")))
        // Erros típicos do java.util.zip, por tipo e por causa aninhada.
        assertTrue(isZipIntegrityFailure(java.util.zip.ZipException("invalid distance too far back")))
        assertTrue(
            isZipIntegrityFailure(
                RuntimeException("extração falhou", java.util.zip.ZipException("invalid stored block lengths")),
            ),
        )
    }

    @Test
    fun environmentalFailuresArePreserved() {
        assertFalse(isZipIntegrityFailure(RuntimeException("Shizuku indisponível para gravar em Android/data")))
        assertFalse(isZipIntegrityFailure(RuntimeException("espaço insuficiente no destino para a parte 3")))
        assertFalse(isZipIntegrityFailure(RuntimeException("timeout aguardando o primeiro byte")))
        assertFalse(isZipIntegrityFailure(RuntimeException("cancelado")))
    }
}
