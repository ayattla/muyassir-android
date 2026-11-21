package com.muyassir.app.engine

/**
 * On-device text intelligence that works for Arabic on every phone, with no model download:
 * extractive summaries (the most informative sentences, in their original order) and review questions
 * (fill-in-the-blank with choices) built from the user's own notes.
 */
object TextAi {
    private val stop = setOf(
        "في", "من", "على", "الى", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي", "الذين", "ان", "أن", "إن", "كان", "كانت", "يكون",
        "او", "أو", "ثم", "قد", "لقد", "كما", "لكن", "حتى", "عند", "كل", "بعض", "غير", "بين", "هو", "هي", "هم", "نحن", "انت", "أنت", "لا", "لم", "لن",
        "ما", "ماذا", "متى", "اين", "أين", "كيف", "وهو", "وهي", "و", "ف", "ب", "ل", "به", "بها", "له", "لها", "فيه", "فيها", "منه", "منها", "عليه", "عليها",
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "is", "are", "was", "were", "be", "it", "this", "that", "with", "as", "by", "at", "from",
    )

    fun norm(w: String) = w.lowercase().replace(Regex("[\\u064B-\\u0652\\u0640]"), "").replace(Regex("[أإآ]"), "ا").replace('ى', 'ي').replace('ة', 'ه')
        .trim { !it.isLetterOrDigit() }

    fun sentences(text: String): List<String> = text.replace("\r", "")
        .split(Regex("(?<=[.!?؟])\\s+|\\n+")).map { it.trim() }.filter { it.split(Regex("\\s+")).size >= 4 }

    private fun words(s: String) = s.split(Regex("\\s+")).map { norm(it) }.filter { it.length >= 2 && it !in stop }

    private fun freq(all: List<String>): Map<String, Int> {
        val f = HashMap<String, Int>(); all.forEach { s -> words(s).forEach { f[it] = (f[it] ?: 0) + 1 } }; return f
    }

    /** The most representative sentences (about a quarter of the text, 3–8 sentences), in reading order. */
    fun summarize(text: String, max: Int = 0): List<String> {
        val ss = sentences(text); if (ss.size <= 3) return ss
        val f = freq(ss)
        val n = if (max > 0) max else (ss.size / 4).coerceIn(3, 8)
        val scored = ss.mapIndexed { i, s ->
            val w = words(s); val score = w.sumOf { f[it] ?: 0 }.toDouble() / Math.pow(w.size.coerceAtLeast(1).toDouble(), 0.6)
            Triple(i, s, score + if (i == 0) 0.5 else 0.0)
        }
        return scored.sortedByDescending { it.third }.take(n).sortedBy { it.first }.map { it.second }
    }

    /** Key terms: frequent, long, non-common words. */
    fun keywords(text: String, n: Int = 12): List<String> {
        val f = freq(sentences(text).ifEmpty { listOf(text) })
        return f.entries.filter { it.key.length >= 4 && it.key.any { c -> c.isLetter() } }.sortedByDescending { it.value * it.key.length }.take(n).map { it.key }
    }

    data class Question(val text: String, val answer: String, val options: List<String>)

    /** Fill-in-the-blank questions with four choices, from sentences that contain a key term. */
    fun quiz(text: String, count: Int = 10, seed: Long = 7): List<Question> {
        val ss = sentences(text); val keys = keywords(text, 30); if (keys.size < 2) return emptyList()
        val rnd = java.util.Random(seed)
        val out = ArrayList<Question>(); val used = HashSet<String>()
        for (s in ss) {
            if (out.size >= count) break
            val tokens = s.split(Regex("\\s+"))
            val idx = tokens.indexOfFirst { t -> norm(t) in keys && norm(t) !in used } .takeIf { it >= 0 } ?: continue
            val word = tokens[idx].trim { !it.isLetterOrDigit() }
            val key = norm(word); used += key
            val blank = tokens.toMutableList().also { it[idx] = it[idx].replace(word, "……") }.joinToString(" ")
            val others = keys.filter { it != key }.shuffled(rnd).take(3)
            val opts = (others.map { o -> ss.flatMap { it.split(Regex("\\s+")) }.map { it.trim { c -> !c.isLetterOrDigit() } }.firstOrNull { norm(it) == o } ?: o } + word).shuffled(rnd)
            out += Question(blank, word, opts)
        }
        return out
    }
}
