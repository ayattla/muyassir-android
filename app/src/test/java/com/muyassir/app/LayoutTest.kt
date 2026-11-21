package com.muyassir.app

import com.muyassir.app.engine.ExamParser
import com.muyassir.app.engine.Layout
import com.muyassir.app.engine.Layout.Word
import org.junit.Assert.*
import org.junit.Test

class LayoutTest {
    // Right-to-left words: x decreases along a line.
    private fun line(y: Int, h: Int, vararg ws: Pair<String, Int>) = ws.map { (t, x) -> Word(t, x - t.length * 10, y, x, y + h) }

    @Test fun headingParagraphAndTable() {
        val words = line(0, 40, "تقرير" to 900, "المبيعات" to 820) +
            line(70, 20, "هذا" to 900, "نص" to 860, "عادي" to 830) +
            line(100, 20, "يكمل" to 900, "الفقرة" to 850) +
            line(160, 20, "المنتج" to 900, "الكمية" to 600, "السعر" to 300) +
            line(190, 20, "قلم" to 900, "5" to 600, "10" to 300) +
            line(220, 20, "دفتر" to 900, "2" to 600, "15" to 300)
        val r = Layout.analyze(words)
        assertEquals(Layout.Block.Heading("تقرير المبيعات"), r.blocks[0])
        assertEquals(Layout.Block.Para("هذا نص عادي يكمل الفقرة"), r.blocks[1])
        val t = r.blocks[2] as Layout.Block.Table
        assertEquals(listOf("المنتج", "الكمية", "السعر"), t.rows[0])
        assertEquals(listOf("دفتر", "2", "15"), t.rows[2])
    }

    @Test fun lowConfidenceWordsAreListed() {
        val r = Layout.analyze(listOf(Word("واضح", 0, 0, 50, 20, 95f), Word("غامض", 60, 0, 110, 20, 30f)))
        assertEquals(listOf("غامض"), r.unsure)
    }

    @Test fun editedTextRebuildsTables() {
        val r = Layout.fromText("العنوان\n\nفقرة أولى من النص.\n\nأ | ب\n1 | 2")
        assertTrue(r.blocks[0] is Layout.Block.Heading)
        assertEquals(listOf(listOf("أ", "ب"), listOf("1", "2")), (r.blocks[2] as Layout.Block.Table).rows)
    }

    @Test fun examQuestionsAndChoices() {
        val qs = ExamParser.parse(
            """
            ١) ما عاصمة المملكة العربية السعودية؟
            أ) جدة
            ب) الرياض
            ج) الدمام
            2- الشمس كوكب. ( )
            3) اشرح دورة الماء في الطبيعة
            4. ناتج 2 + 2 يساوي: (أ) 3 (ب) 4 (ج) 5
            """.trimIndent()
        )
        assertEquals(4, qs.size)
        assertEquals(listOf("جدة", "الرياض", "الدمام"), qs[0].choices)
        assertEquals(ExamParser.Kind.CHOICE, qs[0].kind)
        assertEquals(ExamParser.Kind.TRUE_FALSE, qs[1].kind)
        assertEquals(ExamParser.Kind.OPEN, qs[2].kind)
        assertEquals(listOf("3", "4", "5"), qs[3].choices)
        assertEquals("ناتج 2 + 2 يساوي:", qs[3].text)
    }
}

class EduTest {
    @Test fun importsBankFromSpreadsheetRows() {
        val rows = listOf(
            listOf("السؤال", "النوع", "أ", "ب", "ج", "د", "الإجابة", "الدرجة", "المادة", "الصف", "الدرس", "الصعوبة"),
            listOf("عاصمة السعودية؟", "", "جدة", "الرياض", "مكة", "", "ب", "2", "اجتماعيات", "الرابع", "مدن المملكة", "سهل"),
            listOf("الشمس نجم", "صح وخطأ", "", "", "", "", "صح", "1", "علوم", "الخامس", "", "متوسط"),
            listOf("الماء يغلي عند ___ درجة", "", "", "", "", "", "100", "1", "علوم", "", "", ""),
        )
        val qs = com.muyassir.app.tools.Edu.importRows(rows)
        assertEquals(3, qs.size)
        assertEquals(0, qs[0].getInt("type")); assertEquals(1, qs[0].getInt("ans")); assertEquals("الرابع", qs[0].getString("grade"))
        assertEquals(1, qs[1].getInt("type")); assertEquals(0, qs[1].getInt("ans"))
        assertEquals(3, qs[2].getInt("type")); assertEquals("100", qs[2].getString("answer"))
    }

    @Test fun matchingPairsAndBlanks() {
        val q = org.json.JSONObject().put("pairs", "الرياض = السعودية\nالقاهرة = مصر\nسطر بلا علامة")
        assertEquals(listOf("الرياض" to "السعودية", "القاهرة" to "مصر"), com.muyassir.app.tools.Edu.pairs(q))
        assertFalse(com.muyassir.app.tools.Edu.blanked("يغلي عند ___ درجة").contains("___"))
    }

    @Test fun everyStrategyHasSections() {
        val all = com.muyassir.app.tools.Strategies.all
        assertTrue(all.size >= 13)
        all.drop(1).forEach { assertTrue(it.name, it.sections.isNotEmpty()) }
    }
}
