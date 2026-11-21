package com.muyassir.app

import com.muyassir.app.core.Assistant
import com.muyassir.app.core.Tools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTest {
    @Test fun imagesToPdfThenSmaller() =
        assertEquals(listOf("img_pdf", "pdf_compress"), Assistant.plan("أريد تحويل خمس صور إلى PDF وتقليل حجمه").steps.map { it.tool.id })

    @Test fun mergeThenProtect() =
        assertEquals(listOf("pdf_merge", "pdf_protect"), Assistant.plan("ادمج ملفات PDF واحمها بكلمة مرور").steps.map { it.tool.id })

    @Test fun textFromImage() = assertTrue("ocr" in Assistant.plan("أريد نص هذه الصورة").steps.map { it.tool.id })

    @Test fun unknownFallsBackToSearchOrHelp() = assertTrue(Assistant.plan("كلام لا علاقة له").note != null)

    @Test fun searchUnderstandsPurposeAndEnglish() {
        val shrink = Tools.search("تصغير ملف").map { it.id }
        assertTrue(shrink.toString(), "pdf_compress" in shrink && "img_compress" in shrink)
        assertTrue("pdf_merge" in Tools.search("merge").map { it.id })
        assertEquals("vat", Tools.search("ضريبه").first().id)
    }
}
