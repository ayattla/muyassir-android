package com.muyassir.app.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Category(val id: String, val title: String, val blurb: String, val icon: ImageVector, val color: Color)
data class Tool(val id: String, val cats: List<String>, val title: String, val sub: String, val icon: ImageVector, val keys: String = "")

object Tools {
    val categories = listOf(
        Category("files", "تحويل الملفات", "PDF وWord وExcel وPowerPoint والضغط", Icons.Rounded.SwapHoriz, Color(0xFF3346D3)),
        Category("images", "الصور والتصميم", "ضغط وقص وإزالة خلفية وتحسين", Icons.Rounded.Image, Color(0xFF7340D0)),
        Category("study", "الطالب والمعلم", "المعدل والجداول والاختبارات", Icons.Rounded.School, Color(0xFF0B7F7A)),
        Category("saudi", "خدمات سعودية", "الضريبة والرواتب ونهاية الخدمة", Icons.Rounded.AccountBalance, Color(0xFF23814A)),
        Category("productivity", "الإنتاجية والتنظيم", "مفكرة ومهام وميزانية وتذكيرات", Icons.Rounded.Checklist, Color(0xFFB0600A)),
        Category("docs", "إنشاء المستندات", "سيرة ذاتية وفواتير وخطابات وتوقيع", Icons.Rounded.NoteAdd, Color(0xFFC02A63)),
        Category("calc", "الحسابات والمحولات", "علمية ووحدات وعملات ومساحات", Icons.Rounded.Calculate, Color(0xFF1F6FB8)),
        Category("smart", "أدوات ذكية", "QR ونصوص وصوت", Icons.Rounded.AutoAwesome, Color(0xFFA14522)),
    )

    val all = listOf(
        // ---- files ----
        Tool("pdf_edit", listOf("files"), "محرر PDF", "كتابة وتظليل وإخفاء وتعبئة النماذج", Icons.Rounded.EditNote, "تعديل pdf كتابة تظليل نموذج تعبئة توقيع"),
        Tool("img_word", listOf("files", "docs"), "صور إلى Word", "نص وعناوين وجداول قابلة للتعديل من الصور", Icons.Rounded.DocumentScanner, "صور الى وورد word تحويل صوره مستند جدول"),
        Tool("exam_doc", listOf("study", "files"), "اختبار مصوّر إلى مستند", "يستخرج الأسئلة والاختيارات من صورة الاختبار", Icons.Rounded.FactCheck, "اختبار مصور اسئلة استخراج تحويل ورقة"),
        Tool("pdf_pages", listOf("files"), "ترويسة وتذييل وترقيم", "أضفها لكل صفحات ملف PDF", Icons.Rounded.FormatListNumbered, "ترقيم صفحات ترويسة تذييل header footer"),
        Tool("pdf_word", listOf("files"), "PDF إلى Word", "استخراج النص إلى ملف DOCX قابل للتعديل", Icons.AutoMirrored.Rounded.Article, "pdf word docx تحويل وورد"),
        Tool("word_pdf", listOf("files"), "Word إلى PDF", "تحويل DOCX إلى PDF بخط عربي سليم", Icons.Rounded.PictureAsPdf, "word pdf docx وورد"),
        Tool("pdf_excel", listOf("files"), "PDF إلى Excel", "الأسطر والجداول إلى XLSX", Icons.Rounded.TableChart, "pdf excel xlsx اكسل جدول"),
        Tool("excel_pdf", listOf("files"), "Excel إلى PDF", "جداول XLSX إلى PDF منسّق", Icons.Rounded.GridOn, "excel pdf xlsx اكسل"),
        Tool("pdf_ppt", listOf("files"), "PDF إلى PowerPoint", "كل صفحة شريحة بشكلها الأصلي", Icons.Rounded.Slideshow, "pdf powerpoint pptx عرض شرائح"),
        Tool("ppt_pdf", listOf("files"), "PowerPoint إلى PDF", "نصوص الشرائح إلى صفحات PDF", Icons.Rounded.CoPresent, "powerpoint pptx pdf شرائح"),
        Tool("pdf_img", listOf("files", "images"), "PDF إلى صور", "كل صفحة صورة JPG أو PNG", Icons.Rounded.Collections, "pdf صور jpg png"),
        Tool("img_pdf", listOf("files", "images"), "صور إلى PDF", "اجمع صورك في ملف واحد", Icons.Rounded.PictureAsPdf, "صور pdf تحويل"),
        Tool("pdf_merge", listOf("files"), "دمج PDF", "اجمع عدة ملفات بالترتيب الذي تختاره", Icons.AutoMirrored.Rounded.CallMerge, "دمج pdf merge"),
        Tool("pdf_split", listOf("files"), "تقسيم PDF", "حسب نطاقات أو كل صفحة ملف", Icons.AutoMirrored.Rounded.CallSplit, "تقسيم split pdf"),
        Tool("pdf_compress", listOf("files"), "ضغط PDF", "تصغير حجم الملف بثلاث درجات", Icons.Rounded.Compress, "ضغط تصغير حجم pdf"),
        Tool("pdf_reorder", listOf("files"), "ترتيب صفحات PDF", "غيّر ترتيب الصفحات أو اعكسه", Icons.AutoMirrored.Rounded.Sort, "ترتيب صفحات pdf"),
        Tool("pdf_delete", listOf("files"), "حذف صفحات PDF", "احذف صفحات محددة", Icons.Rounded.DeleteSweep, "حذف صفحات pdf"),
        Tool("pdf_extract", listOf("files"), "استخراج صفحات PDF", "ملف جديد بالصفحات التي تريدها", Icons.Rounded.ContentCut, "استخراج صفحات pdf"),
        Tool("pdf_rotate", listOf("files"), "تدوير صفحات PDF", "90 أو 180 درجة", Icons.Rounded.RotateRight, "تدوير pdf"),
        Tool("ocr", listOf("files", "images", "smart"), "استخراج النص (OCR)", "عربي وإنجليزي من الصور وملفات PDF، دون إنترنت", Icons.Rounded.TextFields, "ocr نص استخراج تعرف ضوئي"),
        Tool("scan", listOf("files"), "مسح المستندات بالكاميرا", "قصّ تلقائي وتحسين وحفظ PDF", Icons.Rounded.DocumentScanner, "مسح سكانر كاميرا scan"),
        Tool("pdf_protect", listOf("files"), "حماية PDF بكلمة مرور", "تشفير ومنع النسخ والطباعة", Icons.Rounded.Lock, "حماية كلمة مرور تشفير pdf"),
        Tool("pdf_unlock", listOf("files"), "إزالة حماية PDF", "لملف تعرف كلمة مروره", Icons.Rounded.LockOpen, "فك حماية pdf"),
        Tool("zip", listOf("files"), "ضغط ZIP", "اجمع ملفات في أرشيف واحد", Icons.Rounded.FolderZip, "zip ضغط ارشيف"),
        Tool("unzip", listOf("files"), "فك ضغط ZIP", "استخرج محتويات الأرشيف", Icons.Rounded.Unarchive, "unzip فك ضغط"),
        Tool("img_convert", listOf("files", "images"), "تحويل صيغ الصور", "JPG وPNG وWEBP", Icons.Rounded.Transform, "تحويل صيغة jpg png webp"),
        // ---- images ----
        Tool("img_compress", listOf("images"), "ضغط الصور", "بجودة تختارها أو حجم مستهدف", Icons.Rounded.Compress, "ضغط صور تصغير"),
        Tool("img_resize", listOf("images"), "تغيير الأبعاد", "بالبكسل أو بالنسبة المئوية", Icons.Rounded.PhotoSizeSelectLarge, "ابعاد مقاس resize"),
        Tool("img_crop", listOf("images"), "قص الصور", "نسب جاهزة أو قص حر", Icons.Rounded.Crop, "قص crop"),
        Tool("img_rotate", listOf("images"), "تدوير وقلب", "تدوير وقلب أفقي وعمودي", Icons.Rounded.RotateRight, "تدوير قلب"),
        Tool("img_enhance", listOf("images"), "تحسين الصور", "إضاءة وتباين وحدّة تلقائية", Icons.Rounded.AutoFixHigh, "تحسين جودة سطوع"),
        Tool("img_bg", listOf("images"), "إزالة الخلفية", "على الجهاز، بصيغة PNG شفافة", Icons.Rounded.Layers, "ازالة خلفية"),
        Tool("img_batch", listOf("images"), "معالجة دفعة واحدة", "ضغط وتحجيم وتحويل عشرات الصور", Icons.Rounded.BurstMode, "دفعة جماعي batch"),
        Tool("id_photo", listOf("images"), "صور الوثائق", "مقاسات الجواز والهوية والتأشيرة", Icons.Rounded.Badge, "صورة شخصية جواز هوية"),
        // ---- study ----
        Tool("gpa", listOf("study"), "حاسبة المعدل", "فصلي وتراكمي، من 5 أو من 4", Icons.Rounded.Grade, "معدل gpa تراكمي"),
        Tool("percent", listOf("study", "calc"), "النسب والدرجات", "النسبة المئوية والتقدير", Icons.Rounded.Percent, "نسبة مئوية درجة"),
        Tool("timetable", listOf("study"), "الجدول الدراسي", "حصص الأسبوع في جدول واحد", Icons.Rounded.CalendarViewWeek, "جدول دراسي حصص"),
        Tool("exams", listOf("study"), "جدول الاختبارات", "مع عدّ تنازلي وتذكير", Icons.AutoMirrored.Rounded.EventNote, "اختبارات امتحانات"),
        Tool("homework", listOf("study"), "تنظيم الواجبات", "حسب المادة وموعد التسليم", Icons.AutoMirrored.Rounded.Assignment, "واجبات"),
        Tool("attendance", listOf("study"), "متابعة الحضور", "سجل حضور وغياب الطلاب", Icons.Rounded.HowToReg, "حضور غياب طلاب"),
        Tool("grades", listOf("study"), "كشف الدرجات", "رصد درجات الطلاب وتصديرها Excel وPDF", Icons.Rounded.Grading, "درجات كشف رصد طلاب معلم"),
        Tool("quiz_maker", listOf("study"), "إنشاء الاختبارات", "أسئلة اختيار وصح وخطأ إلى PDF", Icons.Rounded.Quiz, "انشاء اختبار اسئلة"),
        Tool("worksheet", listOf("study"), "أوراق العمل", "ورقة عمل جاهزة للطباعة", Icons.Rounded.Description, "ورقة عمل"),
        Tool("flashcards", listOf("study"), "بطاقات المراجعة", "احفظ بالتكرار المتباعد", Icons.Rounded.Style, "بطاقات مراجعة"),
        Tool("study_timer", listOf("study"), "مؤقت المذاكرة", "جلسات تركيز واستراحات", Icons.Rounded.Timer, "مؤقت مذاكرة بومودورو"),
        Tool("certificate", listOf("study", "docs"), "شهادات الإنجاز", "شهادة واحدة أو شهادات جماعية من قائمة أسماء", Icons.Rounded.WorkspacePremium, "شهادة شكر تقدير"),
        // ---- saudi ----
        Tool("vat", listOf("saudi", "calc"), "ضريبة القيمة المضافة", "15% إضافة أو استخراج", Icons.Rounded.Receipt, "ضريبة vat قيمة مضافة"),
        Tool("salary", listOf("saudi"), "حاسبة الراتب", "الإجمالي وخصم التأمينات والصافي", Icons.Rounded.Payments, "راتب تأمينات gosi"),
        Tool("eos", listOf("saudi"), "مكافأة نهاية الخدمة", "وفق المادتين 84 و85", Icons.Rounded.Work, "نهاية خدمة مكافأة"),
        Tool("leave", listOf("saudi"), "حاسبة الإجازات", "الرصيد المستحق وقيمته", Icons.Rounded.BeachAccess, "اجازة سنوية رصيد"),
        Tool("service_len", listOf("saudi"), "مدة الخدمة", "من تاريخ المباشرة إلى نهاية العلاقة", Icons.Rounded.Badge, "مده خدمه سنوات عمل"),
        Tool("notice", listOf("saudi"), "التعويض عن مهلة الإشعار", "المادتان 75 و76 من نظام العمل", Icons.Rounded.NotificationImportant, "اشعار انذار مهله تعويض انهاء"),
        Tool("settlement", listOf("saudi"), "التسوية النهائية", "المكافأة والإجازة والتعويضات عند انتهاء العقد", Icons.Rounded.RequestQuote, "تسويه نهائيه مستحقات انهاء فصل استقاله"),
        Tool("entitlements", listOf("saudi"), "المستحقات الوظيفية", "أجر الأيام والعمل الإضافي وبدل الإجازة", Icons.Rounded.Payments, "مستحقات رواتب متاخره اضافي"),
        Tool("wage_deduct", listOf("saudi"), "الاستقطاع من الأجر", "الحد النظامي للخصم من الراتب", Icons.Rounded.RemoveCircleOutline, "خصم استقطاع سلفه جزاء راتب"),
        Tool("finance", listOf("saudi", "calc"), "التمويل والأقساط", "ثابت أو متناقص مع النسبة الفعلية", Icons.Rounded.AccountBalanceWallet, "تمويل قسط قرض"),
        Tool("discount", listOf("saudi", "calc"), "حاسبة الخصومات", "خصم واحد أو خصومات متتالية", Icons.Rounded.LocalOffer, "خصم تخفيض"),
        Tool("hijri", listOf("saudi", "calc"), "التاريخ الهجري والميلادي", "تقويم أم القرى الرسمي", Icons.Rounded.DateRange, "هجري ميلادي تحويل تاريخ"),
        Tool("gov_dir", listOf("saudi"), "دليل المنصات الحكومية", "روابط المنصات الرسمية", Icons.Rounded.Apps, "منصات حكومية ابشر توكلنا"),
        Tool("emergency", listOf("saudi"), "أرقام الطوارئ", "اتصال مباشر بلمسة", Icons.Rounded.LocalHospital, "طوارئ اسعاف شرطة"),
        Tool("zakat", listOf("saudi", "calc"), "حاسبة الزكاة", "النقد والذهب والتجارة مع النصاب", Icons.Rounded.VolunteerActivism, "زكاه زكاة نصاب ذهب"),
        Tool("overtime", listOf("saudi"), "العمل الإضافي", "وفق المادة 107 من نظام العمل", Icons.Rounded.MoreTime, "اضافي اوفر تايم overtime ساعات"),
        Tool("electricity", listOf("saudi", "calc"), "فاتورة الكهرباء", "تقدير بالتعرفة السكنية الحالية والجديدة", Icons.Rounded.Bolt, "كهرباء فاتوره استهلاك كيلو"),
        Tool("date_diff", listOf("calc", "saudi"), "حساب العمر والمدة", "الفرق بين تاريخين بالميلادي والهجري", Icons.Rounded.Cake, "عمر مده فرق تاريخين ميلاد"),
        Tool("documents", listOf("saudi", "productivity"), "الوثائق والمواعيد", "تنبيه قبل انتهاء الوثائق", Icons.Rounded.FolderShared, "وثائق انتهاء هوية جواز موعد"),
        // ---- productivity ----
        Tool("notes", listOf("productivity"), "المفكرة", "ملاحظات سريعة محفوظة على جهازك", Icons.Rounded.StickyNote2, "مفكرة ملاحظات"),
        Tool("tasks", listOf("productivity"), "المهام", "قائمة مهام بأولويات", Icons.Rounded.Checklist, "مهام todo"),
        Tool("calendar", listOf("productivity"), "التقويم", "مواعيدك ومهامك واختباراتك", Icons.Rounded.CalendarMonth, "تقويم"),
        Tool("reminders", listOf("productivity"), "التذكيرات", "تنبيه في الوقت الذي تختاره", Icons.Rounded.Alarm, "تذكير منبه"),
        Tool("budget", listOf("productivity"), "الميزانية الشخصية", "الدخل والمصروف لكل فئة", Icons.Rounded.Savings, "ميزانية"),
        Tool("expenses", listOf("productivity"), "إدارة المصروفات", "سجّل مصروفاتك وصدّرها", Icons.Rounded.ShoppingCart, "مصروفات"),
        // ---- docs ----
        Tool("cv", listOf("docs"), "السيرة الذاتية", "سيرة عربية أنيقة بصيغة PDF", Icons.Rounded.ContactPage, "سيرة ذاتية cv"),
        Tool("invoice", listOf("docs"), "إنشاء الفواتير", "فاتورة ضريبية مبسطة", Icons.Rounded.Receipt, "فاتورة"),
        Tool("quote", listOf("docs"), "عروض الأسعار", "عرض سعر احترافي", Icons.Rounded.RequestQuote, "عرض سعر"),
        Tool("letter", listOf("docs"), "التقارير والخطابات", "خطاب رسمي أو تقرير", Icons.Rounded.Mail, "خطاب تقرير"),
        Tool("sign_pdf", listOf("docs"), "توقيع المستندات", "ارسم توقيعك وضعه على PDF", Icons.Rounded.Draw, "توقيع"),
        Tool("text_pdf", listOf("docs"), "إنشاء PDF", "من نص تكتبه", Icons.AutoMirrored.Rounded.Notes, "انشاء pdf نص"),
        // ---- calc ----
        Tool("scientific", listOf("calc"), "الحاسبة العلمية", "دوال ومثلثات وأسس", Icons.Rounded.Functions, "حاسبة علمية"),
        Tool("units", listOf("calc"), "محول الوحدات", "طول ووزن وحرارة وبيانات", Icons.Rounded.Straighten, "وحدات تحويل"),
        Tool("currency", listOf("calc"), "محول العملات", "بأسعار محدّثة عند الطلب", Icons.Rounded.CurrencyExchange, "عملات دولار ريال"),
        Tool("area_volume", listOf("calc"), "المساحات والأحجام", "12 شكلًا هندسيًا", Icons.Rounded.SquareFoot, "مساحة حجم"),
        // ---- smart ----
        Tool("qr_make", listOf("smart"), "إنشاء رمز QR", "رابط أو نص أو واي فاي أو جهة اتصال", Icons.Rounded.QrCode2, "qr باركود انشاء"),
        Tool("qr_read", listOf("smart"), "قراءة QR والباركود", "بالكاميرا أو من صورة", Icons.Rounded.QrCodeScanner, "قراءة qr باركود"),
        Tool("text_compare", listOf("smart"), "مقارنة النصوص", "إبراز الإضافات والحذف", Icons.AutoMirrored.Rounded.CompareArrows, "مقارنة نصوص"),
        Tool("translate", listOf("smart", "study"), "الترجمة على الجهاز", "عربي وإنجليزي لنص أو صورة أو ملف، دون إنترنت", Icons.Rounded.Translate, "ترجمه ترجم translate انجليزي عربي"),
        Tool("summarize", listOf("smart", "study"), "التلخيص الذكي", "أهم الجمل والكلمات المفتاحية من أي نص أو ملف", Icons.Rounded.Summarize, "تلخيص ملخص لخص summary"),
        Tool("quiz_gen", listOf("study", "smart"), "مولّد أسئلة المراجعة", "أسئلة اختيار من ملاحظاتك وبطاقات مراجعة", Icons.Rounded.Psychology, "اسئله مراجعه اختبار توليد بطاقات"),
        Tool("read_aloud", listOf("smart", "study"), "اقرأ لي", "صوّر ورقة أو اختر ملفًا ليُقرأ بصوت عربي", Icons.Rounded.RecordVoiceOver, "اقرا صوت قراءه نطق مكفوفين"),
        Tool("word_count", listOf("smart"), "عداد الكلمات والحروف", "مع زمن القراءة", Icons.Rounded.FormatSize, "عداد كلمات حروف"),
        Tool("dedupe", listOf("smart"), "إزالة التكرار", "حذف الأسطر المكررة", Icons.Rounded.FilterList, "تكرار حذف"),
        Tool("tts", listOf("smart"), "تحويل النص إلى صوت", "قراءة بصوت عربي وحفظ ملف", Icons.Rounded.RecordVoiceOver, "نص صوت"),
        Tool("stt", listOf("smart"), "تحويل الصوت إلى نص", "إملاء بالعربية والإنجليزية", Icons.Rounded.Mic, "صوت نص املاء"),
    )

    val byId = all.associateBy { it.id }
    fun inCat(cat: String) = all.filter { cat in it.cats }
    fun category(id: String) = categories.first { it.id == id }
    fun colorOf(t: Tool) = category(t.cats.first()).color

    /** Arabic-aware search: ignores diacritics and alef/yaa/taa-marbuta variants. */
    fun norm(s: String) = s.lowercase().replace(Regex("[\\u064B-\\u0652\\u0640]"), "")
        .replace(Regex("[أإآ]"), "ا").replace('ى', 'ي').replace('ة', 'ه')

    /** Words people type → words the tools are described with (Arabic and English). */
    private val synonyms: Map<String, List<String>> = listOf(
        listOf("تصغير", "ضغط", "تقليل", "صغر", "حجم", "compress", "shrink", "reduce", "smaller"),
        listOf("دمج", "جمع", "ضم", "merge", "combine", "join"),
        listOf("تحويل", "حول", "convert", "الى", "to"),
        listOf("تقسيم", "فصل", "قسم", "split"),
        listOf("صوره", "صور", "image", "photo", "picture", "jpg", "png", "صوره"),
        listOf("pdf", "بي دي اف", "مستند"),
        listOf("ورد", "word", "docx"), listOf("اكسل", "excel", "xlsx", "جدول"), listOf("بوربوينت", "powerpoint", "pptx", "عرض"),
        listOf("نص", "كتابه", "ocr", "text", "استخراج"),
        listOf("مسح", "سكانر", "scan", "scanner", "كاميرا"),
        listOf("خلفيه", "background", "bg", "قص الخلفيه"),
        listOf("ابعاد", "مقاس", "resize", "size", "تكبير"),
        listOf("قص", "crop"), listOf("تدوير", "rotate", "قلب"),
        listOf("حمايه", "قفل", "كلمه مرور", "password", "protect", "lock", "تشفير"),
        listOf("ضريبه", "vat", "قيمه مضافه"), listOf("راتب", "رواتب", "salary", "تامينات", "gosi"),
        listOf("معدل", "gpa", "درجات", "نسبه"), listOf("هجري", "تاريخ", "hijri", "ميلادي"),
        listOf("qr", "كيو ار", "باركود", "barcode", "رمز"),
        listOf("صوت", "اقرا", "tts", "speech", "نطق"), listOf("املاء", "تفريغ", "stt", "dictation"),
        listOf("فاتوره", "invoice"), listOf("سيره", "cv", "resume"), listOf("توقيع", "sign", "signature"),
        listOf("عمله", "عملات", "currency", "دولار", "صرف"), listOf("وحدات", "units", "تحويل وحدات"),
        listOf("ملاحظه", "ملاحظات", "notes", "مفكره"), listOf("مهام", "tasks", "todo"), listOf("تذكير", "منبه", "reminder"),
    ).map { g -> g.map { norm(it) } }.flatMap { g -> g.map { it to g } }.toMap()

    private fun expand(word: String): List<String> = (synonyms[word] ?: synonyms.entries.firstOrNull { (k, _) -> k.length > 2 && (word.startsWith(k) || k.startsWith(word)) }?.value ?: emptyList()) + word

    /** Instant search by name, purpose or synonym. Exact matches first, then partial matches. */
    fun search(q: String): List<Tool> {
        val n = norm(q.trim()); if (n.isEmpty()) return emptyList()
        val words = n.split(Regex("\\s+")).filter { it.isNotBlank() && it !in setOf("ال", "من", "في", "على", "الى", "ملف", "ملفات", "اريد", "ابي", "ابغى", "كيف") || n.length <= 3 }.ifEmpty { listOf(n) }
        val hay = all.associateWith { t -> norm("${t.title} ${t.sub} ${t.keys} ${t.id.replace('_', ' ')}") }
        fun score(t: Tool): Int {
            val h = hay.getValue(t); var s = 0; var hits = 0
            words.forEach { w -> val alts = expand(w); if (alts.any { it in h }) { hits++; s += if (alts.any { it in norm(t.title) }) 6 else 3 } }
            if (hits == 0) return 0
            if (hits == words.size) s += 10
            if (norm(t.title).startsWith(n)) s += 12
            return s + (Store.usage[t.id] ?: 0).coerceAtMost(5)
        }
        return all.map { it to score(it) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }

    /** What each kind of user reaches for first (chosen once on first run, changeable in Settings). */
    val byRole = mapOf(
        "student" to listOf("gpa", "flashcards", "study_timer", "exams", "ocr", "scan"),
        "teacher" to listOf("quiz_maker", "worksheet", "exam_doc", "grades", "certificate", "quiz_gen"),
        "employee" to listOf("salary", "settlement", "eos", "overtime", "leave", "letter"),
        "business" to listOf("invoice", "quote", "vat", "sign_pdf", "expenses", "scan"),
    )

    /** Suggestions: what tends to follow the last thing done, then popular starters. */
    fun suggestions(): List<Tool> {
        val follow = mapOf(
            "scan" to listOf("ocr", "pdf_compress", "sign_pdf"), "pdf_merge" to listOf("pdf_compress", "pdf_protect"),
            "img_bg" to listOf("id_photo", "img_resize"), "ocr" to listOf("word_count", "tts"), "invoice" to listOf("vat", "sign_pdf"),
            "pdf_word" to listOf("word_pdf"), "salary" to listOf("eos", "leave"), "gpa" to listOf("percent", "study_timer"),
        )
        val last = Store.recents.firstOrNull()?.tool
        val ids = (follow[last] ?: emptyList()) + (byRole[Prefs.role] ?: listOf("scan", "pdf_merge", "img_bg", "vat", "qr_make", "gpa"))
        return ids.distinct().filter { it != last }.mapNotNull { byId[it] }.take(6)
    }
}
