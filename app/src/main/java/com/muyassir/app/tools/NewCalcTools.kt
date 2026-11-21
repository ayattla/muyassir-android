package com.muyassir.app.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.Calc
import com.muyassir.app.engine.Hijri
import com.muyassir.app.ui.*

@Composable
fun ZakatTool(t: Tool, back: () -> Unit) {
    var cash by remember { mutableStateOf("") }; var gold by remember { mutableStateOf("") }; var silver by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf("") }; var recv by remember { mutableStateOf("") }; var debts by remember { mutableStateOf("") }
    var basis by remember { mutableIntStateOf(0) }; var price by remember { mutableStateOf("") }
    ToolScreen(t, back) {
        Panel("أموالك التي حال عليها الحول") {
            NumField("النقد والأرصدة البنكية", cash, { cash = it }, suffix = "ريال")
            NumField("قيمة الذهب المُدّخر", gold, { gold = it }, suffix = "ريال")
            NumField("قيمة الفضة", silver, { silver = it }, suffix = "ريال")
            NumField("عروض التجارة بسعر اليوم", trade, { trade = it }, suffix = "ريال")
            NumField("الديون المرجوّ سدادها لك", recv, { recv = it }, suffix = "ريال")
            NumField("الديون الحالّة عليك", debts, { debts = it }, suffix = "ريال")
        }
        Panel("النصاب") {
            Segmented(listOf("بالذهب (85 غرامًا)", "بالفضة (595 غرامًا)"), basis) { basis = it }
            NumField(if (basis == 0) "سعر غرام الذهب عيار 24 اليوم" else "سعر غرام الفضة اليوم", price, { price = it }, suffix = "ريال")
        }
        val nisab = price.num() * if (basis == 0) 85 else 595
        val total = cash.num() + gold.num() + silver.num() + trade.num() + recv.num()
        if (total > 0 && nisab > 0) {
            val z = Calc.zakat(cash.num(), gold.num(), silver.num(), trade.num(), recv.num(), debts.num(), nisab)
            LaunchedEffect(z) { Store.used(t.id) }
            ResultCard(
                (if (z.reached) "الزكاة الواجبة" else "لم يبلغ المال النصاب") to money(z.due),
                listOf("المال الزكوي" to money(z.wealth), "النصاب" to money(z.nisab), "النسبة" to "2.5%"),
            )
        }
        Hint("حساب تقريبي للحالات الشائعة. الأموال الخاصة (الأسهم، المزارع، الأنعام، الحلي المستعمل) لها أحكام تفصيلية؛ راجع هيئة الزكاة والضريبة والجمارك أو من تثق بعلمه.")
    }
}

@Composable
fun OvertimeTool(t: Tool, back: () -> Unit) {
    var basic by remember { mutableStateOf("") }; var allow by remember { mutableStateOf("") }
    var daily by remember { mutableStateOf("8") }; var hours by remember { mutableStateOf("") }
    ToolScreen(t, back) {
        Panel {
            NumField("الراتب الأساسي", basic, { basic = it }, suffix = "ريال")
            NumField("البدلات الثابتة (سكن، نقل…)", allow, { allow = it }, suffix = "ريال")
            NumField("ساعات العمل اليومية", daily, { daily = it }, suffix = "ساعة")
            NumField("عدد الساعات الإضافية", hours, { hours = it }, suffix = "ساعة")
        }
        if (basic.num() > 0 && hours.num() > 0) {
            val o = Calc.overtime(basic.num(), allow.num(), daily.num(), hours.num())
            LaunchedEffect(o) { Store.used(t.id) }
            ResultCard("أجر الساعات الإضافية" to money(o.total), listOf(
                "أجر الساعة الفعلي" to money(o.hourly), "50% من أجر الساعة الأساسي" to money(o.basicHourly * 0.5), "أجر الساعة الإضافية" to money(o.perHour),
            ))
        }
        Hint("وفق المادة 107 من نظام العمل: أجر الساعة مضافًا إليه 50% من الأجر الأساسي. أجر الساعة = الأجر الشهري ÷ 30 ÷ ساعات العمل اليومية. يجوز باتفاق الطرفين احتساب أيام راحة تعويضية بدل الأجر.")
    }
}

@Composable
fun ElectricityTool(t: Tool, back: () -> Unit) {
    var kwh by remember { mutableStateOf("") }; var fixed by remember { mutableStateOf("") }
    // The residential tariff changes on 1 January 2027 (18/30 → 21/33 halalas per kWh).
    var period by remember { mutableIntStateOf(if (System.currentTimeMillis() >= 1798750800000L) 1 else 0) }
    ToolScreen(t, back) {
        Panel {
            Segmented(listOf("حتى 31 ديسمبر 2026", "من 1 يناير 2027"), period) { period = it }
            NumField("الاستهلاك الشهري", kwh, { kwh = it }, suffix = "ك.و.س")
            NumField("الرسوم الثابتة في فاتورتك (اختياري)", fixed, { fixed = it }, suffix = "ريال")
        }
        val (r1, r2) = if (period == 0) 18.0 to 30.0 else 21.0 to 33.0
        if (kwh.num() > 0) {
            val p = Calc.electricity(kwh.num(), r1, r2, fixed.num())
            LaunchedEffect(p) { Store.used(t.id) }
            ResultCard("الفاتورة التقديرية" to money(p.total), listOf(
                "حتى 6,000 ك.و.س (${fmt(r1, 0)} هللة)" to money(p.tier1), "ما زاد عن 6,000 (${fmt(r2, 0)} هللة)" to money(p.tier2),
                "الرسوم الثابتة" to money(p.fixed), "ضريبة القيمة المضافة 15%" to money(p.vat),
            ))
        }
        Hint("للقطاع السكني. ابتداءً من 1 يناير 2027 تصبح التعرفة 21 هللة حتى 6,000 ك.و.س و33 هللة لما زاد، ويُستبدل رسم الخدمة برسم ثابت حسب سعة القاطع؛ أدخل الرسم الظاهر في فاتورتك. التقدير لا يغني عن فاتورة الشركة السعودية للكهرباء.")
    }
}

@Composable
fun DateDiffTool(t: Tool, back: () -> Unit) {
    var from by remember { mutableLongStateOf(System.currentTimeMillis() - 365L * 86_400_000L * 25) }
    var to by remember { mutableLongStateOf(System.currentTimeMillis()) }
    ToolScreen(t, back) {
        Panel {
            DateField("من تاريخ (مثل تاريخ الميلاد)", from, false) { from = it }
            DateField("إلى تاريخ", to, false) { to = it }
        }
        val s = Calc.span(from, to)
        LaunchedEffect(s) { Store.used(t.id) }
        val hf = Hijri.fromMillis(from); val ht = Hijri.fromMillis(to)
        ResultCard("المدة" to "${s.years} سنة و${s.months} شهر و${s.days} يوم", listOf(
            "إجمالي الأيام" to "%,d".format(s.totalDays), "بالأسابيع" to "%,d أسبوع و%d يوم".format(s.totalDays / 7, s.totalDays % 7),
            "من (هجري)" to "${hf.d} ${Hijri.months[hf.m - 1]} ${hf.y} هـ", "إلى (هجري)" to "${ht.d} ${Hijri.months[ht.m - 1]} ${ht.y} هـ",
        ))
        Hint("الهجري وفق تقويم أم القرى.")
    }
}
