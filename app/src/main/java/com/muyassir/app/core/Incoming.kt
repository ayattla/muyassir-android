package com.muyassir.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import java.io.File

/**
 * «فتح في مُيسّر»: files shared to the app from WhatsApp, mail, Files… are copied into the private cache
 * (the sharing app's permission is temporary), then the user picks a tool or a recipe for them.
 * Nothing is uploaded; copies are removed at the next launch.
 */
object Incoming {
    val files = mutableStateListOf<Uri>()
    var version by mutableIntStateOf(0); private set
    private var handoff: List<Uri>? = null

    /** URIs carried by a share / open intent, or empty. */
    fun urisOf(intent: Intent?): List<Uri> {
        intent ?: return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data).filter { it.scheme == "content" || it.scheme == "file" }
            else -> emptyList()
        }
    }

    /** Copies the shared files (runs off the main thread) and announces them. */
    fun receive(ctx: Context, uris: List<Uri>): List<Uri> {
        val root = File(ctx.cacheDir, "shared").apply { mkdirs() }
        return uris.take(Plus.PLUS_FILES).mapNotNull { u ->
            runCatching {
                val dir = File(root, System.nanoTime().toString()).apply { mkdirs() }
                val name = Files.name(ctx, u).replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
                val f = File(dir, name)
                ctx.contentResolver.openInputStream(u)!!.use { i -> f.outputStream().use { i.copyTo(it, 1 shl 16) } }
                Uri.fromFile(f)
            }.getOrNull()
        }
    }

    fun announce(list: List<Uri>) { if (list.isEmpty()) return; files.clear(); files.addAll(list); version++ }

    /** The tool screen picks these up once, instead of asking the user to choose files again. */
    fun handTo(list: List<Uri>) { handoff = list }
    fun take(): List<Uri>? = handoff.also { handoff = null }

    fun kind(u: Uri): String = when ((u.lastPathSegment ?: "").substringAfterLast('.', "").lowercase()) {
        "pdf" -> "pdf"
        "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp" -> "image"
        "docx", "doc" -> "word"
        "xlsx", "xls" -> "excel"
        "pptx", "ppt" -> "ppt"
        "zip" -> "zip"
        else -> "other"
    }

    val kindLabel = mapOf("pdf" to "PDF", "image" to "صورة", "word" to "Word", "excel" to "Excel", "ppt" to "PowerPoint", "zip" to "ZIP", "other" to "ملف")

    /** Tools that accept these kinds, most useful first. */
    fun toolsFor(kinds: Set<String>): List<String> {
        val by = mapOf(
            "pdf" to listOf("pdf_compress", "pdf_merge", "pdf_word", "ocr", "pdf_img", "pdf_split", "pdf_protect", "pdf_excel", "pdf_rotate", "pdf_extract"),
            "image" to listOf("img_pdf", "img_compress", "ocr", "img_bg", "img_resize", "img_convert", "img_enhance", "id_photo", "img_crop"),
            "word" to listOf("word_pdf"), "excel" to listOf("excel_pdf"), "ppt" to listOf("ppt_pdf"), "zip" to listOf("unzip"),
        )
        val common = kinds.map { by[it].orEmpty() }.reduceOrNull { a, b -> a.filter { it in b } }.orEmpty()
        return (common + kinds.flatMap { by[it].orEmpty() } + "zip").distinct()
    }

    fun clean(ctx: Context) { File(ctx.cacheDir, "shared").deleteRecursively() }
}
