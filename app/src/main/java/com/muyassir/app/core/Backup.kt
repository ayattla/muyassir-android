package com.muyassir.app.core

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted backup of everything the user saved in the app (notes, records, settings, recipes, logo, signature).
 * The file is written where the user chooses (device, Google Drive…) — never to a Muyassir server.
 * Format: "MYSB1" + salt(16) + iv(12) + AES-256-GCM(zip). Key = PBKDF2-HMAC-SHA256(password, salt, 120 000).
 */
object Backup {
    private val MAGIC = "MYSB1".toByteArray()
    private const val ITER = 120_000

    private fun key(password: String, salt: ByteArray) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, ITER, 256)).encoded, "AES")

    fun zipDir(dir: File): ByteArray = ByteArrayOutputStream().also { bo ->
        ZipOutputStream(bo).use { z -> dir.walkTopDown().filter { it.isFile }.forEach { f -> z.putNextEntry(ZipEntry(f.relativeTo(dir).path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() } }
    }.toByteArray()

    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val rnd = SecureRandom(); val salt = ByteArray(16).also(rnd::nextBytes); val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    /** Throws if the password is wrong or the file is not a Muyassir backup. */
    fun decrypt(data: ByteArray, password: String): ByteArray {
        require(data.size > 33 && data.copyOfRange(0, 5).contentEquals(MAGIC)) { "هذا الملف ليس نسخة احتياطية من مُيسّر" }
        val salt = data.copyOfRange(5, 21); val iv = data.copyOfRange(21, 33)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        return runCatching { c.doFinal(data, 33, data.size - 33) }.getOrElse { throw IllegalArgumentException("كلمة المرور غير صحيحة") }
    }

    fun export(ctx: Context, password: String, out: OutputStream) { out.write(encrypt(zipDir(File(ctx.filesDir, "data")), password)) }

    /** Replaces the saved data with the backup's. The app restarts afterwards to load it. */
    fun restore(ctx: Context, password: String, input: InputStream) {
        val zip = decrypt(input.readBytes(), password)
        val dir = File(ctx.filesDir, "data"); val tmp = File(ctx.filesDir, "data-restore").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val f = File(tmp, e.name).canonicalFile
                require(f.path.startsWith(tmp.canonicalPath)) { "ملف غير صالح" }
                if (!e.isDirectory) { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                e = z.nextEntry
            }
        }
        dir.deleteRecursively(); tmp.renameTo(dir)
    }
}
