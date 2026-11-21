package com.muyassir.app.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * Fully offline OCR. Arabic (and mixed Arabic/English) uses Tesseract with the bundled `ara`+`eng` models;
 * English-only uses ML Kit's on-device Latin recogniser, which is faster and more accurate for Latin text.
 */
object Ocr {
    enum class Lang(val label: String) { AR("عربي + إنجليزي"), EN("إنجليزي فقط") }

    private fun tessDir(ctx: Context): File {
        val dir = File(ctx.filesDir, "ocr"); val td = File(dir, "tessdata").apply { mkdirs() }
        for (n in listOf("ara", "eng")) {
            val f = File(td, "$n.traineddata")
            if (!f.exists() || f.length() == 0L) ctx.assets.open("tessdata/$n.traineddata").use { i -> f.outputStream().use { i.copyTo(it) } }
        }
        return dir
    }

    data class Line(val text: String, val box: android.graphics.Rect)

    /** Text plus the position of every line, for building a searchable PDF (image with an invisible text layer). */
    fun recognizeLines(ctx: Context, bmp: Bitmap, lang: Lang): Pair<String, List<Line>> = when (lang) {
        Lang.EN -> {
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val r = Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0)))
                r.text to r.textBlocks.flatMap { b -> b.lines.mapNotNull { l -> l.boundingBox?.let { Line(l.text, it) } } }
            } finally { rec.close() }
        }
        Lang.AR -> {
            val api = TessBaseAPI()
            try {
                check(api.init(tessDir(ctx).absolutePath, "ara+eng")) { "تعذّر تشغيل محرك التعرف" }
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                val k = if (bmp.width < 1200) 2 else 1
                val src = if (k == 2) Bitmap.createScaledBitmap(bmp, bmp.width * 2, bmp.height * 2, true) else bmp
                api.setImage(src.copy(Bitmap.Config.ARGB_8888, false))
                val text = api.getUTF8Text()?.trim() ?: ""
                val lines = ArrayList<Line>()
                val it = api.resultIterator
                if (it != null) {
                    it.begin()
                    val level = com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                    do {
                        val t = it.getUTF8Text(level)?.trim().orEmpty()
                        val r = it.getBoundingRect(level)
                        if (t.isNotEmpty()) lines += Line(t, android.graphics.Rect(r.left / k, r.top / k, r.right / k, r.bottom / k))
                    } while (it.next(level))
                    it.delete()
                }
                text to lines
            } finally { api.recycle() }
        }
    }

    /** Every word with its position and confidence (0–100), for layout analysis and review of unsure words. */
    fun recognizeWords(ctx: Context, bmp: Bitmap, lang: Lang): List<Layout.Word> = when (lang) {
        Lang.EN -> {
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val r = Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0)))
                r.textBlocks.flatMap { b -> b.lines.flatMap { l -> l.elements.mapNotNull { e -> e.boundingBox?.let { Layout.Word(e.text, it.left, it.top, it.right, it.bottom, e.confidence * 100f) } } } }
            } finally { rec.close() }
        }
        Lang.AR -> {
            val api = TessBaseAPI()
            try {
                check(api.init(tessDir(ctx).absolutePath, "ara+eng")) { "تعذّر تشغيل محرك التعرف" }
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                val k = if (bmp.width < 1200) 2 else 1
                val src = if (k == 2) Bitmap.createScaledBitmap(bmp, bmp.width * 2, bmp.height * 2, true) else bmp
                api.setImage(src.copy(Bitmap.Config.ARGB_8888, false))
                val text = api.getUTF8Text()?.trim().orEmpty()
                val out = ArrayList<Layout.Word>()
                val it = api.resultIterator
                if (it != null) {
                    it.begin()
                    val level = TessBaseAPI.PageIteratorLevel.RIL_WORD
                    do {
                        val t = it.getUTF8Text(level)?.trim().orEmpty(); val r = it.getBoundingRect(level)
                        if (t.isNotEmpty()) out += Layout.Word(t, r.left / k, r.top / k, r.right / k, r.bottom / k, it.confidence(level))
                    } while (it.next(level))
                    it.delete()
                }
                android.util.Log.i("MuyassirOcr", "words=${out.size} chars=${text.length}")
                // Some engines return text without word boxes: keep the text, one pseudo-word per line, so nothing is lost.
                if (out.isEmpty() && text.isNotBlank()) text.lines().filter { it.isNotBlank() }.forEachIndexed { i, l -> out += Layout.Word(l.trim(), 0, i * 40, bmp.width, i * 40 + 30, 50f) }
                out
            } finally { api.recycle() }
        }
    }

    /** One PDF page per image, with the recognised lines drawn as (nearly) invisible text so the PDF can be searched and copied. */
    fun searchablePdf(pages: List<Pair<Bitmap, List<Line>>>, out: java.io.File): java.io.File {
        val doc = android.graphics.pdf.PdfDocument()
        pages.forEachIndexed { i, (bmp, lines) ->
            val w = 595; val h = (bmp.height * 595f / bmp.width).toInt().coerceAtLeast(1); val k = 595f / bmp.width
            val p = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            val c = p.canvas
            c.drawBitmap(bmp, null, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(1, 0, 0, 0) }
            lines.forEach { l ->
                val bh = l.box.height() * k; val bw = l.box.width() * k
                if (bh < 2 || bw < 2) return@forEach
                tp.textSize = bh * 0.85f; tp.textScaleX = 1f
                val tw = tp.measureText(l.text); if (tw > 0) tp.textScaleX = (bw / tw).coerceIn(0.3f, 3f)
                c.drawText(l.text, l.box.left * k, l.box.bottom * k - bh * 0.15f, tp)
            }
            doc.finishPage(p)
        }
        out.outputStream().use { doc.writeTo(it) }; doc.close(); return out
    }

    fun recognize(ctx: Context, bmp: Bitmap, lang: Lang): String = when (lang) {
        Lang.EN -> {
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try { Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0))).text } finally { rec.close() }
        }
        Lang.AR -> {
            val api = TessBaseAPI()
            try {
                check(api.init(tessDir(ctx).absolutePath, "ara+eng")) { "تعذّر تشغيل محرك التعرف" }
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                // Tesseract works best around 300 dpi; upscale small images.
                val src = if (bmp.width < 1200) Bitmap.createScaledBitmap(bmp, bmp.width * 2, bmp.height * 2, true) else bmp
                api.setImage(src.copy(Bitmap.Config.ARGB_8888, false))
                api.getUTF8Text()?.trim() ?: ""
            } finally { api.recycle() }
        }
    }
}
