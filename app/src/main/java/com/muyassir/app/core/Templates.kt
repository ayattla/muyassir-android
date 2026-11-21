package com.muyassir.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muyassir.app.engine.DocBuilder
import org.json.JSONObject
import java.io.File

/**
 * Document templates for the document tools: 4 layouts × 6 colours = 24 templates.
 * Free: the classic template (index 0) with the small «أُنشئ بمُيسّر» line. Plus: all templates, the business logo, no line.
 */
object Templates {
    data class Style(val index: Int, val name: String, val layout: Int, val accent: Int)

    val layouts = listOf("كلاسيكي", "ترويسة ملوّنة", "شريط جانبي", "رسمي متوسط")
    val palettes = listOf(
        "نيلي" to 0xFF2F4BFF.toInt(), "زمردي" to 0xFF0B7F7A.toInt(), "عنابي" to 0xFF8E1F3C.toInt(),
        "فحمي" to 0xFF2A2F3A.toInt(), "ذهبي" to 0xFFA86A06.toInt(), "بنفسجي" to 0xFF6A3FC4.toInt(),
    )
    val all: List<Style> = layouts.flatMapIndexed { li, l -> palettes.mapIndexed { pi, (pn, c) -> Style(li * palettes.size + pi, "$l • $pn", li, c) } }

    var selected by mutableIntStateOf(0); private set
    var hasLogo by mutableStateOf(false); private set
    private var loaded = false

    private fun logoFile(ctx: Context) = File(ctx.filesDir, "data/logo.png")

    fun load(ctx: Context) {
        if (loaded) return; loaded = true; Store.init(ctx)
        selected = Store.obj("templates").optInt("selected", 0).coerceIn(0, all.lastIndex)
        hasLogo = logoFile(ctx).exists()
    }

    fun choose(i: Int) { selected = i; Store.saveObj("templates", JSONObject().put("selected", i)) }

    fun setLogo(ctx: Context, bmp: Bitmap?) {
        val f = logoFile(ctx)
        if (bmp == null) f.delete() else { f.parentFile?.mkdirs(); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        hasLogo = f.exists()
    }

    fun logo(ctx: Context): Bitmap? = logoFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    /** The template actually used: free users always get the classic one. */
    val current: Style get() = if (Plus.unlocked) all[selected] else all[0]

    /** Apply the chosen template, logo and footer to a document-tool PDF. */
    fun apply(ctx: Context, b: DocBuilder, useLogo: Boolean = true): DocBuilder {
        load(ctx)
        val st = current
        b.layoutStyle = st.layout; b.accent = st.accent
        if (Plus.unlocked && useLogo) b.logo = logo(ctx)
        b.footer = if (Plus.unlocked) null else Plus.FOOTER
        return b
    }
}
