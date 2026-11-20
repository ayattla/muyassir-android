package com.muyassir.app.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.Calc
import com.muyassir.app.engine.Hijri
import com.muyassir.app.ui.*

@Composable
fun VatTool(t: Tool, back: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    var rate by remember { mutableStateOf("15") }
    ToolScreen(t, back) {
        Panel {
            Segmented(listOf("إضافة الضريبة", "استخراج من مبلغ شامل"), mode) { mode = it }
            NumField(if (mode == 0) "المبلغ قبل الضريبة" else "المبلغ شامل الضريبة", amount, { amount = it }, suffix = "ريال")
            NumField("نسبة الضريبة", rate, { rate = it }, suffix = "%")
        }
        if (amount.num() > 0) {
            val r = Calc.vat(amount.num(), rate.num(), mode == 1)
            LaunchedEffect(r) { Store.used(t.id) }
            ResultCard("الإجمالي شامل الضريبة" to money(r.total), listOf("قبل الضريبة" to money(r.net), "قيمة الضريبة (${fmt(rate.num())}%)" to money(r.vat)))
        }
        Hint("النسبة الأساسية لضريبة القيمة المضافة في المملكة 15% وفق هيئة الزكاة والضريبة والجمارك.")
    }
}

@Composable
fun SalaryTool(t: Tool, back: () -> Unit) {
    var basic by remember { mutableStateOf("") }; var housing by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("") }; var other by remember { mutableStateOf("") }; var ded by remember { mutableStateOf("") }
    var saudi by remember { mutableIntStateOf(0) }; var sys by remember { mutableIntStateOf(0) }
    ToolScreen(t, back) {
        Panel {
            Segmented(listOf("سعودي", "غير سعودي"), saudi) { saudi = it }
            if (saudi == 0) { Text("نظام التأمينات", style = MaterialTheme.typography.labelLarge); Segmented(listOf("مسجل قبل 3 يوليو 2024", "النظام الجديد"), sys) { sys = it } }
            NumField("الراتب الأساسي", basic, { basic = it }, suffix = "ريال")
            NumField("بدل السكن", housing, { housing = it }, suffix = "ريال")
            NumField("بدل النقل", transport, { transport = it }, suffix = "ريال")
            NumField("بدلات أخرى", other, { other = it }, suffix = "ريال")
            NumField("استقطاعات أخرى (سلف، غياب…)", ded, { ded = it }, suffix = "ريال")
        }
        if (basic.num() > 0) {
            val sy = if (sys == 0) Calc.GosiSystem.LEGACY else Calc.GosiSystem.NEW
            val s = Calc.salary(basic.num(), housing.num(), transport.num(), other.num(), saudi == 0, sy, ded.num())
            val g = Calc.gosi(basic.num(), housing.num(), saudi == 0, sy)
            ResultCard("صافي الراتب الشهري" to money(s.net), listOf(
                "إجمالي الراتب" to money(s.gross),
                "خصم التأمينات (${fmt(g.employeeRate)}%)" to money(s.gosi),
                "الأجر الخاضع للاشتراك" to money(g.base),
                "حصة صاحب العمل (${fmt(g.employerRate)}%)" to money(g.employer),
                "صافي سنوي" to money(s.net * 12),
            ))
        }
        Hint("الأجر الخاضع = الأساسي + السكن بحد أقصى 45,000 ريال. للسعودي في النظام السابق: 9% معاشات + 0.75% ساند على الموظف، ويضيف صاحب العمل 2% أخطار مهنية. في النظام الجديد ترتفع نسبة المعاشات 0.5% كل يوليو (9.5% في 2026). تحقق من تطبيق «تأميناتي» لمعرفة نظامك.")
    }
}

@Composable
fun EosTool(t: Tool, back: () -> Unit) {
    var wage by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }; var m by remember { mutableStateOf("") }; var d by remember { mutableStateOf("") }
    var how by remember { mutableIntStateOf(0) }
    ToolScreen(t, back) {
        Panel {
            NumField("آخر أجر شهري (الأساسي + البدلات الثابتة)", wage, { wage = it }, suffix = "ريال")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("سنوات", y, { y = it }, Modifier.weight(1f)); NumField("أشهر", m, { m = it }, Modifier.weight(1f)); NumField("أيام", d, { d = it }, Modifier.weight(1f))
            }
            Segmented(listOf("انتهاء العقد أو إنهاء من صاحب العمل", "استقالة"), how) { how = it }
        }
        if (wage.num() > 0 && (y.num() + m.num() + d.num()) > 0) {
            val r = Calc.endOfService(wage.num(), y.num().toInt(), m.num().toInt(), d.num().toInt(), if (how == 0) Calc.Leaving.TERMINATION else Calc.Leaving.RESIGNATION)
            ResultCard("المكافأة المستحقة" to money(r.payable), listOf(
                "مدة الخدمة" to "${fmt(r.years)} سنة", "المكافأة الكاملة" to money(r.fullAward),
                "نسبة الاستحقاق" to when (r.share) { 1.0 -> "كاملة"; 0.0 -> "لا تستحق"; else -> "${fmt(r.share * 100, 0)}%" },
            ))
        }
        Hint("المادة 84: نصف أجر شهر عن كل سنة من السنوات الخمس الأولى، وأجر شهر عن كل سنة بعدها. المادة 85 للاستقالة: لا شيء قبل سنتين، الثلث من 2 إلى 5 سنوات، الثلثان من 5 إلى 10، والمكافأة كاملة بعد 10 سنوات. النتيجة تقديرية.")
    }
}

@Composable
fun LeaveTool(t: Tool, back: () -> Unit) {
    var years by remember { mutableStateOf("") }; var months by remember { mutableStateOf("12") }
    var used by remember { mutableStateOf("0") }; var wage by remember { mutableStateOf("") }
    ToolScreen(t, back) {
        Panel {
            NumField("سنوات الخدمة", years, { years = it })
            NumField("الأشهر المنقضية من سنة الاستحقاق الحالية", months, { months = it })
            NumField("الأيام المستخدمة", used, { used = it }, suffix = "يوم")
            NumField("الأجر الشهري (لحساب قيمة الرصيد)", wage, { wage = it }, suffix = "ريال")
        }
        if (years.isNotBlank()) {
            val r = Calc.leave(years.num(), months.num(), used.num(), wage.num())
            ResultCard("الرصيد المتبقي" to "${fmt(r.remaining, 1)} يوم", listOf(
                "الاستحقاق السنوي" to "${r.entitlement} يومًا", "المستحق حتى الآن" to "${fmt(r.accrued, 1)} يوم",
                "قيمة الرصيد" to money(r.value),
            ))
        }
        Hint("المادة 109 من نظام العمل: 21 يومًا سنويًا، تزيد إلى 30 يومًا بعد خمس سنوات متصلة لدى صاحب العمل.")
    }
}

@Composable
fun FinanceTool(t: Tool, back: () -> Unit) {
    var amount by remember { mutableStateOf("") }; var rate by remember { mutableStateOf("") }; var months by remember { mutableStateOf("60") }
    var type by remember { mutableIntStateOf(0) }; var down by remember { mutableStateOf("0") }
    ToolScreen(t, back) {
        Panel {
            Segmented(listOf("نسبة ثابتة (Flat)", "متناقصة"), type) { type = it }
            NumField("مبلغ التمويل أو سعر السلعة", amount, { amount = it }, suffix = "ريال")
            NumField("الدفعة الأولى", down, { down = it }, suffix = "ريال")
            NumField(if (type == 0) "نسبة الربح السنوية الثابتة" else "النسبة السنوية", rate, { rate = it }, suffix = "%")
            NumField("المدة", months, { months = it }, suffix = "شهر")
        }
        val p = amount.num() - down.num(); val n = months.num().toInt()
        if (p > 0 && n > 0) {
            val r = if (type == 0) Calc.flatLoan(p, rate.num(), n) else Calc.reducingLoan(p, rate.num(), n)
            ResultCard("القسط الشهري" to money(r.monthly), listOf(
                "مبلغ التمويل" to money(p), "إجمالي الأرباح" to money(r.profit), "إجمالي المدفوع" to money(r.totalPaid + down.num()),
                "النسبة السنوية الفعلية (APR)" to "${fmt(r.apr)}%",
            ))
        }
        Hint("العروض بنسبة ثابتة تبدو أقل من حقيقتها؛ قارن العروض بالنسبة السنوية الفعلية (APR) التي يلزم البنك المركزي السعودي بالإفصاح عنها.")
    }
}

@Composable
fun DiscountTool(t: Tool, back: () -> Unit) {
    var price by remember { mutableStateOf("") }; var d1 by remember { mutableStateOf("") }; var d2 by remember { mutableStateOf("") }
    var vat by remember { mutableStateOf(false) }
    ToolScreen(t, back) {
        Panel {
            NumField("السعر الأصلي", price, { price = it }, suffix = "ريال")
            NumField("نسبة الخصم", d1, { d1 = it }, suffix = "%")
            NumField("خصم إضافي (اختياري)", d2, { d2 = it }, suffix = "%")
            SwitchRow("إضافة ضريبة 15% بعد الخصم", vat) { vat = it }
        }
        if (price.num() > 0) {
            val r = Calc.discount(price.num(), d1.num(), d2.num())
            val final = if (vat) r.final * 1.15 else r.final
            ResultCard("السعر بعد الخصم" to money(final), listOf("التوفير" to money(r.saving), "الخصم الفعلي" to "${fmt(r.effectivePct)}%") +
                if (vat) listOf("قبل الضريبة" to money(r.final)) else emptyList(), note = if (d2.num() > 0) "خصمان متتاليان لا يُجمعان: ${fmt(d1.num())}% ثم ${fmt(d2.num())}% = ${fmt(r.effectivePct)}%" else null)
        }
    }
}

@Composable
fun HijriTool(t: Tool, back: () -> Unit) {
    var dir by remember { mutableIntStateOf(0) }
    val today = remember { java.util.Calendar.getInstance() }
    var y by remember { mutableStateOf(today.get(java.util.Calendar.YEAR).toString()) }
    var m by remember { mutableStateOf((today.get(java.util.Calendar.MONTH) + 1).toString()) }
    var d by remember { mutableStateOf(today.get(java.util.Calendar.DAY_OF_MONTH).toString()) }
    ToolScreen(t, back) {
        val h = remember { Hijri.today() }
        ResultCard("اليوم" to "${h.d} ${Hijri.months[h.m - 1]} ${h.y} هـ", emptyList())
        Panel {
            Segmented(listOf("ميلادي ← هجري", "هجري ← ميلادي"), dir) {
                dir = it
                if (it == 1) { y = h.y.toString(); m = h.m.toString(); d = h.d.toString() } else { y = today.get(java.util.Calendar.YEAR).toString(); m = (today.get(java.util.Calendar.MONTH) + 1).toString(); d = today.get(java.util.Calendar.DAY_OF_MONTH).toString() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("اليوم", d, { d = it }, Modifier.weight(1f)); NumField("الشهر", m, { m = it }, Modifier.weight(1f)); NumField("السنة", y, { y = it }, Modifier.weight(1.3f))
            }
        }
        val res = runCatching {
            val yy = y.num().toInt(); val mm = m.num().toInt(); val dd = d.num().toInt()
            require(mm in 1..12 && dd in 1..31)
            if (dir == 0) Hijri.toHijri(yy, mm, dd).let { r -> "${Hijri.days[r.weekday - 1]} ${r.d} ${Hijri.months[r.m - 1]} ${r.y} هـ" }
            else Hijri.toGregorian(yy, mm, dd).let { r -> "${Hijri.days[r.weekday - 1]} ${r.d} ${Hijri.gMonths[r.m - 1]} ${r.y} م" }
        }.getOrNull()
        res?.let { ResultCard("النتيجة" to it, emptyList()) }
        Hint("وفق تقويم أم القرى المعتمد في المملكة. بدايات الأشهر الشرعية تُعلن رسميًا بالرؤية وقد تختلف بيوم.")
    }
}

private data class Link(val name: String, val desc: String, val url: String)

@Composable
fun GovDirectory(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val groups = listOf(
        "الهوية والخدمات العامة" to listOf(
            Link("المنصة الوطنية GOV.SA", "دليل جميع الخدمات الحكومية", "https://my.gov.sa"),
            Link("أبشر", "الجوازات والأحوال المدنية والمرور", "https://www.absher.sa"),
            Link("النفاذ الوطني الموحد", "الدخول الموحد للمنصات", "https://www.iam.gov.sa"),
            Link("توكلنا", "الوثائق الرقمية والخدمات", "https://ta.sdaia.gov.sa"),
        ),
        "العمل والتأمينات" to listOf(
            Link("قوى", "عقود العمل وخدمات القطاع الخاص", "https://www.qiwa.sa"),
            Link("التأمينات الاجتماعية", "الاشتراكات والمعاشات وساند", "https://www.gosi.gov.sa"),
            Link("مساند", "العمالة المنزلية", "https://www.musaned.com.sa"),
            Link("وزارة الموارد البشرية", "خدمات العمل والتنمية الاجتماعية", "https://www.hrsd.gov.sa"),
        ),
        "المال والتجارة" to listOf(
            Link("هيئة الزكاة والضريبة والجمارك", "الضريبة والفوترة الإلكترونية", "https://zatca.gov.sa"),
            Link("المركز السعودي للأعمال", "السجلات والتراخيص", "https://business.sa"),
            Link("وزارة التجارة", "البلاغات وحماية المستهلك", "https://mc.gov.sa"),
            Link("حساب المواطن", "الدعم والاستحقاق", "https://www.ca.gov.sa"),
        ),
        "الصحة والتعليم والعدل" to listOf(
            Link("وزارة الصحة", "الخدمات الصحية وتطبيق صحتي", "https://www.moh.gov.sa"),
            Link("مدرستي", "منصة التعليم", "https://schools.madrasati.sa"),
            Link("ناجز", "الخدمات العدلية", "https://najiz.sa"),
            Link("إيجار", "عقود الإيجار", "https://www.ejar.sa"),
        ),
    )
    ToolScreen(t, back) {
        Hint("روابط مباشرة للمواقع الرسمية. مُيسّر لا ينفذ معاملات حكومية ولا يطلب بيانات دخولك.")
        groups.forEach { (g, links) ->
            Panel(g) {
                links.forEach { l ->
                    Row(Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url))) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(l.name, style = MaterialTheme.typography.titleSmall); Text(l.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Icon(Icons.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyTool(t: Tool, back: () -> Unit) {
    val ctx = LocalContext.current
    val nums = listOf(
        "911" to "الطوارئ الموحد (الرياض، مكة المكرمة، المدينة المنورة، الشرقية)", "999" to "الشرطة", "997" to "الإسعاف — الهلال الأحمر",
        "998" to "الدفاع المدني", "993" to "حوادث المرور", "996" to "أمن الطرق", "112" to "طوارئ دون شريحة",
        "937" to "وزارة الصحة — استشارات", "933" to "طوارئ الكهرباء", "994" to "حرس الحدود", "995" to "مكافحة المخدرات (بلاغات سرية)", "920000560" to "نجم — حوادث المركبات المؤمّنة",
    )
    ToolScreen(t, back) {
        nums.forEach { (n, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(n, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.width(130.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Call, "اتصال", tint = Brand.Mint)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Hint("المصدر: قوائم الطوارئ في المنصة الوطنية وشركات الاتصالات. يفتح الزر لوحة الاتصال ولا يتصل تلقائيًا.")
    }
}
