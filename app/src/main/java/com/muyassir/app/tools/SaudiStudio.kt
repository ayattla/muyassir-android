package com.muyassir.app.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.engine.SaudiRules
import com.muyassir.app.engine.SaudiRules.Contract
import com.muyassir.app.engine.SaudiRules.Reason
import com.muyassir.app.ui.*

/** How the result was calculated: formula, article, official source, last review date, and the estimate warning. */
@Composable
fun RulePanel(vararg ids: String) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val rules = ids.mapNotNull { SaudiRules.rules[it] }
    Panel(modifier = Modifier.testTag("rulePanel")) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("طريقة الحساب والمصدر الرسمي", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { open = !open }) { Text(if (open) "إخفاء" else "عرض") }
        }
        if (open) rules.forEach { r ->
            Text(r.title, style = MaterialTheme.typography.labelLarge)
            Text(r.formula, style = MaterialTheme.typography.bodySmall)
            Text("${r.articles} • آخر مراجعة ${r.reviewed}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
            TextButton(onClick = { openUrl(ctx, r.sourceUrl) }) { Text(r.source) }
        }
        Text("النتيجة تقديرية للحالات الشائعة ولا تُعد فتوى نظامية؛ العقد والأحكام القضائية واللوائح قد تغيّرها. راجع الجهة المختصة قبل الاعتماد عليها.", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
    }
}

@Composable
private fun DatePair(start: Long, end: Long, onStart: (Long) -> Unit, onEnd: (Long) -> Unit) {
    DateField("تاريخ المباشرة", start, false, onStart)
    DateField("تاريخ انتهاء العلاقة", end, false, onEnd)
}

@Composable
fun ServiceTool(t: Tool, back: () -> Unit) {
    var start by remember { mutableLongStateOf(0L) }; var end by remember { mutableLongStateOf(System.currentTimeMillis()) }
    ToolScreen(t, back) {
        Panel { DatePair(start, end, { start = it }, { end = it }) }
        if (start > 0) {
            val s = SaudiRules.service(start, end)
            LaunchedEffect(s) { Store.used(t.id) }
            ResultCard("مدة الخدمة" to "${s.years} سنة و${s.months} شهر و${s.days} يوم", listOf("بالأيام" to "%,d يوم".format(s.totalDays), "بالسنوات" to fmt(s.asYears, 2)))
        }
        RulePanel("service")
    }
}

@Composable
fun NoticeTool(t: Tool, back: () -> Unit) {
    var contract by remember { mutableIntStateOf(0) }; var by by remember { mutableIntStateOf(0) }; var monthly by remember { mutableIntStateOf(0) }
    var given by remember { mutableStateOf("0") }; var wage by remember { mutableStateOf("") }
    ToolScreen(t, back) {
        Panel {
            Text("نوع العقد", style = MaterialTheme.typography.labelLarge); Segmented(listOf("غير محدد المدة", "محدد المدة"), contract) { contract = it }
            Text("من أنهى العقد", style = MaterialTheme.typography.labelLarge); Segmented(listOf("صاحب العمل", "العامل"), by) { by = it }
            Text("طريقة دفع الأجر", style = MaterialTheme.typography.labelLarge); Segmented(listOf("شهري", "غير شهري"), monthly) { monthly = it }
            NumField("أيام الإشعار التي أُعطيت", given, { given = it }, suffix = "يوم")
            NumField("الأجر الشهري", wage, { wage = it }, suffix = "ريال")
        }
        if (wage.num() > 0) {
            val n = SaudiRules.notice(if (contract == 0) Contract.OPEN else Contract.FIXED, if (by == 0) SaudiRules.By.EMPLOYER else SaudiRules.By.WORKER, monthly == 0, given.num().toInt(), wage.num())
            LaunchedEffect(n) { Store.used(t.id) }
            if (contract == 1) ResultCard("لا مهلة إشعار في العقد محدد المدة" to money(0.0), emptyList(), "إنهاؤه مبكرًا دون سبب مشروع يرتب تعويض المادة 77.")
            else ResultCard("التعويض عن المهلة" to money(n.amount), listOf("المهلة النظامية" to "${n.requiredDays} يومًا", "الأيام الناقصة" to "${n.missingDays} يومًا", "يدفعه" to if (by == 0) "صاحب العمل للعامل" else "العامل لصاحب العمل"))
        }
        RulePanel("notice", "art77")
    }
}

@Composable
fun SettlementTool(t: Tool, back: () -> Unit) {
    var sector by remember { mutableIntStateOf(0) }
    var start by remember { mutableLongStateOf(0L) }; var end by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var wage by remember { mutableStateOf("") }; var reason by remember { mutableStateOf(Reason.CONTRACT_END) }; var contract by remember { mutableIntStateOf(0) }
    var unpaid by remember { mutableStateOf("0") }; var leave by remember { mutableStateOf("0") }; var given by remember { mutableStateOf("0") }; var remaining by remember { mutableStateOf("0") }
    ToolScreen(t, back) {
        Panel {
            Text("القطاع", style = MaterialTheme.typography.labelLarge); Segmented(listOf("خاص", "حكومي"), sector) { sector = it }
            if (sector == 1) Hint("موظفو الجهات الحكومية يخضعون لنظام الخدمة المدنية وليس نظام العمل؛ هذه الحاسبة للقطاع الخاص.")
        }
        if (sector == 0) {
            Panel {
                Text("نوع العقد", style = MaterialTheme.typography.labelLarge); Segmented(listOf("غير محدد المدة", "محدد المدة"), contract) { contract = it }
                DatePair(start, end, { start = it }, { end = it })
                NumField("آخر أجر شهري (الأساسي + البدلات الثابتة)", wage, { wage = it }, suffix = "ريال")
                Picker("سبب انتهاء العلاقة", Reason.values().toList(), reason, { it.label }) { reason = it }
                NumField("أيام عمل لم يُدفع أجرها", unpaid, { unpaid = it }, suffix = "يوم")
                NumField("رصيد الإجازة غير المستخدم", leave, { leave = it }, suffix = "يوم")
                if (reason == Reason.EMPLOYER_LAWFUL || reason == Reason.EMPLOYER_UNLAWFUL) NumField("أيام الإشعار التي أُعطيت", given, { given = it }, suffix = "يوم")
                if (reason == Reason.EMPLOYER_UNLAWFUL && contract == 1) NumField("الأشهر المتبقية من العقد", remaining, { remaining = it }, suffix = "شهر")
            }
            if (start > 0 && wage.num() > 0) {
                val s = SaudiRules.settlement(wage.num(), start, end, reason, if (contract == 0) Contract.OPEN else Contract.FIXED, unpaid.num(), leave.num(), given.num().toInt(), remaining.num())
                LaunchedEffect(s) { Store.used(t.id) }
                val sp = SaudiRules.service(start, end)
                ResultCard("إجمالي التسوية التقديرية" to money(s.total), listOf("مدة الخدمة" to "${sp.years} سنة و${sp.months} شهر و${sp.days} يوم") + s.lines.map { it.label to money(it.amount) })
                Panel("تفاصيل الحساب") { s.lines.forEach { LabelValue(it.label, it.note) } }
            }
        }
        RulePanel("eos", "leave", "notice", "art77", "service")
    }
}

@Composable
fun EntitlementsTool(t: Tool, back: () -> Unit) {
    var wage by remember { mutableStateOf("") }; var unpaid by remember { mutableStateOf("") }; var leave by remember { mutableStateOf("") }
    var otHours by remember { mutableStateOf("") }; var daily by remember { mutableStateOf("8") }; var basic by remember { mutableStateOf("") }
    ToolScreen(t, back) {
        Panel {
            NumField("الأجر الشهري الكامل", wage, { wage = it }, suffix = "ريال")
            NumField("الراتب الأساسي (للعمل الإضافي)", basic, { basic = it }, suffix = "ريال")
            NumField("أيام عمل لم يُدفع أجرها", unpaid, { unpaid = it }, suffix = "يوم")
            NumField("ساعات عمل إضافي لم تُدفع", otHours, { otHours = it }, suffix = "ساعة")
            NumField("ساعات العمل اليومية", daily, { daily = it }, suffix = "ساعة")
            NumField("رصيد إجازة يُصرف بدله", leave, { leave = it }, suffix = "يوم")
        }
        val w = wage.num()
        if (w > 0) {
            val unpaidAmt = unpaid.num() * w / 30; val leaveAmt = leave.num() * w / 30
            val ot = if (otHours.num() > 0) com.muyassir.app.engine.Calc.overtime(basic.num().takeIf { it > 0 } ?: w, (w - basic.num()).coerceAtLeast(0.0), daily.num().coerceAtLeast(1.0), otHours.num()).total else 0.0
            LaunchedEffect(unpaidAmt, leaveAmt, ot) { Store.used(t.id) }
            ResultCard("المستحقات" to money(unpaidAmt + leaveAmt + ot), listOf("أجر الأيام غير المدفوعة" to money(unpaidAmt), "العمل الإضافي" to money(ot), "بدل الإجازة" to money(leaveAmt)))
        }
        RulePanel("overtime", "leave")
    }
}

@Composable
fun DeductTool(t: Tool, back: () -> Unit) {
    var wage by remember { mutableStateOf("") }; var req by remember { mutableStateOf("") }
    ToolScreen(t, back) {
        Panel { NumField("الأجر المستحق هذا الشهر", wage, { wage = it }, suffix = "ريال"); NumField("المبلغ المطلوب استقطاعه (سلفة، جزاء…)", req, { req = it }, suffix = "ريال") }
        if (wage.num() > 0 && req.num() > 0) {
            val d = SaudiRules.deduction(wage.num(), req.num())
            LaunchedEffect(d) { Store.used(t.id) }
            ResultCard("الحد المسموح استقطاعه" to money(d.allowed), listOf("الحد الأعلى (نصف الأجر)" to money(d.cap), "الباقي للأشهر التالية" to money((d.requested - d.allowed).coerceAtLeast(0.0)), "صافي ما يُصرف" to money(wage.num() - d.allowed)))
        }
        RulePanel("deduct")
    }
}
