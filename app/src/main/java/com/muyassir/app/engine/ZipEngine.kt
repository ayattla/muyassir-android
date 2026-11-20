package com.muyassir.app.engine

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipEngine {
    fun zip(files: List<Pair<String, File>>, out: File, level: Int = 6): File {
        val used = HashSet<String>()
        ZipOutputStream(FileOutputStream(out)).use { z ->
            z.setLevel(level)
            for ((name, f) in files) {
                var n = name; var k = 1
                while (!used.add(n)) n = "${name.substringBeforeLast('.')} (${k++}).${name.substringAfterLast('.', "")}".trimEnd('.')
                z.putNextEntry(ZipEntry(n)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        return out
    }

    /** Extracts safely (blocks "../" path traversal) and returns the extracted files. */
    fun unzip(zip: File, dir: File): List<File> {
        val out = ArrayList<File>()
        val root = dir.canonicalFile
        ZipInputStream(zip.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val f = File(root, e.name).canonicalFile
                if (!f.path.startsWith(root.path + File.separator)) continue
                if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); FileOutputStream(f).use { z.copyTo(it) }; out += f }
            }
        }
        return out
    }
}
