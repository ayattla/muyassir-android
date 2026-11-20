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
