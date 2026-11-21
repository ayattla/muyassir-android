package com.muyassir.app.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File plumbing. Inputs picked by the user are copied into the cache only for the duration of an operation.
 * Results go to files/out and are removed after 24 hours unless the user saves or shares them.
 */
object Files {
    fun outDir(ctx: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(ctx.filesDir, "out/$stamp").apply { mkdirs() }
    }

    /** Every result file still on the device, newest first (they are removed after 24 hours). */
    fun outputs(ctx: Context): List<File> = (File(ctx.filesDir, "out").walkTopDown() + File(ctx.filesDir, "kept").walkTopDown())
        .filter { it.isFile }.sortedByDescending { it.lastModified() }.toList()

    /** Kept results live outside the 24-hour clean-up until the user deletes them. */
    fun isKept(ctx: Context, f: File) = f.absolutePath.startsWith(File(ctx.filesDir, "kept").absolutePath)
    fun keep(ctx: Context, f: File): File {
        val dir = File(ctx.filesDir, "kept").apply { mkdirs() }
        var t = File(dir, f.name); var n = 2
        while (t.exists()) { t = File(dir, "${f.nameWithoutExtension}-$n.${f.extension}"); n++ }
        return if (f.renameTo(t)) t else f.copyTo(t).also { f.delete() }
    }

    fun clearAll(ctx: Context) { File(ctx.filesDir, "out").deleteRecursively(); File(ctx.filesDir, "kept").deleteRecursively(); File(ctx.cacheDir, "in").deleteRecursively() }

    fun cleanOld(ctx: Context) {
        val limit = System.currentTimeMillis() - 24L * 3600 * 1000
        File(ctx.filesDir, "out").listFiles()?.forEach { if (it.lastModified() < limit) it.deleteRecursively() }
        File(ctx.cacheDir, "in").deleteRecursively()
    }

    fun name(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0) ?: "file"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    fun size(ctx: Context, uri: Uri): Long {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) return it.getLong(0)
        }
        return -1
    }

    /** Copies a picked document into the private cache so engines can work on a real file. */
    fun toCache(ctx: Context, uri: Uri): File {
        val dir = File(ctx.cacheDir, "in").apply { mkdirs() }
        val safe = name(ctx, uri).replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val f = File(dir, "${System.nanoTime()}_$safe")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> FileOutputStream(f).use { o -> i.copyTo(o, 1 shl 16) } }
        return f
    }

    fun base(name: String) = name.substringBeforeLast('.').ifBlank { "file" }

    fun mime(f: File): String = when (f.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "zip" -> "application/zip"
        "txt" -> "text/plain"
        "webp" -> "image/webp"
        "csv" -> "text/csv"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"
    }

    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    fun share(ctx: Context, files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { uri(ctx, it) })
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]); type = mime(files[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); type = "*/*" }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, f), mime(f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    /** Saves into Downloads/Muyassir on Android 10+; returns false on older systems (the UI then offers "save as"). */
    fun saveToDownloads(ctx: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime(f))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Muyassir")
        }
        val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        ctx.contentResolver.openOutputStream(u)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
        return true
    }

    fun writeTo(ctx: Context, f: File, target: Uri) {
        ctx.contentResolver.openOutputStream(target)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
    }

    fun printPdf(ctx: Context, f: File) {
        val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(f.nameWithoutExtension, object : PrintDocumentAdapter() {
            override fun onLayout(o: PrintAttributes?, n: PrintAttributes?, c: CancellationSignal?, cb: LayoutResultCallback, e: Bundle?) {
                cb.onLayoutFinished(PrintDocumentInfo.Builder(f.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }
            override fun onWrite(p: Array<out PageRange>?, dest: ParcelFileDescriptor, c: CancellationSignal?, cb: WriteResultCallback) {
                FileOutputStream(dest.fileDescriptor).use { o -> f.inputStream().use { it.copyTo(o) } }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, null)
    }

    fun human(bytes: Long): String = when {
        bytes < 0 -> "—"
        bytes < 1024 -> "$bytes بايت"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f ك.ب", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f م.ب", bytes / 1048576.0)
    }
}
