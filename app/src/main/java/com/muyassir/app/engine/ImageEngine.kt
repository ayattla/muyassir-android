package com.muyassir.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.TimeUnit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream

object ImageEngine {
    enum class Fmt(val ext: String, val label: String) { JPG("jpg", "JPG"), PNG("png", "PNG"), WEBP("webp", "WEBP") }

    /** Decodes large photos safely by sub-sampling to at most `maxSide` and applying the EXIF rotation. */
    fun load(ctx: Context, uri: Uri, maxSide: Int = 4096): Bitmap {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (o.outWidth / sample > maxSide || o.outHeight / sample > maxSide) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("تعذّر فتح الصورة")
        val rot = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        }.getOrDefault(0)
        return if (rot != 0) rotate(bmp, rot.toFloat()) else bmp
    }

    fun save(bmp: Bitmap, fmt: Fmt, quality: Int, out: File): File {
        val cf = when (fmt) {
            Fmt.JPG -> Bitmap.CompressFormat.JPEG
            Fmt.PNG -> Bitmap.CompressFormat.PNG
            Fmt.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        val src = if (fmt == Fmt.JPG && bmp.hasAlpha()) flatten(bmp, Color.WHITE) else bmp
        FileOutputStream(out).use { src.compress(cf, quality, it) }
        return out
    }

    fun flatten(b: Bitmap, bg: Int): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(bg); drawBitmap(b, 0f, 0f, null) }
        return out
    }

    fun rotate(b: Bitmap, deg: Float, flipH: Boolean = false, flipV: Boolean = false): Bitmap {
        val m = Matrix().apply { postRotate(deg); if (flipH) postScale(-1f, 1f); if (flipV) postScale(1f, -1f) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    fun resize(b: Bitmap, w: Int, h: Int, keepRatio: Boolean): Bitmap {
        if (!keepRatio) return Bitmap.createScaledBitmap(b, w.coerceAtLeast(1), h.coerceAtLeast(1), true)
        val s = minOf(w.toFloat() / b.width, h.toFloat() / b.height)
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }

    /** Centre crop to an aspect ratio (w:h), or a relative rectangle. */
    fun cropRatio(b: Bitmap, rw: Float, rh: Float): Bitmap {
        val target = rw / rh
        return if (b.width.toFloat() / b.height > target) {
            val w = (b.height * target).toInt(); Bitmap.createBitmap(b, (b.width - w) / 2, 0, w, b.height)
        } else {
            val h = (b.width / target).toInt(); Bitmap.createBitmap(b, 0, (b.height - h) / 2, b.width, h)
        }
    }

    fun cropRect(b: Bitmap, l: Float, t: Float, r: Float, btm: Float): Bitmap {
        val x = (l * b.width).toInt().coerceIn(0, b.width - 1); val y = (t * b.height).toInt().coerceIn(0, b.height - 1)
        val w = ((r - l) * b.width).toInt().coerceIn(1, b.width - x); val h = ((btm - t) * b.height).toInt().coerceIn(1, b.height - y)
        return Bitmap.createBitmap(b, x, y, w, h)
    }

    /** Brightness (-100..100), contrast (0.5..2), saturation (0..2), then an optional sharpen pass. */
    fun adjust(b: Bitmap, brightness: Float, contrast: Float, saturation: Float, sharpen: Boolean, grayscale: Boolean = false): Bitmap {
        val cm = ColorMatrix().apply { setSaturation(if (grayscale) 0f else saturation) }
        val t = (1 - contrast) * 128 + brightness
        cm.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return if (sharpen) sharpen(out) else out
    }

    /** Auto-enhance: stretches the luminance histogram (1%–99%) and adds light sharpening. */
    fun autoEnhance(b: Bitmap): Bitmap {
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val hist = IntArray(256)
        for (p in px) hist[((Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000)]++
        var lo = 0; var hi = 255; var acc = 0; val cut = px.size / 100
        while (lo < 255 && acc + hist[lo] < cut) { acc += hist[lo]; lo++ }
        acc = 0; while (hi > 0 && acc + hist[hi] < cut) { acc += hist[hi]; hi-- }
        val scale = 255f / (hi - lo).coerceAtLeast(1)
        return adjust(b, -lo * scale - (1 - scale) * 128, scale, 1.12f, true)
    }

    private fun sharpen(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        if (w < 3 || h < 3 || w.toLong() * h > 16_000_000) return b
        val src = IntArray(w * h); b.getPixels(src, 0, w, 0, 0, w, h)
        val dst = src.copyOf()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            var r = 0; var g = 0; var bl = 0
            fun add(j: Int, k: Int) { val p = src[j]; r += Color.red(p) * k; g += Color.green(p) * k; bl += Color.blue(p) * k }
            add(i, 5); add(i - 1, -1); add(i + 1, -1); add(i - w, -1); add(i + w, -1)
            dst[i] = Color.argb(Color.alpha(src[i]), r.coerceIn(0, 255), g.coerceIn(0, 255), bl.coerceIn(0, 255))
        }
        return Bitmap.createBitmap(dst, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * On-device background removal. Uses ML Kit subject segmentation (any object) when its Google Play services
     * module is installed; otherwise asks Play services to install it in the background and immediately uses the
     * selfie segmentation model bundled inside the app (people), so the tool works offline and on first use.
     * No image ever leaves the device.
     */
    fun removeBackground(ctx: Context, b: Bitmap, onStatus: (String) -> Unit = {}): Bitmap {
        val seg = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        try {
            val installer = ModuleInstall.getClient(ctx)
            val available = runCatching { Tasks.await(installer.areModulesAvailable(seg), 10, TimeUnit.SECONDS).areModulesAvailable() }.getOrDefault(false)
            if (available) {
                val subject = runCatching { Tasks.await(seg.process(InputImage.fromBitmap(b, 0)), 60, TimeUnit.SECONDS).foregroundBitmap }.getOrNull()
                if (subject != null) return subject
            } else runCatching { installer.installModules(ModuleInstallRequest.newBuilder().addApi(seg).build()) }
        } finally { seg.close() }
        onStatus("فصل الشخص عن الخلفية…")
        return selfieCutout(b)
    }

    /** Person cut-out with the bundled ML Kit selfie segmenter: alpha = per-pixel confidence. */
    fun selfieCutout(src: Bitmap): Bitmap {
        val b = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        val client = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
        try {
            val mask = Tasks.await(client.process(InputImage.fromBitmap(b, 0)), 60, TimeUnit.SECONDS)
            val mw = mask.width; val mh = mask.height; val buf = mask.buffer.apply { rewind() }.asFloatBuffer()
            val conf = FloatArray(mw * mh).also { buf.get(it) }
            if (conf.count { it > 0.5f } < conf.size / 200) throw IllegalStateException("لم يُعثر على شخص أو عنصر واضح في الصورة")
            val small = IntArray(mw * mh) { i -> (conf[i].coerceIn(0f, 1f) * 255).toInt() shl 24 }
            val alpha = Bitmap.createScaledBitmap(Bitmap.createBitmap(small, mw, mh, Bitmap.Config.ARGB_8888), b.width, b.height, true)
            val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            val ap = IntArray(px.size); alpha.getPixels(ap, 0, b.width, 0, 0, b.width, b.height)
            for (i in px.indices) px[i] = (px[i] and 0x00FFFFFF) or (ap[i] and 0xFF000000.toInt())
            return Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888)
        } finally { client.close() }
    }

    /** ID/passport photo: fits the subject into W×H mm at 300 dpi on a plain background. */
    fun idPhoto(b: Bitmap, wMm: Float, hMm: Float, bg: Int): Bitmap {
        val w = (wMm / 25.4f * 300).toInt(); val h = (hMm / 25.4f * 300).toInt()
        val crop = cropRatio(b, wMm, hMm)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(bg); drawBitmap(Bitmap.createScaledBitmap(crop, w, h, true), 0f, 0f, null) }
        return out
    }

    /** Compress to a target size (KB) by searching JPEG quality, then scaling down if needed. */
    fun compressToSize(b: Bitmap, targetKb: Int, out: File): File {
        var bmp = b
        repeat(6) {
            var lo = 10; var hi = 95; var best: ByteArray? = null
            while (lo <= hi) {
                val q = (lo + hi) / 2
                val bytes = java.io.ByteArrayOutputStream().also { flatten(bmp, Color.WHITE).compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
                if (bytes.size / 1024 <= targetKb) { best = bytes; lo = q + 1 } else hi = q - 1
            }
            if (best != null) { out.writeBytes(best!!); return out }
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * 0.8).toInt(), (bmp.height * 0.8).toInt(), true)
        }
        save(bmp, Fmt.JPG, 10, out); return out
    }
}
