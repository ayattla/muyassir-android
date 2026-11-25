package com.muyassir.app.core

/**
 * Offline helper that reads what the user wants to do and suggests the tools and the order to use them.
 * It only guides: it never claims to have done anything. Nothing is sent anywhere.
 */
object Assistant {
    data class Step(val tool: Tool, val why: String)
    data class Plan(val steps: List<Step>, val note: String?)

    private class Rule(val tool: String, val why: String, val all: List<List<String>>, val none: List<String> = emptyList())

    // Each rule fires when every group has at least one word in the request. Order of the final plan follows the text.
    private val rules = listOf(
        Rule("img_pdf", "اجمع الصور في ملف PDF واحد", listOf(listOf("صور", "صوره", "image", "photo"), listOf("pdf"))),
        Rule("pdf_img", "حول صفحات PDF إلى صور", listOf(listOf("pdf"), listOf("الى صور", "الى صوره", "to image", "to jpg", "صفحات صور"))),
        Rule("pdf_merge", "ادمج ملفات PDF بالترتيب الذي تريده", listOf(listOf("دمج", "ادمج", "اجمع", "جمع", "ضم", "merge"), listOf("pdf", "ملفات", "مستندات")), none = listOf("صور")),
        Rule("pdf_compress", "صغر حجم ملف PDF", listOf(listOf("ضغط", "تصغير", "تقليل", "صغر", "حجم", "compress"), listOf("pdf", "ملف", "مستند"))),
        Rule("img_compress", "صغر حجم الصور مع الحفاظ على الوضوح", listOf(listOf("ضغط", "تصغير", "تقليل", "حجم", "compress"), listOf("صور", "صوره", "image", "photo")), none = listOf("pdf")),
        Rule("pdf_word", "حول PDF إلى ملف Word قابل للتعديل", listOf(listOf("pdf"), listOf("ورد", "word", "تعديل"))),
        Rule("word_pdf", "حول ملف Word إلى PDF", listOf(listOf("ورد", "word", "docx"), listOf("pdf")), none = listOf("pdf الى ورد", "pdf الى word")),
        Rule("pdf_excel", "استخرج الجداول من PDF إلى Excel", listOf(listOf("pdf"), listOf("اكسل", "excel"))),
        Rule("ocr", "استخرج النص من الصورة أو الملف الممسوح", listOf(listOf("نص", "كلام", "كتابه", "ocr", "text", "انسخ"), listOf("صور", "صوره", "ممسوح", "مصور", "image", "pdf", "استخراج", "استخرج"))),
        Rule("scan", "صور الأوراق بالكاميرا مع قص وتحسين تلقائي", listOf(listOf("مسح", "اسكان", "scan", "صور ورق", "اصور", "كاميرا", "ورقه", "اوراق")), none = listOf("ورقه عمل", "اوراق عمل")),
        Rule("img_bg", "أزل خلفية الصورة", listOf(listOf("خلفيه", "background"))),
        Rule("id_photo", "جهز صورة بمقاس الجواز أو الهوية", listOf(listOf("جواز", "هويه", "شخصيه", "passport", "تاشيره"))),
        Rule("img_resize", "غير أبعاد الصورة", listOf(listOf("ابعاد", "مقاس", "resize", "تكبير", "عرض وارتفاع"))),
        Rule("pdf_protect", "احم الملف بكلمة مرور", listOf(listOf("حمايه", "احمي", "كلمه مرور", "قفل", "password", "protect"))),
        Rule("pdf_split", "قسم الملف إلى أجزاء", listOf(listOf("تقسيم", "قسم", "split", "فصل صفحات"))),
        Rule("sign_pdf", "وقع المستند بخط يدك", listOf(listOf("توقيع", "وقع", "امضاء", "sign"))),
        Rule("vat", "احسب ضريبة القيمة المضافة", listOf(listOf("ضريبه", "vat", "قيمه مضافه"))),
        Rule("salary", "احسب صافي الراتب وخصم التأمينات", listOf(listOf("راتب", "salary", "تامينات"))),
        Rule("eos", "احسب مكافأة نهاية الخدمة", listOf(listOf("نهايه الخدمه", "مكافاه", "استقاله"))),
        Rule("gpa", "احسب معدلك الفصلي والتراكمي", listOf(listOf("معدل", "gpa"))),
        Rule("invoice", "أنشئ فاتورة", listOf(listOf("فاتوره", "invoice"))),
        Rule("cv", "أنشئ سيرة ذاتية", listOf(listOf("سيره", "cv", "resume"))),
        Rule("qr_make", "أنشئ رمز QR", listOf(listOf("qr", "باركود", "رمز"), listOf("انشاء", "انشئ", "اعمل", "سو", "صمم", "create", "make"))),
        Rule("qr_read", "اقرأ رمز QR", listOf(listOf("qr", "باركود"), listOf("اقرا", "قراءه", "مسح", "scan", "read"))),
        Rule("tts", "حول النص إلى صوت", listOf(listOf("صوت", "اقرا النص", "نطق", "tts"))),
        Rule("zip", "اجمع الملفات في أرشيف ZIP", listOf(listOf("zip", "ارشيف", "مضغوط"))),
        Rule("settlement", "احسب التسوية النهائية: المكافأة والإجازة والتعويضات", listOf(listOf("مستحقات", "تسويه", "مخالصه", "حقوقي"))),
        Rule("notice", "احسب التعويض عن مهلة الإشعار", listOf(listOf("مهله الاشعار", "اشعار", "انذار"), listOf("تعويض", "احسب", "كم", "مهله"))),
        Rule("service_len", "احسب مدة خدمتك", listOf(listOf("مده الخدمه", "مده خدمتي", "سنوات الخدمه"))),
        Rule("wage_deduct", "اعرف الحد النظامي للخصم من الراتب", listOf(listOf("خصم", "استقطاع"), listOf("راتب", "اجر", "سلفه", "جزاء"))),
        Rule("img_word", "حول الصور إلى ملف Word قابل للتعديل", listOf(listOf("صور", "صوره", "مصور", "image"), listOf("ورد", "word")), none = listOf("pdf الى")),
        Rule("pdf_excel", "استخرج الجداول إلى Excel", listOf(listOf("جداول", "جدول"), listOf("استخرج", "استخراج", "اكسل", "excel", "من"))),
        Rule("exam_doc", "حول الاختبار المصور إلى مستند قابل للتعديل", listOf(listOf("اختبار", "امتحان", "اسئله"), listOf("مصور", "صوره", "صور", "ورقه"))),
        Rule("quiz_gen", "ولد أسئلة مراجعة من ملفك أو ملاحظاتك", listOf(listOf("اختبار", "اسئله", "امتحان"), listOf("من هذا الملف", "من الملف", "من ملاحظاتي", "من الدرس", "من النص"))),
        Rule("quiz_maker", "أنشئ اختبارا وصدره PDF وWord", listOf(listOf("اختبار", "امتحان", "اسئله"), listOf("انشئ", "انشاء", "اعمل", "صمم", "جهز", "اكتب")), none = listOf("مصور", "من هذا الملف", "من الملف", "من ملاحظاتي")),
        Rule("worksheet", "أنشئ ورقة عمل بقالب الاستراتيجية التي تريد", listOf(listOf("ورقه عمل", "اوراق عمل", "نشاط", "بطاقه خروج"))),
        Rule("summarize", "لخص النص أو الملف", listOf(listOf("لخص", "تلخيص", "ملخص"))),
        Rule("translate", "ترجم بين العربية والإنجليزية على جهازك", listOf(listOf("ترجم", "ترجمه", "translate"))),
        Rule("grades", "ارصد درجات الطلاب وصدرها", listOf(listOf("درجات", "رصد", "كشف"), listOf("طلاب", "طالب", "الصف", "رصد"))),
        Rule("certificate", "أنشئ شهادات شكر أو تفوق", listOf(listOf("شهاده", "شهادات"))),
        Rule("pdf_pages", "أضف ترقيم الصفحات أو ترويسة لملف PDF", listOf(listOf("ترقيم", "ترويسه", "تذييل"))),
        Rule("currency", "حول بين العملات", listOf(listOf("عمله", "عملات", "دولار", "صرف", "currency"))),
    )

    fun plan(request: String): Plan {
        val text = Tools.norm(request)
        if (text.isBlank()) return Plan(emptyList(), null)
        val hits = rules.mapNotNull { r ->
            val ok = r.all.all { g -> g.any { Tools.norm(it) in text } } && r.none.none { Tools.norm(it) in text }
            if (!ok) null else {
                val pos = r.all.first().mapNotNull { w -> text.indexOf(Tools.norm(w)).takeIf { it >= 0 } }.minOrNull() ?: 0
                Tools.byId[r.tool]?.let { Triple(pos, it, r.why) }
            }
        }.distinctBy { it.second.id }
        // When images become a PDF and the user also wants it smaller, the PDF step comes first, then compression.
        var steps = hits.sortedBy { it.first }.map { Step(it.second, it.third) }
        val ids = steps.map { it.tool.id }
        if ("img_pdf" in ids && "img_compress" in ids && "pdf" in text) steps = steps.filter { it.tool.id != "img_compress" }
        if ("img_pdf" in ids && "pdf_compress" in ids) steps = steps.sortedBy { if (it.tool.id == "img_pdf") 0 else 1 }
        if (steps.isEmpty()) {
            val found = Tools.search(request).take(3)
            return Plan(found.map { Step(it, it.sub) }, if (found.isEmpty()) "لم أفهم الطلب بعد. جرب وصفا مثل: «حول خمس صور إلى PDF وصغر حجمه»." else "أقرب الأدوات لطلبك:")
        }
        return Plan(steps, if (steps.size > 1) "نفذ الخطوات بالترتيب. افتح كل أداة واختر ملفاتك فيها." else null)
    }

    private val subjects = listOf("رياضيات", "علوم", "لغتي", "لغه عربيه", "انجليزي", "اجتماعيات", "فيزياء", "كيمياء", "احياء", "دراسات اسلاميه", "توحيد", "فقه", "حديث", "تفسير", "قران", "حاسب", "مهارات رقميه", "تربيه فنيه")
    private val gradeWords = listOf("الاول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس")

    /** Details found in the request (grade, subject, count) that the opened tool fills in for the user. */
    fun details(request: String): org.json.JSONObject {
        val t = Tools.norm(request); val o = org.json.JSONObject()
        Regex("(?:لل?صف|صف)\\s+(\\S+)(?:\\s+(متوسط|ثانوي|ابتدائي))?").find(t)?.let { m ->
            val w = m.groupValues[1]; if (w in gradeWords || w.any { it.isDigit() }) o.put("grade", "الصف $w" + (m.groupValues[2].takeIf { it.isNotBlank() }?.let { " $it" } ?: ""))
        }
        subjects.firstOrNull { Tools.norm(it) in t }?.let { o.put("subject", it) }
        Regex("(\\d{1,2})\\s*(?:سؤال|اسئله)").find(t)?.let { o.put("count", it.groupValues[1]) }
        return o
    }

    /** Hands the request's details to the tool that will open (read once by that tool). */
    fun prefill(request: String) { Store.saveObj("assist_prefill", details(request)) }
    fun takePrefill(): org.json.JSONObject = Store.obj("assist_prefill").also { Store.saveObj("assist_prefill", org.json.JSONObject()) }

    /** A built-in recipe that does the whole plan in one tap, if there is one. */
    fun recipeFor(ids: List<String>): String? {
        val set = ids.toSet()
        return when {
            "img_pdf" in set && "pdf_compress" in set -> "doc"
            "pdf_merge" in set && "pdf_compress" in set -> "merge"
            set == setOf("ocr") -> "text"
            set.isNotEmpty() && set.all { it in setOf("img_compress", "img_resize") } -> "light"
            else -> null
        }
    }
}
