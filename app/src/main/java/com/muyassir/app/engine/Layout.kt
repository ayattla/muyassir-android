package com.muyassir.app.engine

/**
 * Document reconstruction from OCR words: lines, headings, paragraphs and tables, plus the words the engine was
 * unsure of so the user can review them before export. Pure Kotlin, so it is unit-tested off-device.
 * Coordinates are image pixels, y grows downwards. Text direction is right-to-left for Arabic.
 */
object Layout {
    data class Word(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val conf: Float = 100f) {
        val cy get() = (top + bottom) / 2; val h get() = bottom - top
    }
    data class Line(val words: List<Word>) {
        val top = words.minOf { it.top }; val bottom = words.maxOf { it.bottom }
        val left = words.minOf { it.left }; val right = words.maxOf { it.right }
        val h get() = bottom - top
        fun text(rtl: Boolean) = (if (rtl) words.sortedByDescending { it.right } else words.sortedBy { it.left }).joinToString(" ") { it.text }
    }
    sealed class Block {
        data class Heading(val text: String) : Block()
        data class Para(val text: String) : Block()
        data class Table(val rows: List<List<String>>) : Block()
    }
    data class Result(val blocks: List<Block>, val unsure: List<String>) {
        fun plain(): String = blocks.joinToString("\n\n") { b -> when (b) { is Block.Heading -> b.text; is Block.Para -> b.text; is Block.Table -> b.rows.joinToString("\n") { it.joinToString(" | ") } } }
    }

    /** Words below this confidence (0–100) are listed for review. */
    const val UNSURE = 60f

    fun isRtl(words: List<Word>): Boolean {
        val letters = words.joinToString("") { it.text }.filter { it.isLetter() }
        return letters.count { it in '؀'..'ۿ' } * 2 >= letters.length
    }

    /** Groups words into lines by vertical overlap. */
    fun lines(words: List<Word>): List<Line> {
        val sorted = words.filter { it.text.isNotBlank() }.sortedBy { it.cy }
        val out = ArrayList<MutableList<Word>>()
        for (w in sorted) {
            val last = out.lastOrNull()
            val lt = last?.let { l -> l.minOf { it.top } to l.maxOf { it.bottom } }
            if (lt != null && w.cy in lt.first..lt.second) last.add(w) else out += mutableListOf(w)
        }
        return out.map { Line(it) }
    }

    /** Splits a line into cells at gaps wider than [gap] pixels. */
    private fun cells(l: Line, gap: Int): List<List<Word>> {
        val ws = l.words.sortedBy { it.left }; val out = ArrayList<MutableList<Word>>()
        for (w in ws) { val c = out.lastOrNull(); if (c != null && w.left - c.last().right <= gap) c.add(w) else out += mutableListOf(w) }
        return out
    }

    fun analyze(words: List<Word>): Result {
        val ls = lines(words); if (ls.isEmpty()) return Result(emptyList(), emptyList())
        val rtl = isRtl(words)
        val medH = ls.map { it.h }.sorted()[ls.size / 2].coerceAtLeast(1)
        val gap = (medH * 1.6).toInt()
        val split = ls.map { cells(it, gap) }
        val blocks = ArrayList<Block>()
        var para = StringBuilder()
        fun flush() { if (para.isNotBlank()) blocks += Block.Para(para.toString().trim()); para = StringBuilder() }
        var i = 0
        while (i < ls.size) {
            // A table: two or more consecutive lines that each split into the same number (≥ 2) of cells.
            val n = split[i].size
            var j = i
            while (j + 1 < ls.size && n >= 2 && split[j + 1].size == n && ls[j + 1].top - ls[j].bottom < medH * 2.5) j++
            if (n >= 2 && j > i) {
                flush()
                val rows = (i..j).map { k -> val cs = split[k].map { c -> Line(c).text(rtl) }; if (rtl) cs.reversed() else cs }
                blocks += Block.Table(rows); i = j + 1; continue
            }
            val l = ls[i]; val t = l.text(rtl)
            val gapAbove = if (i > 0) l.top - ls[i - 1].bottom else 0
            if (l.h > medH * 1.35 && t.length < 80) { flush(); blocks += Block.Heading(t) }
            else {
                if (gapAbove > medH * 1.2) flush()
                if (para.isNotEmpty()) para.append(' ')
                para.append(t)
            }
            i++
        }
        flush()
        val unsure = words.filter { it.conf < UNSURE && it.text.any { c -> c.isLetterOrDigit() } }.map { it.text }.distinct()
        return Result(blocks, unsure)
    }

    /** Converts blocks to Word paragraphs. */
    fun toDocx(r: Result): List<Office.Para> = r.blocks.map { b ->
        when (b) { is Block.Heading -> Office.Para.Text(b.text, 2); is Block.Para -> Office.Para.Text(b.text); is Block.Table -> Office.Para.Table(b.rows) }
    }

    /** Rebuilds blocks from edited plain text (the review screen): "|" separated lines become table rows. */
    fun fromText(text: String): Result {
        val blocks = ArrayList<Block>()
        text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { chunk ->
            val ls = chunk.lines()
            if (ls.size >= 2 && ls.all { it.contains('|') }) blocks += Block.Table(ls.map { l -> l.split('|').map { it.trim() } })
            else if (ls.size == 1 && chunk.length < 60 && !chunk.endsWith('.') && !chunk.endsWith('،')) blocks += Block.Heading(chunk)
            else blocks += Block.Para(ls.joinToString(" "))
        }
        return Result(blocks, emptyList())
    }
}

/** Splits recognised exam text into questions and their choices, so a photographed test becomes editable. */
object ExamParser {
    data class Q(val text: String, val choices: List<String>, val kind: Kind)
    enum class Kind { CHOICE, TRUE_FALSE, OPEN }

    private val digits = "٠١٢٣٤٥٦٧٨٩"
    private fun latin(s: String) = s.map { c -> digits.indexOf(c).takeIf { it >= 0 }?.let { '0' + it } ?: c }.joinToString("")
    private val qStart = Regex("^\\s*(?:س\\s*)?[(\\[]?([0-9]{1,3})\\s*[)(.:\\-ـ/\\]]\\s*(.+)$")
    private val choice = Regex("^\\s*[(\\[]?([أابجدهـabcdABCD])\\s*[)(.:\\-ـ\\]]\\s*(.+)$")
    private val inlineChoices = Regex("\\(?([أبجد])\\s*[).\\-]\\s*([^()]+?)(?=\\s*\\(?[أبجد]\\s*[).\\-]|$)")

    fun parse(text: String): List<Q> {
        val out = ArrayList<Q>()
        var cur: String? = null; var cs = ArrayList<String>()
        fun push() { cur?.let { q -> out += Q(q.trim(), cs.toList(), kind(q, cs)) }; cur = null; cs = ArrayList() }
        for (raw in text.lines()) {
            val l = latin(raw).trim(); if (l.isEmpty()) continue
            val qm = qStart.find(l); val cm = choice.find(l)
            when {
                qm != null && (cm == null || cur == null) -> {
                    push(); var body = qm.groupValues[2]
                    // Choices written on the same line as the question.
                    val inl = inlineChoices.findAll(body).toList()
                    if (inl.size >= 2) { body = body.substring(0, inl.first().range.first).trim(); cs.addAll(inl.map { it.groupValues[2].trim() }) }
                    cur = body
                }
                cm != null && cur != null -> {
                    val inl = inlineChoices.findAll(l).toList()
                    if (inl.size >= 2) cs.addAll(inl.map { it.groupValues[2].trim() }) else cs += cm.groupValues[2].trim()
                }
                cur != null -> if (cs.isEmpty()) cur = cur + " " + l else cs[cs.size - 1] = cs.last() + " " + l
            }
        }
        push()
        return out
    }

    private fun kind(q: String, cs: List<String>) = when {
        cs.size >= 2 -> Kind.CHOICE
        Regex("صح|خطأ|\\(\\s*\\)").containsMatchIn(q) -> Kind.TRUE_FALSE
        else -> Kind.OPEN
    }
}
