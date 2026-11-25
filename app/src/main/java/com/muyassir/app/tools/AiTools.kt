package com.muyassir.app.tools

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.muyassir.app.core.Files
import com.muyassir.app.core.Plus
import com.muyassir.app.core.Store
import com.muyassir.app.core.TestHooks
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.DocBuilder
import com.muyassir.app.engine.DocBuilder.Block
import com.muyassir.app.engine.ImageEngine
import com.muyassir.app.engine.Ocr
import com.muyassir.app.engine.Office
import com.muyassir.app.engine.PdfEngine
import com.muyassir.app.engine.TextAi
import com.muyassir.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Reads the text of a picked file on the device: PDF text (or OCR for scans), images by OCR, Word, PowerPoint, plain text. */
fun readText(ctx: Context, u: Uri, onStatus: (String) -> Unit = {}): String {
    val name = Files.name(ctx, u).lowercase()
    val f = Files.toCache(ctx, u)
    return when {
        name.endsWith(".pdf") -> {
            val pages = PdfEngine.text(f).joinToString("\n\n").trim()
            if (pages.length > 40) pages else {
                val n = minOf(PdfEngine.pageCount(f), Plus.ocrPages, 30)
                (0 until n).joinToString("\n\n") { i -> onStatus("قراءة الصفحة ${i + 1} من $n…"); val b = PdfEngine.render(f, i, 220); Ocr.recognize(ctx, b, Ocr.Lang.AR).also { b.recycle() } }
            }
        }
        name.endsWith(".docx") -> Office.readDocx(f).joinToString("\n")
        name.endsWith(".pptx") -> Office.readPptx(f).joinToString("\n")
        name.endsWith(".txt") || name.endsWith(".md") -> f.readText()
        else -> { onStatus("قراءة النص من الصورة…"); Ocr.recognize(ctx, ImageEngine.load(ctx, u, 3000), Ocr.Lang.AR) }
    }.trim()
}

/** Text input with "from a file or photo" import, shared by the smart text tools. */
@Composable
fun TextSource(text: String, onText: (String) -> Unit, label: String = "النص") {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u ?: return@rememberLauncherForActivityResult
        scope.launch {
            status = "جار القراءة على جهازك…"
            val r = withContext(Dispatchers.IO) { runCatching { readText(ctx, u) { s -> status = s } } }
            status = r.exceptionOrNull()?.let { "تعذرت قراءة الملف: تأكد أنه سليم." }
            r.getOrNull()?.let { if (it.isBlank()) status = "لم يعثر على نص في الملف." else onText(it) }
        }
    }
    LaunchedEffect(Unit) { TestHooks.inputs?.firstOrNull()?.let { u -> if (text.isBlank()) { val t = withContext(Dispatchers.IO) { runCatching { readText(ctx, u) }.getOrDefault("") }; if (t.isNotBlank()) onText(t) } } }
    Panel {
        TextField2(label, text, onText, Modifier.testTag("sourceText"), lines = 8)
        SecondaryButton("من ملف أو صورة (PDF، Word، صورة)", Icons.Rounded.UploadFile, Modifier.fillMaxWidth()) {
            pick.launch(arrayOf("application/pdf", "image/*", "text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        Text("${text.split(Regex("\\s+")).count { it.isNotBlank() }} كلمة • يقرأ على جهازك ولا يرفع", style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun TranslateTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var dir by remember { mutableIntStateOf(0) } // 0 auto, 1 ar→en, 2 en→ar
    var out by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        TextSource(text, { text = it; out = "" })
        Panel { Segmented(listOf("تلقائي", "عربي ← إنجليزي", "إنجليزي ← عربي"), dir) { dir = it } }
        busy?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(it) } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error")) }
        PrimaryButton("ترجمة", Icons.Rounded.Translate, enabled = text.isNotBlank() && busy == null, modifier = Modifier.testTag("action")) {
            scope.launch {
                error = null; busy = "تجهيز الترجمة على جهازك…"
                val r = withContext(Dispatchers.IO) {
                    runCatching {
                        val src = when (dir) {
                            1 -> TranslateLanguage.ARABIC; 2 -> TranslateLanguage.ENGLISH
                            else -> Tasks.await(LanguageIdentification.getClient().identifyLanguage(text.take(500))).let { if (it == "ar") TranslateLanguage.ARABIC else TranslateLanguage.ENGLISH }
                        }
                        val dst = if (src == TranslateLanguage.ARABIC) TranslateLanguage.ENGLISH else TranslateLanguage.ARABIC
                        val tr = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build())
                        try {
                            busy = "تنزيل حزمة اللغة لأول مرة (مرة واحدة، نحو 30 م.ب)…"
                            Tasks.await(tr.downloadModelIfNeeded(DownloadConditions.Builder().build()))
                            busy = "جار الترجمة على جهازك…"
                            text.split(Regex("\\n")).joinToString("\n") { line -> if (line.isBlank()) line else Tasks.await(tr.translate(line)) }
                        } finally { tr.close() }
                    }
                }
                busy = null
                r.onSuccess { tx ->
                    out = tx
                    val d = Files.outDir(ctx)
                    files = listOf(File(d, "ترجمة.txt").apply { writeText(tx) }, File(d, "ترجمة.docx").also { Office.writeDocx(it, tx.split("\n").map { p -> Office.Para.Text(p) }) })
                    Store.used(t.id); maybeAskForReview(ctx)
                }.onFailure { error = "تعذرت الترجمة. أول استخدام يحتاج اتصالا لتنزيل حزمة اللغة، ثم تعمل دون إنترنت." }
            }
        }
        if (out.isNotBlank()) { Box(Modifier.testTag("results")) { TextResult(out, "الترجمة") }; ResultFiles(files) }
        Hint("ترجمة آلية على جهازك بنماذج Google ML Kit، مناسبة للفهم السريع. راجعها قبل الاستخدام الرسمي.")
    }
}

@Composable
fun SummarizeTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var size by remember { mutableIntStateOf(1) }
    var out by remember { mutableStateOf<List<String>>(emptyList()) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        TextSource(text, { text = it; out = emptyList() })
        Panel { Text("طول الملخص", style = MaterialTheme.typography.labelLarge); Segmented(listOf("قصير", "متوسط", "مفصل"), size) { size = it } }
        PrimaryButton("لخص", Icons.Rounded.Summarize, enabled = text.split(Regex("\\s+")).size >= 30, modifier = Modifier.testTag("action")) {
            if (!Plus.useAi()) return@PrimaryButton
            val n = TextAi.sentences(text).size
            out = TextAi.summarize(text, listOf((n / 6).coerceIn(2, 4), 0, (n / 2).coerceIn(4, 14))[size])
            val d = Files.outDir(ctx)
            val body = out.joinToString("\n") { "• $it" }
            files = listOf(File(d, "ملخص.txt").apply { writeText(body) }, File(d, "ملخص.docx").also { Office.writeDocx(it, out.map { s -> Office.Para.Text("• $s") }) })
            Store.used(t.id); maybeAskForReview(ctx)
        }
        if (text.isNotBlank() && text.split(Regex("\\s+")).size < 30) Text("أضف نصا أطول (30 كلمة على الأقل) للتلخيص.", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
        if (out.isNotEmpty()) {
            Box(Modifier.testTag("results")) { TextResult(out.joinToString("\n") { "• $it" }, "الملخص (${out.size} نقاط)") }
            Panel("الكلمات المفتاحية") { ChipRow { TextAi.keywords(text, 10).forEach { Chip(it) {} } } }
            ResultFiles(files)
        }
        Hint("يختار أهم الجمل من نصك على جهازك ويعرضها بترتيبها، فلا يضيف معلومة ليست في النص. ${if (!Plus.unlocked) "المجاني: تلخيص واحد يوميا." else ""}")
    }
}

@Composable
fun QuizGenTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(1) }
    var qs by remember { mutableStateOf<List<TextAi.Question>>(emptyList()) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var saved by remember { mutableStateOf(false) }
    ToolScreen(t, back) {
        TextSource(text, { text = it; qs = emptyList() }, "ملاحظات الدرس أو نص الكتاب")
        Panel { Text("عدد الأسئلة", style = MaterialTheme.typography.labelLarge); Segmented(listOf("5", "10", "20"), count) { count = it } }
        PrimaryButton("أنشئ أسئلة المراجعة", Icons.Rounded.Quiz, enabled = TextAi.sentences(text).size >= 3, modifier = Modifier.testTag("action")) {
            if (!Plus.useAi()) return@PrimaryButton
            qs = TextAi.quiz(text, listOf(5, 10, 20)[count]); saved = false
            if (qs.isNotEmpty()) {
                val b = com.muyassir.app.core.Templates.apply(ctx, DocBuilder(ctx))
                b.add(Block.Title("أسئلة مراجعة", "اختر الكلمة المناسبة لكل فراغ"))
                qs.forEachIndexed { i, q -> b.add(Block.Para("${i + 1}) ${q.text}", 12f, bold = true)); q.options.forEachIndexed { k, o -> b.add(Block.Para("   ${"أبجد"[k]}) $o")) }; b.add(Block.Space(6f)) }
                b.add(Block.PageBreak); b.add(Block.Heading("الإجابات"))
                qs.forEachIndexed { i, q -> b.add(Block.Para("${i + 1}) ${q.answer}")) }
                files = listOf(b.save(File(Files.outDir(ctx), "أسئلة-مراجعة.pdf")))
                Store.used(t.id); maybeAskForReview(ctx)
            }
        }
        if (qs.isEmpty() && text.isNotBlank() && TextAi.sentences(text).size < 3) Text("أضف ثلاث جمل كاملة على الأقل.", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
        if (qs.isNotEmpty()) {
            Panel("${qs.size} سؤالا", Modifier.testTag("results")) {
                qs.take(5).forEachIndexed { i, q -> Text("${i + 1}) ${q.text}", style = MaterialTheme.typography.bodyMedium); Text("الإجابة: ${q.answer}", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary) }
                if (qs.size > 5) Text("والبقية في ملف PDF.", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
            }
            SecondaryButton(if (saved) "أضيفت إلى بطاقات المراجعة" else "أضفها إلى بطاقات المراجعة", Icons.Rounded.Style, Modifier.fillMaxWidth()) {
                if (!saved) {
                    val now = System.currentTimeMillis()
                    Store.saveList("flashcards", Store.list("flashcards") + qs.mapIndexed { i, q -> JSONObject().put("id", now + i).put("f", q.text).put("b", q.answer).put("box", 1) })
                    saved = true
                }
            }
            ResultFiles(files)
        }
        Hint("تبنى الأسئلة من جملك نفسها على جهازك (أكمل الفراغ بأربعة اختيارات). راجعها قبل توزيعها. ${if (!Plus.unlocked) "المجاني: مرة واحدة يوميا." else ""}")
    }
}

@Composable
fun ReadAloudTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var rate by remember { mutableFloatStateOf(0.9f) }
    var ready by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        tts[0] = TextToSpeech(ctx) { st -> if (st == TextToSpeech.SUCCESS) { tts[0]?.language = Locale("ar", "SA"); ready = true } }
        onDispose { tts[0]?.shutdown() }
    }
    ToolScreen(t, back) {
        TextSource(text, { text = it }, "النص المقروء")
        Panel { Text("السرعة ${fmt(rate.toDouble(), 1)}×", style = MaterialTheme.typography.labelLarge); Slider(rate, { rate = it }, valueRange = 0.5f..1.6f) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (speaking) "يقرأ الآن…" else "اقرأ لي", Icons.Rounded.RecordVoiceOver, enabled = ready && text.isNotBlank(), modifier = Modifier.weight(1f).testTag("action")) {
                tts[0]?.setSpeechRate(rate)
                text.split(Regex("\\n\\s*\\n")).forEachIndexed { i, p -> tts[0]?.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "p$i") }
                speaking = true; Store.used(t.id)
            }
            SecondaryButton("إيقاف", Icons.Rounded.Stop) { tts[0]?.stop(); speaking = false }
        }
        Hint("صور ورقة أو اختر ملفا، وسيقرأ ميسر النص بصوت عربي على جهازك. مفيد لضعاف البصر ولمراجعة الدروس سماعا.")
        if (!ready) Hint("إن لم يعمل الصوت العربي، ثبت حزمة اللغة العربية من إعدادات «تحويل النص إلى كلام» في جهازك.")
    }
}
