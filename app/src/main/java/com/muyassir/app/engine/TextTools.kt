package com.muyassir.app.engine

object TextTools {
    data class Counts(val words: Int, val chars: Int, val charsNoSpaces: Int, val lines: Int, val sentences: Int, val paragraphs: Int, val readMinutes: Double)

    fun count(s: String): Counts {
        val words = Regex("[\\p{L}\\p{N}]+(['’][\\p{L}]+)?").findAll(s).count()
        val sentences = Regex("[^.!?؟\\n]+[.!?؟]+").findAll(s).count().let { if (it == 0 && s.isNotBlank()) 1 else it }
        return Counts(
            words, s.length, s.count { !it.isWhitespace() },
            if (s.isEmpty()) 0 else s.lines().size, sentences,
            s.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }, words / 200.0,
        )
    }

    fun dedupe(s: String, ignoreCase: Boolean, trim: Boolean, keepBlank: Boolean): Pair<String, Int> {
        val seen = HashSet<String>(); val out = ArrayList<String>(); var removed = 0
        for (line in s.lines()) {
            if (line.isBlank()) { if (keepBlank) out += line; continue }
            var key = if (trim) line.trim() else line
            if (ignoreCase) key = key.lowercase()
            if (seen.add(key)) out += if (trim) line.trim() else line else removed++
        }
        return out.joinToString("\n") to removed
    }

    enum class Op { SAME, ADDED, REMOVED }
    data class Piece(val op: Op, val text: String)

    /** Longest-common-subsequence diff on lines (or words when `byWord`). */
    fun diff(a: String, b: String, byWord: Boolean): List<Piece> {
        val x = if (byWord) a.split(Regex("\\s+")).filter { it.isNotEmpty() } else a.lines()
        val y = if (byWord) b.split(Regex("\\s+")).filter { it.isNotEmpty() } else b.lines()
        val n = x.size; val m = y.size
        if (n.toLong() * m > 4_000_000) return listOf(Piece(Op.REMOVED, a), Piece(Op.ADDED, b))
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
            dp[i][j] = if (x[i] == y[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        val out = ArrayList<Piece>(); var i = 0; var j = 0
        while (i < n && j < m) when {
            x[i] == y[j] -> { out += Piece(Op.SAME, x[i]); i++; j++ }
            dp[i + 1][j] >= dp[i][j + 1] -> { out += Piece(Op.REMOVED, x[i]); i++ }
            else -> { out += Piece(Op.ADDED, y[j]); j++ }
        }
        while (i < n) out += Piece(Op.REMOVED, x[i++])
        while (j < m) out += Piece(Op.ADDED, y[j++])
        return out
    }

    fun similarity(p: List<Piece>): Double {
        val same = p.count { it.op == Op.SAME }
        val n = p.count { it.op != Op.ADDED }; val m = p.count { it.op != Op.REMOVED }
        return if (n + m == 0) 100.0 else 200.0 * same / (n + m)
    }
}
