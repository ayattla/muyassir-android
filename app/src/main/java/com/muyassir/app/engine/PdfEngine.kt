package com.muyassir.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer

object PdfEngine {
    /** "1-3, 5, 8-" → zero-based page indexes (in the order written). */
    fun parseRanges(spec: String, count: Int): List<Int> {
        val out = ArrayList<Int>()
        val norm = spec.map { if (it in '٠'..'٩') ('0' + (it - '٠')) else it }.joinToString("").replace('،', ',').replace('–', '-')
        for (part in norm.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            if ('-' in part) {
                val (a, b) = part.split('-', limit = 2).map { it.trim() }
                val s = a.toIntOrNull() ?: 1; val e = b.toIntOrNull() ?: count
                val range = if (s <= e) s..e else s downTo e
                for (p in range) if (p in 1..count) out += p - 1
            } else part.toIntOrNull()?.let { if (it in 1..count) out += it - 1 }
        }
        return out
    }

    fun pageCount(f: File, password: String? = null): Int =
        runCatching { ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { PdfRenderer(it).use { r -> r.pageCount } } }
            .getOrElse { PDDocument.load(f, password ?: "").use { it.numberOfPages } }

    fun merge(inputs: List<File>, out: File): File {
        val m = PDFMergerUtility()
        inputs.forEach { m.addSource(it) }
        m.destinationFileName = out.absolutePath
        m.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        return out
    }

    fun select(input: File, pages: List<Int>, out: File): File {
        PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { src ->
            PDDocument().use { dst ->
                pages.forEach { dst.importPage(src.getPage(it)) }
                dst.save(out)
            }
        }
        return out
    }

    fun delete(input: File, pages: Set<Int>, out: File): File {
        PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { d ->
            pages.sortedDescending().forEach { if (it < d.numberOfPages) d.removePage(it) }
            require(d.numberOfPages > 0) { "لا يمكن حذف جميع الصفحات" }
            d.save(out)
        }
        return out
    }

    fun split(input: File, spec: String, everyPage: Boolean, dir: File, base: String): List<File> {
        val n = pageCount(input)
        val groups: List<List<Int>> = if (everyPage || spec.isBlank()) (0 until n).map { listOf(it) }
        else spec.split(',', '،').map { parseRanges(it, n) }.filter { it.isNotEmpty() }
        return groups.mapIndexed { i, g -> select(input, g, File(dir, "$base-${i + 1}.pdf")) }
    }

    fun rotate(input: File, pages: List<Int>?, degrees: Int, out: File): File {
        PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { d ->
            (pages ?: (0 until d.numberOfPages).toList()).forEach { val p = d.getPage(it); p.rotation = (p.rotation + degrees + 360) % 360 }
            d.save(out)
        }
        return out
    }

    fun protect(input: File, userPass: String, ownerPass: String, allowPrint: Boolean, allowCopy: Boolean, out: File): File {
        PDDocument.load(input).use { d ->
            val ap = AccessPermission().apply { setCanPrint(allowPrint); setCanExtractContent(allowCopy); setCanModify(false) }
            val pol = StandardProtectionPolicy(ownerPass.ifBlank { userPass + "#owner" }, userPass, ap).apply { encryptionKeyLength = 128 }
            d.protect(pol); d.save(out)
        }
        return out
    }

    fun unlock(input: File, password: String, out: File): File {
        PDDocument.load(input, password).use { d -> d.isAllSecurityToBeRemoved = true; d.save(out) }
        return out
    }

    fun render(input: File, page: Int, dpi: Int): Bitmap =
        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(page).use { p ->
                    val scale = dpi / 72f
                    val w = (p.width * scale).toInt().coerceAtMost(4000); val h = (p.height * scale).toInt().coerceAtMost(5600)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    bmp
                }
            }
        }

    fun toImages(input: File, pages: List<Int>, dpi: Int, png: Boolean, dir: File, base: String, progress: (Int, Int) -> Unit): List<File> =
        pages.mapIndexed { i, p ->
            progress(i, pages.size)
            val bmp = render(input, p, dpi)
            val f = File(dir, "$base-${p + 1}.${if (png) "png" else "jpg"}")
            FileOutputStream(f).use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
            bmp.recycle(); f
        }

    /** Images → PDF. `a4` fits each image on an A4 page with margins; otherwise the page takes the image size. */
    fun fromImages(bitmaps: Sequence<Bitmap>, a4: Boolean, out: File): File {
        val doc = PdfDocument()
        var n = 0
        for (bmp in bitmaps) {
            n++
            val (pw, ph) = if (a4) (if (bmp.width > bmp.height) 842 to 595 else 595 to 842) else (bmp.width * 72 / 150) to (bmp.height * 72 / 150)
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
            val m = if (a4) 24f else 0f
            val s = minOf((pw - 2 * m) / bmp.width, (ph - 2 * m) / bmp.height)
            val w = bmp.width * s; val h = bmp.height * s
            page.canvas.drawBitmap(bmp, null, android.graphics.RectF((pw - w) / 2, (ph - h) / 2, (pw + w) / 2, (ph + h) / 2), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            doc.finishPage(page); bmp.recycle()
        }
        FileOutputStream(out).use { doc.writeTo(it) }; doc.close()
        return out
    }

    /** Rasterising compression: re-renders every page as JPEG. Strong size reduction; text becomes an image. */
    fun compress(input: File, dpi: Int, quality: Int, out: File, progress: (Int, Int) -> Unit): File {
        val n = pageCount(input)
        val doc = PdfDocument()
        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                for (i in 0 until n) {
                    progress(i, n)
                    r.openPage(i).use { p ->
                        val s = dpi / 72f
                        val bmp = Bitmap.createBitmap((p.width * s).toInt(), (p.height * s).toInt(), Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val bytes = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                        bmp.recycle()
                        val jpg = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val page = doc.startPage(PdfDocument.PageInfo.Builder(p.width, p.height, i + 1).create())
                        page.canvas.drawBitmap(jpg, null, android.graphics.RectF(0f, 0f, p.width.toFloat(), p.height.toFloat()), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                        doc.finishPage(page); jpg.recycle()
                    }
                }
            }
        }
        FileOutputStream(out).use { doc.writeTo(it) }; doc.close()
        return out
    }

    /** Text per page. Arabic presentation forms are normalised to regular letters. */
    fun text(input: File, password: String? = null): List<String> =
        PDDocument.load(input, password ?: "").use { d ->
            val st = PDFTextStripper().apply { sortByPosition = true }
            (1..d.numberOfPages).map { p ->
                st.startPage = p; st.endPage = p
                Normalizer.normalize(st.getText(d), Normalizer.Form.NFKC)
            }
        }

    /** Stamps a signature image on one page at a relative position (0..1 from the top-left). */
    fun stamp(input: File, page: Int, png: Bitmap, rx: Float, ry: Float, rw: Float, out: File): File {
        PDDocument.load(input).use { d ->
            val p: PDPage = d.getPage(page)
            val box = p.cropBox
            val img = LosslessFactory.createFromImage(d, png)
            val w = box.width * rw; val h = w * png.height / png.width
            val x = box.lowerLeftX + box.width * rx
            val yTop = box.upperRightY - box.height * ry
            PDPageContentStream(d, p, PDPageContentStream.AppendMode.APPEND, true, true).use { cs -> cs.drawImage(img, x, yTop - h, w, h) }
            d.save(out)
        }
        return out
    }

    /** Diagonal semi-transparent text watermark on every page (text drawn by Android so Arabic is shaped correctly). */
    fun watermark(input: File, text: String, out: File, password: String? = null): File {
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 120f; color = android.graphics.Color.argb(46, 20, 26, 46); typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val tw = tp.measureText(text).toInt().coerceAtLeast(10); val th = 160
        val diag = Math.hypot(tw.toDouble(), th.toDouble()).toInt()
        val bmp = Bitmap.createBitmap(diag, diag, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply { rotate(-35f, diag / 2f, diag / 2f); drawText(text, (diag - tw) / 2f, diag / 2f + 40f, tp) }
        PDDocument.load(input, password ?: "").use { d ->
            val img = LosslessFactory.createFromImage(d, bmp)
            for (p in d.pages) {
                val box = p.cropBox; val s = minOf(box.width, box.height) * 0.85f
                PDPageContentStream(d, p, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.drawImage(img, box.lowerLeftX + (box.width - s) / 2, box.lowerLeftY + (box.height - s) / 2, s, s)
                }
            }
            d.save(out)
        }
        bmp.recycle(); return out
    }

    /** One overlay placed on a page, in relative coordinates (0..1 from the top-left of the page). */
    data class Overlay(val page: Int, val rx: Float, val ry: Float, val rw: Float, val rh: Float, val bitmap: Bitmap)

    /** Draws text boxes, highlights and white-outs (rendered by Android, so Arabic is shaped correctly) onto the pages. */
    fun annotate(input: File, items: List<Overlay>, out: File): File {
        PDDocument.load(input).use { d ->
            items.groupBy { it.page }.forEach { (pg, list) ->
                if (pg !in 0 until d.numberOfPages) return@forEach
                val p = d.getPage(pg); val box = p.cropBox
                PDPageContentStream(d, p, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    list.forEach { o ->
                        val img = LosslessFactory.createFromImage(d, o.bitmap)
                        val w = box.width * o.rw; val h = box.height * o.rh
                        cs.drawImage(img, box.lowerLeftX + box.width * o.rx, box.upperRightY - box.height * o.ry - h, w, h)
                    }
                }
            }
            d.save(out)
        }
        return out
    }

    data class FormField(val name: String, val label: String, val page: Int, val rx: Float, val ry: Float, val rw: Float, val rh: Float, val value: String)

    /** Text fields of a fillable PDF form, with where each one sits on its page. */
    fun formFields(input: File): List<FormField> = PDDocument.load(input).use { d ->
        val form = d.documentCatalog.acroForm ?: return@use emptyList()
        form.fieldTree.filterIsInstance<com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField>().mapNotNull { f ->
            val w = f.widgets.firstOrNull() ?: return@mapNotNull null
            val page = w.page?.let { d.pages.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            val box = d.getPage(page).cropBox; val r = w.rectangle ?: return@mapNotNull null
            FormField(f.fullyQualifiedName, f.alternateFieldName ?: f.partialName ?: f.fullyQualifiedName, page,
                (r.lowerLeftX - box.lowerLeftX) / box.width, (box.upperRightY - r.upperRightY) / box.height, r.width / box.width, r.height / box.height, f.valueAsString ?: "")
        }
    }

    /**
     * Fills form fields. Latin values go into the field itself; Arabic (which the form's own font usually cannot show)
     * is drawn onto the page at the field's place instead. [render] makes the bitmap for a value at a given size.
     */
    fun fillForm(input: File, values: Map<String, String>, out: File, render: (String, Float, Float) -> Bitmap): File {
        val overlays = ArrayList<Overlay>()
        val tmp = File(out.parentFile, "form-${System.nanoTime()}.pdf")
        PDDocument.load(input).use { d ->
            val form = d.documentCatalog.acroForm
            val fields = formFields(input).associateBy { it.name }
            values.filterValues { it.isNotBlank() }.forEach { (name, v) ->
                val f = form?.getField(name)
                val ok = v.all { it.code < 128 } && runCatching { f?.setValue(v) }.isSuccess
                if (!ok) fields[name]?.let { ff ->
                    runCatching { f?.setValue("") }
                    val box = d.getPage(ff.page).cropBox
                    overlays += Overlay(ff.page, ff.rx, ff.ry, ff.rw, ff.rh, render(v, box.width * ff.rw, box.height * ff.rh))
                }
            }
            d.save(tmp)
        }
        return if (overlays.isEmpty()) tmp.copyTo(out, true).also { tmp.delete() } else annotate(tmp, overlays, out).also { tmp.delete() }
    }
}
