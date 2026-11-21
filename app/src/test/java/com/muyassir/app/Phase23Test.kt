package com.muyassir.app

import com.muyassir.app.core.Backup
import com.muyassir.app.engine.TextAi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Phase23Test {
    private val text = """
        الماء ضروري لجميع الكائنات الحية على الأرض. يتكون الماء من ذرتي هيدروجين وذرة أكسجين.
        يغطي الماء نحو سبعين بالمئة من سطح الأرض. يتبخر الماء من البحار ثم يتكاثف في السحب ويهطل مطرًا.
        تسمى هذه العملية دورة الماء في الطبيعة. تعتمد الزراعة على الأمطار والمياه الجوفية بشكل كبير.
        يجب ترشيد استهلاك الماء في المنازل والمزارع للحفاظ عليه. تحلية مياه البحر مصدر مهم في المملكة.
    """.trimIndent()

    @Test fun summaryKeepsOrderAndIsShorter() {
        val all = TextAi.sentences(text); val s = TextAi.summarize(text)
        assertTrue(s.size in 3 until all.size)
        assertEquals(s, all.filter { it in s }) // original order
    }

    @Test fun quizHasFourChoicesWithTheAnswer() {
        val q = TextAi.quiz(text, 5)
        assertTrue(q.isNotEmpty())
        q.forEach { assertTrue(it.answer in it.options); assertTrue("……" in it.text); assertTrue(it.options.size in 2..4) }
    }

    @Test fun backupRoundTripAndWrongPassword() {
        val data = "بيانات تجريبية".toByteArray()
        val enc = Backup.encrypt(data, "كلمة-سر-1")
        assertArrayEquals(data, Backup.decrypt(enc, "كلمة-سر-1"))
        try { Backup.decrypt(enc, "خطأ"); fail("wrong password accepted") } catch (e: IllegalArgumentException) {}
    }
}
