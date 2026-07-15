package com.sunny.skin.data

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupCipherTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun encryptedZipRoundTrips() {
        val file = temporary.newFile("test.sunnybackup")
        val password = "correct horse battery".toCharArray()
        BackupCipher.write(file, password) { encrypted ->
            ZipOutputStream(encrypted).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("{\"schemaVersion\":1}".toByteArray())
                zip.closeEntry()
            }
        }

        val restored = BackupCipher.read(file, password) { decrypted ->
            ZipInputStream(decrypted).use { zip ->
                assertEquals("manifest.json", zip.nextEntry.name)
                zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        assertEquals("{\"schemaVersion\":1}", restored)
    }

    @Test
    fun wrongPasswordCannotReadArchive() {
        val file = temporary.newFile("test.sunnybackup")
        BackupCipher.write(file, "long correct password".toCharArray()) { it.write("secret".toByteArray()) }

        assertThrows(Exception::class.java) {
            BackupCipher.read(file, "long wrong password".toCharArray()) { it.readBytes() }
        }
    }
}
