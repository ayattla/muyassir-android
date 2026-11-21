package com.muyassir.app.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «مُيسّر بلس»: one place that decides what is free and what needs the subscription.
 *
 * Launch rule (from the monetization plan): the app ships fully free. Limits only start once the store
 * actually offers the Plus products ([live]); until then every gate is open and the paywall says «قريبًا».
 * Everything is counted on the device; nothing is sent anywhere.
 */
object Plus {
    // ---- the free tier, exactly as in the plan ----
    const val FREE_OPS_PER_DAY = 5
    const val FREE_FILES = 5
    const val FREE_MB = 20
    const val FREE_OCR_PAGES = 3
    const val PLUS_FILES = 100
    const val PLUS_MB = 500
    const val FOOTER = "أُنشئ بمُيسّر"

    /** Tools whose runs count toward the daily free operations (files and images). */
    val metered = setOf(
        "pdf_word", "word_pdf", "pdf_excel", "excel_pdf", "pdf_ppt", "ppt_pdf", "pdf_img", "img_pdf", "pdf_merge", "pdf_split",
        "pdf_compress", "pdf_reorder", "pdf_delete", "pdf_extract", "pdf_rotate", "ocr", "pdf_protect", "pdf_unlock",
        "zip", "unzip", "img_convert", "img_compress", "img_resize", "img_crop", "img_rotate", "img_enhance", "img_bg",
        "img_batch", "id_photo", "pdf_edit",
    )

    enum class Reason(val title: String, val body: String) {
        Daily("استخدمت عملياتك المجانية لليوم", "لديك $FREE_OPS_PER_DAY عمليات مجانية يوميًا لأدوات الملفات والصور، وتتجدد غدًا. أو افتحها الآن بلا حدود مع بلس."),
        Files("المعالجة الدفعية من ميزات بلس", "المجاني يعالج حتى $FREE_FILES ملفات في العملية الواحدة. مع بلس حتى $PLUS_FILES ملف دفعة واحدة."),
        Size("الملفات الكبيرة من ميزات بلس", "المجاني يقبل ملفات حتى $FREE_MB م.ب. مع بلس حتى $PLUS_MB م.ب حسب ذاكرة جهازك."),
        Ocr("استخراج النص من ملف كامل من ميزات بلس", "المجاني يستخرج النص من $FREE_OCR_PAGES صفحات في العملية. مع بلس ملف PDF كامل وملف PDF قابل للبحث."),
        Template("القوالب الاحترافية من ميزات بلس", "المجاني يشمل القالب الكلاسيكي. مع بلس أكثر من 20 قالبًا وشعار منشأتك ودون سطر «$FOOTER»."),
        Quality("الدقة العالية من ميزات بلس", "المجاني يحفظ بدقة عادية. مع بلس دقة عالية وألوان خلفية مخصصة."),
        Sizes("كل المقاسات من ميزات بلس", "المجاني يشمل مقاسين. مع بلس كل المقاسات وورقة طباعة جاهزة."),
        Sign("التوقيع المحفوظ والأختام من ميزات بلس", "احفظ توقيعك مرة واحدة، وأضف ختمًا أو علامة مائية لمستنداتك."),
        Export("تصدير السجلات من ميزات بلس", "صدّر المصاريف والحضور والمهام وغيرها إلى Excel وPDF."),
        Lock("قفل الملاحظات والوثائق من ميزات بلس", "احمِ ملاحظاتك ووثائقك ببصمة الإصبع أو الوجه."),
        Look("تخصيص المظهر من ميزات بلس", "اختر لونًا مميزًا للتطبيق وأيقونة بديلة."),
        Support("الدعم ذو الأولوية من ميزات بلس", "رسائلك تصل بعلامة «بلس» ويُرد عليها أولًا."),
        Recipe("الوصفات المخصصة من ميزات بلس", "الوصفات الجاهزة مجانية للجميع. مع بلس اصنع وصفاتك الخاصة واحفظها."),
        Ai("المساعد الذكي من ميزات بلس", "جرّب التلخيص والتدقيق مرة يوميًا مجانًا. مع بلس بلا حدود."),
        Backup("النسخ الاحتياطي من ميزات بلس", "احفظ نسخة مشفّرة من بياناتك في المكان الذي تختاره، واستعدها على جهاز جديد."),
        Menu("مُيسّر بلس", ""),
    }

    /** The store has the Plus products, so limits apply to free users. */
    var live by mutableStateOf(false); internal set
    /** Subscription or day pass is valid now. */
    var subscribed by mutableStateOf(false); internal set
    var dayPassUntil by mutableStateOf(0L); private set
    /** What the subscription is (monthly / yearly / day), for the settings row. */
    var planName by mutableStateOf(""); internal set
    var renews by mutableStateOf(true); internal set
    var usedToday by mutableStateOf(0); private set
    var notifyWhenLive by mutableStateOf(false); private set

    /** The paywall currently requested (null = hidden). */
    var paywall by mutableStateOf<Reason?>(null)

    val active: Boolean get() = subscribed || dayPassUntil > System.currentTimeMillis()
    /** True when a feature is open to this user: Plus, or Plus not launched yet. */
    val unlocked: Boolean get() = active || !live
    val opsLeft: Int get() = if (unlocked) Int.MAX_VALUE else (FREE_OPS_PER_DAY - usedToday).coerceAtLeast(0)

    private var day = ""
    private var loaded = false
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun load(ctx: Context) {
        if (loaded) return; loaded = true; Store.init(ctx)
        val o = Store.obj("plus")
        day = o.optString("day"); usedToday = o.optInt("used"); dayPassUntil = o.optLong("dayPassUntil")
        notifyWhenLive = o.optBoolean("notify"); rollDay()
    }

    private fun save() = runCatching { saveNow() }
    private fun saveNow() = Store.saveObj("plus", JSONObject().put("day", day).put("used", usedToday)
        .put("dayPassUntil", dayPassUntil).put("notify", notifyWhenLive))

    private fun rollDay() { val today = dayFmt.format(Date()); if (today != day) { day = today; usedToday = 0; save() } }

    /** Gate a feature: returns true if it may be used, otherwise shows the paywall with [r]. */
    fun allow(r: Reason): Boolean { if (unlocked) return true; paywall = r; return false }

    /**
     * Check a metered run before it starts. Returns the reason it is blocked, or null.
     * [sizes] are the input sizes in bytes.
     */
    fun isMetered(tool: String) = tool in metered || tool.startsWith("recipe:")

    fun check(tool: String, sizes: List<Long>): Reason? {
        if (!isMetered(tool)) return null
        val maxFiles = if (unlocked) PLUS_FILES else FREE_FILES
        val maxBytes = (if (unlocked) PLUS_MB else FREE_MB) * 1024L * 1024L
        if (!unlocked) { rollDay(); if (usedToday >= FREE_OPS_PER_DAY) return Reason.Daily }
        if (sizes.size > maxFiles && (!unlocked || live)) return Reason.Files
        if (sizes.any { it > maxBytes } && (!unlocked || live)) return Reason.Size
        return null
    }

    /** Count one finished metered run. */
    fun counted(tool: String) { if (isMetered(tool) && !active) { rollDay(); usedToday++; save() } }

    /** Max OCR pages for one run. */
    val ocrPages: Int get() = if (unlocked) Int.MAX_VALUE else FREE_OCR_PAGES

    /** Smart text tools (summary, review questions): free users get one a day, Plus has no limit. */
    fun useAi(): Boolean {
        if (unlocked) return true
        rollDay()
        val o = Store.obj("plus_ai")
        val used = if (o.optString("day") == day) o.optInt("n") else 0
        if (used >= 1) { paywall = Reason.Ai; return false }
        runCatching { Store.saveObj("plus_ai", JSONObject().put("day", day).put("n", used + 1)) }
        return true
    }

    fun grantDayPass(from: Long) { dayPassUntil = maxOf(dayPassUntil, from + 24 * 3600_000L); save() }
    fun toggleNotify() { notifyWhenLive = !notifyWhenLive; save() }
    internal fun clearNotify() { notifyWhenLive = false; save() }

    /** Debug builds only: lets tests put the app in a known state. */
    fun testState(state: String?) {
        when (state) {
            "free" -> { Billing.samplePlans(); live = true; subscribed = false; dayPassUntil = 0; usedToday = 0; save() }
            "free-used" -> { Billing.samplePlans(); live = true; subscribed = false; dayPassUntil = 0; usedToday = FREE_OPS_PER_DAY; day = dayFmt.format(Date()); save() }
            "plus" -> { live = true; subscribed = true; planName = "سنوي" }
            "off" -> { live = false; subscribed = false; dayPassUntil = 0; usedToday = 0; save() }
        }
    }
    fun resetForTest() { loaded = false; live = false; subscribed = false; dayPassUntil = 0; usedToday = 0; day = ""; paywall = null }
}
