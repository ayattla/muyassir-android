package com.muyassir.app.tools

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.Expr
import com.muyassir.app.engine.ImageEngine
import com.muyassir.app.engine.TextTools
import com.muyassir.app.engine.Units
import com.muyassir.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.Locale

fun qrBitmap(text: String, size: Int = 900): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
    val px = IntArray(size * size) { if (m.get(it % size, it / size)) 0xFF101935.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun QrMake(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var kind by remember { mutableIntStateOf(0) }
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; var c by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    ToolScreen(t, back) {
        Panel {
            Segmented(listOf("رابط/نص", "واي فاي", "جهة اتصال"), kind) { kind = it; result = emptyList() }
            when (kind) {
                0 -> TextField2("الرابط أو النص", a, { a = it }, lines = 3)
                1 -> { TextField2("اسم الشبكة", a, { a = it }); TextField2("كلمة المرور", b, { b = it }) }
                else -> { TextField2("الاسم", a, { a = it }); TextField2("الجوال", b, { b = it }); TextField2("البريد", c, { c = it }) }
            }
        }
        val content = when (kind) {
            0 -> a
            1 -> if (a.isBlank()) "" else "WIFI:T:WPA;S:${a.replace(";", "\;")};P:${b.replace(";", "\;")};;"
            else -> if (a.isBlank()) "" else "BEGIN:VCARD\nVERSION:3.0\nFN:$a\nTEL:$b\nEMAIL:$c\nEND:VCARD"
        }
        if (content.isNotBlank()) {
            val bmp = remember(content) { qrBitmap(content) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Image(bmp.asImageBitmap(), "رمز QR", Modifier.size(240.dp).clip(RoundedCornerShape(12.dp))) }
            PrimaryButton("حفظ الصورة", Icons.Rounded.Save) {
                result = listOf(ImageEngine.save(bmp, ImageEngine.Fmt.PNG, 100, File(Files.outDir(ctx), "رمز-QR.png"))); Store.used(t.id, a.take(40))
            }
        }
        if (result.isNotEmpty()) ResultFiles(result)
    }
}

@Composable
fun QrRead(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        u ?: return@rememberLauncherForActivityResult
        runCatching {
            val bmp = ImageEngine.load(ctx, u, 2000)
            val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, px))), mapOf(DecodeHintType.TRY_HARDER to true)).text
        }.onSuccess { value = it; error = null; Store.used(t.id, it.take(40)) }.onFailure { error = "لم يُعثر على رمز في الصورة" }
    }
    ToolScreen(t, back) {
        PrimaryButton("مسح بالكاميرا", Icons.Rounded.QrCodeScanner) {
            GmsBarcodeScanning.getClient(ctx).startScan()
                .addOnSuccessListener { value = it.rawValue ?: ""; error = null; Store.used(t.id, value.take(40)) }
                .addOnFailureListener { error = "الماسح غير متاح. تأكد من تحديث خدمات Google Play." }
        }
        SecondaryButton("قراءة من صورة", Icons.Rounded.Image, Modifier.fillMaxWidth()) { pick.launch("image/*") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (value.isNotBlank()) {
            TextResult(value, "المحتوى")
            if (value.startsWith("http")) SecondaryButton("فتح الرابط", Icons.Rounded.OpenInNew, Modifier.fillMaxWidth()) { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(value))) }
            Hint("تأكد من الرابط قبل فتحه؛ رموز QR قد تُستخدم للاحتيال.")
        }
    }
}

@Composable
fun TextCompare(t: Tool, back: () -> Unit) {
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; var byWord by remember { mutableIntStateOf(0) }
    ToolScreen(t, back) {
        Panel { TextField2("النص الأول (الأصلي)", a, { a = it }, lines = 5); TextField2("النص الثاني (المعدّل)", b, { b = it }, lines = 5); Segmented(listOf("بالأسطر", "بالكلمات"), byWord) { byWord = it } }
        if (a.isNotBlank() && b.isNotBlank()) {
            val d = remember(a, b, byWord) { TextTools.diff(a, b, byWord == 1) }
            ResultCard("نسبة التطابق" to "${fmt(TextTools.similarity(d), 1)}%", listOf("إضافات" to "${d.count { it.op == TextTools.Op.ADDED }}", "حذف" to "${d.count { it.op == TextTools.Op.REMOVED }}"))
            Panel("الفروق") {
                d.forEach { p ->
                    val (bg, prefix) = when (p.op) { TextTools.Op.ADDED -> Color(0x3312C29A) to "+ "; TextTools.Op.REMOVED -> Color(0x33E5484D) to "− "; else -> Color.Transparent to "  " }
                    Text(prefix + p.text, Modifier.fillMaxWidth().background(bg).padding(4.dp), textDecoration = if (p.op == TextTools.Op.REMOVED) TextDecoration.LineThrough else null)
                }
            }
        }
    }
}

@Composable
fun WordCount(t: Tool, back: () -> Unit) {
    var s by remember { mutableStateOf("") }
    ToolScreen(t, back) {
        TextField2("الصق النص هنا", s, { s = it }, lines = 8)
        val c = TextTools.count(s)
        ResultCard("الكلمات" to "${c.words}", listOf("الحروف بالمسافات" to "${c.chars}", "الحروف دون مسافات" to "${c.charsNoSpaces}", "الجمل" to "${c.sentences}", "الفقرات" to "${c.paragraphs}", "الأسطر" to "${c.lines}", "زمن القراءة" to "${fmt(c.readMinutes.coerceAtLeast(0.0), 1)} دقيقة"))
    }
}

@Composable
fun Dedupe(t: Tool, back: () -> Unit) {
    var s by remember { mutableStateOf("") }; var ic by remember { mutableStateOf(true) }; var tr by remember { mutableStateOf(true) }; var kb by remember { mutableStateOf(false) }
    ToolScreen(t, back) {
        TextField2("النص (سطر لكل عنصر)", s, { s = it }, lines = 8)
        Panel { SwitchRow("تجاهل حالة الأحرف", ic) { ic = it }; SwitchRow("تجاهل المسافات الزائدة", tr) { tr = it }; SwitchRow("الإبقاء على الأسطر الفارغة", kb) { kb = it } }
        if (s.isNotBlank()) {
            val (out, removed) = TextTools.dedupe(s, ic, tr, kb)
            ResultCard("حُذف" to "$removed سطر مكرر", listOf("المتبقي" to "${out.lines().count { it.isNotBlank() }} سطر"))
            TextResult(out)
        }
    }
}

@Composable
fun TtsTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var rate by remember { mutableFloatStateOf(1f) }
    var ready by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<List<File>>(emptyList()) }
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        tts[0] = TextToSpeech(ctx) { st -> if (st == TextToSpeech.SUCCESS) { tts[0]?.language = Locale("ar", "SA"); ready = true } }
        onDispose { tts[0]?.shutdown() }
    }
    ToolScreen(t, back) {
        TextField2("النص", text, { text = it }, lines = 8)
        Panel { Text("السرعة ${fmt(rate.toDouble(), 1)}×"); Slider(rate, { rate = it }, valueRange = 0.5f..2f) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("استماع", Icons.Rounded.PlayArrow, enabled = ready && text.isNotBlank(), modifier = Modifier.weight(1f)) { tts[0]?.setSpeechRate(rate); tts[0]?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "p") }
            SecondaryButton("إيقاف", Icons.Rounded.Stop) { tts[0]?.stop() }
        }
        SecondaryButton("حفظ ملف صوتي", Icons.Rounded.Save, Modifier.fillMaxWidth()) {
            val f = File(Files.outDir(ctx), "نص-مقروء.wav")
            tts[0]?.setSpeechRate(rate)
            tts[0]?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { if (id == "file") { result = listOf(f); Store.used(t.id) } }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) {}
            })
            tts[0]?.synthesizeToFile(text, null, f, "file")
        }
        if (!ready) Hint("إن لم يعمل الصوت العربي، ثبّت حزمة اللغة العربية من إعدادات «تحويل النص إلى كلام» في جهازك.")
        if (result.isNotEmpty()) ResultFiles(result)
    }
}

@Composable
fun SttTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var lang by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
            text = (text + " " + said).trim(); Store.used(t.id)
        }
    }
    ToolScreen(t, back) {
        Panel { Segmented(listOf("العربية", "English"), lang) { lang = it } }
        PrimaryButton("ابدأ الإملاء", Icons.Rounded.Mic) {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == 0) "ar-SA" else "en-US").putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            runCatching { launcher.launch(i) }.onFailure { toast(ctx, "خدمة التعرف على الكلام غير متاحة في جهازك") }
        }
        if (text.isNotBlank()) { TextResult(text); TextButton(onClick = { text = "" }) { Text("مسح") } }
        Hint("يستخدم خدمة التعرف على الكلام في جهازك، ويعمل دون إنترنت إذا كانت حزمة اللغة منزّلة.")
    }
}

@Composable
fun ScientificCalc(t: Tool, back: () -> Unit) {
    var expr by remember { mutableStateOf("") }
    var deg by remember { mutableStateOf(true) }
    var memory by remember { mutableStateOf("") }
    val result = remember(expr, deg) { if (expr.isBlank()) "" else runCatching { fmt(Expr(expr, deg).eval(), 10) }.getOrElse { "…" } }
    val keys = listOf(
        listOf("sin(", "cos(", "tan(", "π", "e"), listOf("ln(", "log(", "√(", "^", "!"),
        listOf("(", ")", "%", "AC", "⌫"), listOf("7", "8", "9", "÷", "Ans"), listOf("4", "5", "6", "×", "M"), listOf("1", "2", "3", "−", "MR"), listOf("0", ".", "E", "+", "="),
    )
    ToolScreen(t, back) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Ink).padding(18.dp), horizontalAlignment = Alignment.End) {
            Text(expr.ifEmpty { "0" }, color = Color(0xFFB7BFDB), fontSize = 22.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Text(result, color = Color.White, fontSize = 36.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
        Segmented(listOf("درجات", "راديان"), if (deg) 0 else 1) { deg = it == 0 }
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            val op = k in listOf("÷", "×", "−", "+", "=")
                            Box(
                                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (k == "=") MaterialTheme.colorScheme.primary else if (op) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        when (k) {
                                            "AC" -> expr = ""
                                            "⌫" -> expr = expr.dropLast(1)
                                            "=" -> if (result.isNotBlank() && result != "…") { expr = result.replace(",", ""); Store.used(t.id) }
                                            "Ans" -> expr += result.replace(",", "")
                                            "M" -> memory = result.replace(",", "")
                                            "MR" -> expr += memory
                                            "E" -> expr += "e"
                                            else -> expr += k
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(k, fontSize = 18.sp, color = if (k == "=") Color.White else MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnitsTool(t: Tool, back: () -> Unit) {
    var kind by remember { mutableStateOf(Units.kinds[0]) }
    var from by remember(kind) { mutableStateOf(kind.units[0]) }
    var to by remember(kind) { mutableStateOf(kind.units[1]) }
    var v by remember { mutableStateOf("1") }
    ToolScreen(t, back) {
        Panel {
            Picker("النوع", Units.kinds, kind, { it.name }) { kind = it }
            NumField("القيمة", v, { v = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Picker("من", kind.units, from, { it.name }) { from = it } }
                IconButton(onClick = { val x = from; from = to; to = x }) { Icon(Icons.Rounded.SwapHoriz, "تبديل") }
                Box(Modifier.weight(1f)) { Picker("إلى", kind.units, to, { it.name }) { to = it } }
            }
        }
        ResultCard("${fmt(v.num(), 6)} ${from.name} =" to "${fmt(Units.convert(kind, v.num(), from, to), 6)} ${to.name}",
            kind.units.filter { it != from && it != to }.map { it.name to fmt(Units.convert(kind, v.num(), from, it), 6) })
    }
}

@Composable
fun AreaVolume(t: Tool, back: () -> Unit) {
    var shape by remember { mutableStateOf(Units.shapes[0]) }
    val vals = remember(shape) { mutableStateListOf(*Array(shape.fields.size) { "" }) }
    ToolScreen(t, back) {
        Panel {
            Picker("الشكل", Units.shapes, shape, { it.name + if (it.volume) " (حجم)" else " (مساحة)" }) { shape = it }
            shape.fields.forEachIndexed { i, f -> NumField(f, vals.getOrElse(i) { "" }, { vals[i] = it }) }
        }
        if (vals.all { it.num() > 0 }) {
            val r = shape.f(vals.map { it.num() })
            ResultCard((if (shape.volume) "الحجم" else "المساحة") to "${fmt(r, 4)} ${if (shape.volume) "وحدة³" else "وحدة²"}",
                if (shape.volume) listOf("باللتر (إن كانت الوحدة سم)" to fmt(r / 1000, 4)) else emptyList())
        }
    }
}

@Composable
fun CurrencyTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cached = remember { Store.obj("fx") }
    var rates by remember { mutableStateOf(cached.optJSONObject("rates") ?: JSONObject().put("SAR", 1.0).put("USD", 1 / 3.75)) }
    var updated by remember { mutableStateOf(cached.optString("date", "سعر ربط الريال بالدولار 3.75")) }
    var busy by remember { mutableStateOf(false) }
    val names = mapOf("SAR" to "ريال سعودي", "USD" to "دولار أمريكي", "EUR" to "يورو", "GBP" to "جنيه إسترليني", "AED" to "درهم إماراتي", "KWD" to "دينار كويتي",
        "BHD" to "دينار بحريني", "QAR" to "ريال قطري", "OMR" to "ريال عماني", "EGP" to "جنيه مصري", "JOD" to "دينار أردني", "TRY" to "ليرة تركية",
        "INR" to "روبية هندية", "PKR" to "روبية باكستانية", "PHP" to "بيزو فلبيني", "IDR" to "روبية إندونيسية", "CNY" to "يوان صيني", "JPY" to "ين ياباني", "MAD" to "درهم مغربي", "BDT" to "تاكا بنغلاديشي")
    val codes = names.keys.filter { rates.has(it) }
    var from by remember { mutableStateOf("SAR") }; var to by remember { mutableStateOf("USD") }; var v by remember { mutableStateOf("100") }
    ToolScreen(t, back) {
        Panel {
            NumField("المبلغ", v, { v = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Picker("من", codes, from, { "${names[it]} ($it)" }) { from = it } }
                IconButton(onClick = { val x = from; from = to; to = x }) { Icon(Icons.Rounded.SwapHoriz, "تبديل") }
                Box(Modifier.weight(1f)) { Picker("إلى", codes, to, { "${names[it]} ($it)" }) { to = it } }
            }
        }
        val r = v.num() / rates.optDouble(from, 1.0) * rates.optDouble(to, 1.0)
        ResultCard("${fmt(v.num())} $from =" to "${fmt(r, 4)} $to", listOf("آخر تحديث" to updated))
        PrimaryButton(if (busy) "جارٍ التحديث…" else "تحديث الأسعار من الإنترنت", Icons.Rounded.Refresh, enabled = !busy) {
            busy = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { JSONObject(URL("https://open.er-api.com/v6/latest/SAR").readText()) }
                }.onSuccess { j ->
                    if (j.optString("result") == "success") {
                        rates = j.getJSONObject("rates"); updated = j.optString("time_last_update_utc").take(16)
                        Store.saveObj("fx", JSONObject().put("rates", rates).put("date", updated)); Store.used(t.id)
                    }
                }.onFailure { toast(ctx, "تعذّر الاتصال. تُستخدم آخر أسعار محفوظة.") }
                busy = false
            }
        }
        Hint("يتصل بالإنترنت فقط عند الضغط على التحديث (مصدر الأسعار: ExchangeRate-API المجانية). الأسعار استرشادية وليست أسعار تنفيذ البنوك.")
    }
}
