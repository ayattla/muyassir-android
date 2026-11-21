package com.muyassir.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muyassir.app.engine.DocBuilder
import com.muyassir.app.engine.Hijri
import com.muyassir.app.engine.ImageEngine
import com.muyassir.app.engine.Ocr
import com.muyassir.app.engine.Office
import com.muyassir.app.engine.PdfEngine
import com.muyassir.app.engine.ZipEngine
import com.muyassir.app.tools.qrBitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs the real file engines on an emulator: every assertion works on genuine output files. */
@RunWith(AndroidJUnit4::class)
class EnginesTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File

    @Before fun setup() {
        PDFBoxResourceLoader.init(ctx)
        dir = File(ctx.cacheDir, "test-${System.nanoTime()}").apply { mkdirs() }
    }

    private fun samplePdf(name: String, pages: Int): File {
        val b = DocBuilder(ctx)
        repeat(pages) { i -> if (i > 0) b.add(DocBuilder.Block.PageBreak); b.add(DocBuilder.Block.Title("صفحة ${i + 1}")); b.add(DocBuilder.Block.Para("مرحبًا بكم في مُيسّر — Muyassir test page ${i + 1}")) }
        return b.save(File(dir, name))
    }

    @Test fun docBuilderMakesPages() = assertEquals(3, PdfEngine.pageCount(samplePdf("a.pdf", 3)))

    @Test fun mergeSplitSelectDeleteRotate() {
        val a = samplePdf("a.pdf", 2); val b = samplePdf("b.pdf", 3)
        val m = PdfEngine.merge(listOf(a, b), File(dir, "m.pdf")); assertEquals(5, PdfEngine.pageCount(m))
        assertEquals(5, PdfEngine.split(m, "", true, dir, "s").size)
        assertEquals(listOf(2, 3), PdfEngine.split(m, "1-2, 3-5", false, dir, "r").map { PdfEngine.pageCount(it) })
        assertEquals(2, PdfEngine.pageCount(PdfEngine.select(m, PdfEngine.parseRanges("5,1", 5), File(dir, "x.pdf"))))
        assertEquals(3, PdfEngine.pageCount(PdfEngine.delete(m, setOf(0, 1), File(dir, "d.pdf"))))
        assertEquals(5, PdfEngine.pageCount(PdfEngine.rotate(m, null, 90, File(dir, "rot.pdf"))))
    }

    @Test fun protectAndUnlock() {
        val p = PdfEngine.protect(samplePdf("p.pdf", 1), "1234", "", true, false, File(dir, "prot.pdf"))
        val u = PdfEngine.unlock(p, "1234", File(dir, "open.pdf"))
        assertEquals(1, PdfEngine.pageCount(u))
    }

    @Test fun textAndImagesAndCompress() {
        val f = samplePdf("t.pdf", 2)
        assertTrue(PdfEngine.text(f)[0].contains("Muyassir"))
        val imgs = PdfEngine.toImages(f, listOf(0, 1), 100, false, dir, "img") { _, _ -> }
        assertEquals(2, imgs.size); assertTrue(imgs.all { it.length() > 1000 })
        val back = PdfEngine.fromImages(imgs.asSequence().map { android.graphics.BitmapFactory.decodeFile(it.path) }, true, File(dir, "fromimg.pdf"))
        assertEquals(2, PdfEngine.pageCount(back))
        assertEquals(2, PdfEngine.pageCount(PdfEngine.compress(f, 80, 50, File(dir, "c.pdf")) { _, _ -> }))
    }

    @Test fun officeToPdf() {
        val x = File(dir, "x.xlsx"); Office.writeXlsx(x, listOf("ورقة" to listOf(listOf("أ", "ب"), listOf("1", "2"))))
        val b = DocBuilder(ctx, true); Office.readXlsx(x).forEach { (_, rows) -> b.add(DocBuilder.Block.Table(rows.first(), rows.drop(1))) }
        assertEquals(1, PdfEngine.pageCount(b.save(File(dir, "x.pdf"))))
    }

    @Test fun arabicOcr() {
        val bmp = Bitmap.createBitmap(1400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 90f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        Canvas(bmp).drawText("مرحبا بالعالم", 700f, 180f, p)
        val text = Ocr.recognize(ctx, bmp, Ocr.Lang.AR)
        assertTrue("OCR read: $text", text.contains("مرحبا") || text.contains("بالعالم"))
    }

    @Test fun imageOps() {
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 140, 200)) }
        assertEquals(400, ImageEngine.resize(bmp, 400, 400, true).width)
        assertEquals(600, ImageEngine.cropRatio(bmp, 1f, 1f).width)
        assertEquals(600, ImageEngine.rotate(bmp, 90f).width)
        val small = ImageEngine.compressToSize(bmp, 30, File(dir, "c.jpg")); assertTrue(small.length() <= 30 * 1024)
        assertTrue(ImageEngine.save(bmp, ImageEngine.Fmt.WEBP, 80, File(dir, "w.webp")).length() > 0)
        assertEquals(472, ImageEngine.idPhoto(bmp, 40f, 60f, Color.WHITE).width)
    }

    @Test fun zipRoundTrip() {
        val a = File(dir, "a.txt").apply { writeText("مرحبا") }
        val z = ZipEngine.zip(listOf("a.txt" to a, "a.txt" to a), File(dir, "z.zip"))
        val files = ZipEngine.unzip(z, File(dir, "unz"))
        assertEquals(2, files.size); assertEquals("مرحبا", files[0].readText())
    }

    @Test fun hijriKnownDates() {
        val h = Hijri.toHijri(2026, 11, 20) // 1 Jumada al-Akhirah 1448 ≈ late Nov 2026
        assertEquals(1448, h.y)
        val g = Hijri.toGregorian(h.y, h.m, h.d)
        assertEquals(listOf(2026, 11, 20), listOf(g.y, g.m, g.d))
    }

    @Test fun qrEncodes() = assertEquals(900, qrBitmap("https://ayattla.com").width)

    /** Arabic OCR at word level, with positions, as the document studio uses it. The recognised text is in the failure message. */
    @Test fun ocrWordsArabic() {
        val bmp = android.graphics.Bitmap.createBitmap(1400, 420, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp); c.drawColor(android.graphics.Color.WHITE)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 64f; textAlign = android.graphics.Paint.Align.RIGHT }
        c.drawText("تقرير المبيعات السنوي", 1340f, 120f, p); c.drawText("Muyassir 2026", 1340f, 240f, p); c.drawText("هذا نص عربي واضح", 1340f, 360f, p)
        val text = com.muyassir.app.engine.Ocr.recognize(ctx, bmp, com.muyassir.app.engine.Ocr.Lang.AR)
        val words = com.muyassir.app.engine.Ocr.recognizeWords(ctx, bmp, com.muyassir.app.engine.Ocr.Lang.AR)
        org.junit.Assert.assertTrue("text=[$text] words=${words.map { it.text }}", words.isNotEmpty() && text.contains("2026"))
        val r = com.muyassir.app.engine.Layout.analyze(words)
        org.junit.Assert.assertTrue("blocks=${r.blocks}", r.blocks.isNotEmpty())
    }
}
