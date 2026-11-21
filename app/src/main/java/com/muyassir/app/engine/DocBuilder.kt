package com.muyassir.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.muyassir.app.R
import java.io.File
import java.io.FileOutputStream

/**
 * Builds real A4 PDFs from simple blocks with correct Arabic shaping and right-to-left layout
 * (Android's text engine does the shaping). Used by invoices, CVs, letters, certificates, quizzes, worksheets, Word→PDF …
 */
class DocBuilder(ctx: Context, private val landscape: Boolean = false) {
    sealed class Block {
        data class Title(val text: String, val sub: String = "") : Block()
        data class Heading(val text: String) : Block()
        data class Para(val text: String, val size: Float = 11f, val bold: Boolean = false, val center: Boolean = false, val color: Int = INK) : Block()
        data class KeyValues(val rows: List<Pair<String, String>>) : Block()
        data class Table(val header: List<String>, val rows: List<List<String>>, val weights: List<Float>? = null) : Block()
        data class Image(val bmp: Bitmap, val maxHeight: Float = 300f, val center: Boolean = true) : Block()
        data class Space(val h: Float) : Block()
        object Rule : Block()
        object PageBreak : Block()
        data class Lines(val count: Int, val gap: Float = 26f) : Block()
    }

    companion object {
        const val INK = 0xFF101935.toInt(); const val LAPIS = 0xFF2F4BFF.toInt(); const val MUTED = 0xFF5B6480.toInt(); const val LINE = 0xFFD9DEEA.toInt()
    }

    private val regular: Typeface = ResourcesCompat.getFont(ctx, R.font.plex_regular) ?: Typeface.DEFAULT
    private val bold: Typeface = ResourcesCompat.getFont(ctx, R.font.plex_bold) ?: Typeface.DEFAULT_BOLD
    private val pw = if (landscape) 842 else 595
    private val ph = if (landscape) 595 else 842
    private val margin = 42f
    private val cw = pw - 2 * margin

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private lateinit var c: Canvas
    private var y = 0f
    private var pageNo = 0
    var border: Int? = null // certificate frame colour
    /** Text before the page number in the footer (free document tools: «أُنشئ بمُيسّر»); null = page number only. */
    var footer: String? = null
    var pageNumbers = true
    /** Template: accent colour and layout (0 classic, 1 coloured header band, 2 side stripe, 3 formal centred). */
    var accent: Int = LAPIS
    var layoutStyle = 0
    var logo: Bitmap? = null
    private var titled = false
    private var startY = 0f

    private fun newPage() {
        page?.let { finishPage(it) }
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
        page = p; c = p.canvas; y = margin
        if (layoutStyle == 2) c.drawRect(pw - 14f, 0f, pw.toFloat(), ph.toFloat(), Paint().apply { color = accent })
        if (pageNo == 1 && layoutStyle != 1) logoRow()
        border?.let { col ->
            val bt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = col; strokeWidth = 6f }
            c.drawRect(RectF(18f, 18f, pw - 18f, ph - 18f), bt)
            bt.strokeWidth = 1.5f; c.drawRect(RectF(28f, 28f, pw - 28f, ph - 28f), bt)
            y = maxOf(y, margin + 20)
        }
        startY = y
    }

    /** Logo at the top-left of the first page; content starts below it. */
    private fun logoRow() = logo?.let { l ->
        val h = 54f; val w = l.width * h / l.height; val k = minOf(1f, 160f / w)
        c.drawBitmap(l, null, RectF(margin, margin - 10, margin + w * k, margin - 10 + h * k), Paint(Paint.FILTER_BITMAP_FLAG))
        y = margin + h * k + 6
    }

    private fun finishPage(p: PdfDocument.Page) {
        val label = footer?.let { "$it  •  $pageNo" } ?: if (pageNumbers) "$pageNo" else null
        label?.let {
            val tp = paint(8f, false, MUTED)
            c.drawText(it, pw / 2f - tp.measureText(it) / 2, ph - 20f, tp)
        }
        doc.finishPage(p)
    }

    private fun paint(size: Float, b: Boolean, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (b) bold else regular; textSize = size * 1.333f; this.color = color
    }

    private fun layout(text: String, tp: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
        StaticLayout.Builder.obtain(text, 0, text.length, tp, width.toInt())
            .setAlignment(align).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setLineSpacing(0f, 1.15f).setIncludePad(false).build()

    private fun ensure(h: Float) { if (page == null || y + h > ph - margin - 10) newPage() }

    /** Draws a laid-out text, splitting across pages line by line when needed. */
    private fun drawText(l: StaticLayout, x: Float = margin) {
        var line = 0
        while (line < l.lineCount) {
            ensure(l.getLineBottom(line) - l.getLineTop(line).toFloat())
            val top = l.getLineTop(line)
            var last = line
            while (last + 1 < l.lineCount && y + (l.getLineBottom(last + 1) - top) <= ph - margin - 10) last++
            val h = (l.getLineBottom(last) - top).toFloat()
            c.save(); c.translate(x, y - top); c.clipRect(0f, top.toFloat(), l.width.toFloat(), top + h); l.draw(c); c.restore()
            y += h; line = last + 1
        }
    }

    fun add(b: Block) {
        if (page == null) newPage()
        // band layout needs a title first; otherwise fall back to the logo row
        if (layoutStyle == 1 && pageNo == 1 && !titled && b !is Block.Title && y <= startY + 1) { logoRow(); titled = true }
        when (b) {
            is Block.Title -> when {
                layoutStyle == 1 && !titled && pageNo == 1 && y <= startY + 1 -> {
                    // coloured header band across the top of the first page
                    val tl = layout(b.text, paint(22f, true, Color.WHITE), cw - (if (logo != null) 170f else 0f))
                    val sl = if (b.sub.isNotBlank()) layout(b.sub, paint(10.5f, false, 0xE6FFFFFF.toInt()), cw - (if (logo != null) 170f else 0f)) else null
                    val bandH = 28f + tl.height + (sl?.height ?: 0) + 22f
                    c.drawRect(0f, 0f, pw.toFloat(), bandH, Paint().apply { color = accent })
                    logo?.let { l -> val h = 54f; val w = l.width * h / l.height; c.drawBitmap(l, null, RectF(margin, (bandH - h) / 2, margin + minOf(w, 160f), (bandH - h) / 2 + h * minOf(1f, 160f / w)), Paint(Paint.FILTER_BITMAP_FLAG)) }
                    c.save(); c.translate(margin + (if (logo != null) 170f else 0f), 28f); tl.draw(c); c.restore()
                    sl?.let { c.save(); c.translate(margin + (if (logo != null) 170f else 0f), 28f + tl.height + 4); it.draw(c); c.restore() }
                    y = bandH + 22; titled = true
                }
                layoutStyle == 3 -> {
                    drawText(layout(b.text, paint(22f, true, INK), cw, Layout.Alignment.ALIGN_CENTER)); y += 4
                    if (b.sub.isNotBlank()) drawText(layout(b.sub, paint(10.5f, false, MUTED), cw, Layout.Alignment.ALIGN_CENTER))
                    ensure(12f); val pt = Paint().apply { color = accent; strokeWidth = 1.2f }
                    c.drawLine(margin + cw * 0.2f, y + 6, pw - margin - cw * 0.2f, y + 6, pt); c.drawLine(margin + cw * 0.2f, y + 10, pw - margin - cw * 0.2f, y + 10, pt); y += 24
                }
                else -> {
                    drawText(layout(b.text, paint(22f, true, INK), cw)); y += 4
                    if (b.sub.isNotBlank()) { drawText(layout(b.sub, paint(10.5f, false, MUTED), cw)) }
                    ensure(10f); c.drawRect(pw - margin - 56f, y + 6, pw - margin, y + 10, Paint().apply { color = accent }); y += 22; titled = true
                }
            }
            is Block.Heading -> { y += 6; drawText(layout(b.text, paint(14f, true, accent), cw, if (layoutStyle == 3) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)); y += 6 }
            is Block.Para -> {
                drawText(layout(b.text, paint(b.size, b.bold, b.color), cw, if (b.center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)); y += 6
            }
            is Block.KeyValues -> b.rows.forEach { (k, v) ->
                val kl = layout(k, paint(10.5f, true, MUTED), cw * 0.32f)
                val vl = layout(v, paint(10.5f, false, INK), cw * 0.66f)
                val h = maxOf(kl.height, vl.height).toFloat() + 8
                ensure(h)
                c.save(); c.translate(pw - margin - cw * 0.32f, y); kl.draw(c); c.restore()
                c.save(); c.translate(margin, y); vl.draw(c); c.restore()
                y += h
                c.drawLine(margin, y - 4, pw - margin, y - 4, Paint().apply { color = LINE })
            }
            is Block.Table -> table(b)
            is Block.Image -> {
                val scale = minOf(cw / b.bmp.width, b.maxHeight / b.bmp.height, 1.5f)
                val w = b.bmp.width * scale; val h = b.bmp.height * scale
                ensure(h + 6)
                val x = if (b.center) (pw - w) / 2 else pw - margin - w
                c.drawBitmap(b.bmp, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG)); y += h + 8
            }
            is Block.Space -> { y += b.h }
            Block.Rule -> { ensure(10f); c.drawLine(margin, y + 4, pw - margin, y + 4, Paint().apply { color = LINE; strokeWidth = 1f }); y += 12 }
            Block.PageBreak -> newPage()
            is Block.Lines -> repeat(b.count) { ensure(b.gap); y += b.gap; c.drawLine(margin, y, pw - margin, y, Paint().apply { color = LINE }) }
        }
    }

    private fun table(t: Block.Table) {
        val n = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0)
        if (n == 0) return
        val w = (t.weights ?: List(n) { 1f }).let { ws -> val s = ws.sum(); ws.map { it / s * cw } }
        fun row(cells: List<String>, head: Boolean) {
            val ls = (0 until n).map { i -> layout(cells.getOrElse(i) { "" }, paint(9.5f, head, if (head) Color.WHITE else INK), w[i] - 10) }
            val h = (ls.maxOf { it.height } + 10).toFloat()
            ensure(h)
            if (head) c.drawRect(margin, y, pw - margin, y + h, Paint().apply { color = accent })
            var x = pw - margin // first column on the right (RTL table)
            ls.forEachIndexed { i, l ->
                x -= w[i]
                c.save(); c.translate(x + 5, y + 5); l.draw(c); c.restore()
            }
            y += h
            c.drawLine(margin, y, pw - margin, y, Paint().apply { color = LINE })
        }
        if (t.header.isNotEmpty()) row(t.header, true)
        t.rows.forEach { row(it, false) }
        y += 10
    }

    fun addAll(blocks: List<Block>) = blocks.forEach { add(it) }

    fun save(out: File): File {
        if (page == null) newPage()
        page?.let { finishPage(it) }; page = null
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
