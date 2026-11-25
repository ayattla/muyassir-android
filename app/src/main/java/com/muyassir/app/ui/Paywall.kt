package com.muyassir.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyassir.app.core.Billing
import com.muyassir.app.core.Plus

const val PRIVACY_URL = "https://github.com/ayattla/muyassir-android/blob/main/docs/PRIVACY.md"
const val TERMS_URL = "https://github.com/ayattla/muyassir-android/blob/main/docs/TERMS.md"

fun Context.activity(): Activity? { var c: Context? = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }
fun openUrl(ctx: Context, url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

/** Small «بلس» label shown next to an option that needs the subscription. */
@Composable
fun PlusBadge(modifier: Modifier = Modifier) {
    if (Plus.unlocked) return
    Row(modifier.clip(RoundedCornerShape(50)).background(Brand.Saffron.copy(alpha = 0.22f)).padding(horizontal = 7.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.WorkspacePremium, null, tint = Color(0xFF8A5A00), modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp))
        Text("بلس", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B4600))
    }
}

/** Shown on top of everything when [Plus.paywall] is set. */
@Composable
fun PaywallHost() {
    val r = Plus.paywall ?: return
    Dialog(onDismissRequest = { Plus.paywall = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Paywall(r) { Plus.paywall = null } }
        }
    }
}

private data class Benefit(val icon: ImageVector, val title: String, val text: String)

private val benefits = listOf(
    Benefit(Icons.Rounded.AllInclusive, "بلا حدود يومية", "كل أدوات الملفات والصور متى شئت"),
    Benefit(Icons.Rounded.BurstMode, "دفعة واحدة حتى ${Plus.PLUS_FILES} ملف", "وملفات حتى ${Plus.PLUS_MB} م.ب"),
    Benefit(Icons.Rounded.Style, "قوالب احترافية", "أكثر من 20 قالبا بشعار منشأتك"),
    Benefit(Icons.Rounded.TextFields, "OCR لملفات كاملة", "وملف PDF قابل للبحث"),
)

private val extras = listOf(
    "إزالة الخلفية بدقة عالية وألوان مخصصة", "كل مقاسات صور الوثائق وورقة طباعة", "توقيع محفوظ وأختام وعلامة مائية",
    "تصدير السجلات إلى Excel وPDF", "قفل الملاحظات والوثائق بالبصمة", "ألوان وأيقونات مميزة", "أولوية في الدعم",
)

@Composable
fun Paywall(reason: Plus.Reason, close: () -> Unit) {
    val ctx = LocalContext.current
    var pick by remember { mutableStateOf("yearly") }
    val plans = Billing.plans
    LaunchedEffect(Billing.message) { Billing.message?.let { toast(ctx, it); Billing.message = null } }
    LaunchedEffect(Plus.active) { if (Plus.active && reason != Plus.Reason.Menu) close() }

    Column(Modifier.fillMaxSize().systemBarsPadding().testTag("paywall")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, modifier = Modifier.testTag("paywallClose")) { Icon(Icons.Rounded.Close, "إغلاق", tint = tokens.textSecondary) }
            Spacer(Modifier.weight(1f))
            if (Plus.live && !Plus.active) TextButton(onClick = { Billing.restore() }, enabled = !Billing.busy) { Text("استعادة المشتريات") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.xl).widthIn(max = 560.dp).align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brand.Ink), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = Brand.Saffron, modifier = Modifier.size(34.dp))
            }
            Text("ميسر بلس", style = MaterialTheme.typography.labelLarge, color = tokens.accent)
            Text(if (Plus.active) "أنت مشترك في بلس" else "أنجز أكثر بلا حدود", style = MaterialTheme.typography.headlineSmall, color = tokens.textPrimary, textAlign = TextAlign.Center)
            if (reason != Plus.Reason.Menu && !Plus.active) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(tokens.accentSoft).padding(Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(reason.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, textAlign = TextAlign.Center)
                    Text(reason.body, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center)
                }
            }
            benefits.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    row.forEach { b ->
                        Column(Modifier.weight(1f).cardSurface(Radius.m).padding(Space.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(b.icon, null, tint = tokens.accent, modifier = Modifier.size(24.dp))
                            Text(b.title, style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary, textAlign = TextAlign.Center)
                            Text(b.text, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                extras.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Check, null, tint = Brand.Mint, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(e, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    }
                }
            }

            when {
                Plus.school.isNotEmpty() && Plus.schoolUntil > System.currentTimeMillis() -> {
                    Text("بلس مفعل ضمن باقة ${Plus.school} حتى ${java.text.SimpleDateFormat("d/M/yyyy", java.util.Locale.US).format(java.util.Date(Plus.schoolUntil))}",
                        style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.testTag("schoolActive"))
                }
                Plus.active -> {
                    Text(
                        if (Plus.subscribed) "${Plus.planName}${if (Plus.renews) " • يتجدد تلقائيا" else " • لن يتجدد"}" else "مرور اليوم مفعل حتى ${java.text.SimpleDateFormat("h:mm a", java.util.Locale("ar")).format(java.util.Date(Plus.dayPassUntil))}",
                        style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, textAlign = TextAlign.Center,
                    )
                    if (Plus.subscribed) SecondaryButton("إدارة الاشتراك أو إلغاؤه", Icons.Rounded.ManageAccounts, Modifier.fillMaxWidth()) { openUrl(ctx, Billing.MANAGE_URL) }
                }
                !Plus.live -> {
                    Column(Modifier.fillMaxWidth().cardSurface(Radius.m).padding(Space.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text("قريبا", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                        Text("كل الأدوات مفتوحة لك مجانا الآن خلال فترة الإطلاق. سيطرح بلس قريبا بسعر رمزي.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center)
                        SwitchRow("نبهني عند إطلاق بلس", Plus.notifyWhenLive) { Plus.toggleNotify() }
                    }
                }
                plans.isEmpty() -> Text("تعذر تحميل الأسعار من Google Play. تأكد من الاتصال وحاول لاحقا.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center)
                else -> {
                    val monthly = plans.firstOrNull { it.id == "monthly" }
                    plans.filter { it.id != "day" }.forEach { p ->
                        val save = if (p.id == "yearly" && monthly != null && monthly.micros > 0) (100 - p.micros * 100 / (monthly.micros * 12)).toInt() else 0
                        PlanCard(p, pick == p.id, save) { pick = p.id }
                    }
                    val chosen = plans.firstOrNull { it.id == pick } ?: plans.first()
                    PrimaryButton(
                        if (chosen.trialDays > 0) "ابدأ تجربة مجانية ${chosen.trialDays} أيام" else if (chosen.id == "day") "افتح بلس 24 ساعة" else "اشترك الآن",
                        Icons.Rounded.WorkspacePremium, modifier = Modifier.fillMaxWidth().testTag("subscribe"),
                    ) { ctx.activity()?.let { Billing.buy(it, chosen) } }
                    Text(
                        when {
                            chosen.id == "day" -> "دفعة واحدة ${chosen.price}، بلا اشتراك ولا تجديد."
                            chosen.trialDays > 0 -> "مجانا ${chosen.trialDays} أيام، ثم ${chosen.price} كل ${chosen.period} ويتجدد تلقائيا. نذكرك قبل نهاية التجربة بيوم، ويمكنك الإلغاء في أي وقت من Google Play."
                            else -> "${chosen.price} كل ${chosen.period}، ويتجدد تلقائيا حتى تلغيه من Google Play."
                        },
                        style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary, textAlign = TextAlign.Center,
                    )
                    plans.firstOrNull { it.id == "day" }?.let { d ->
                        TextButton(onClick = { pick = "day" }) { Text("تحتاجه مرة واحدة؟ مرور يوم بـ ${d.price}", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            if (!Plus.active) SchoolCodeButton()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = tokens.textTertiary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                Text("ملفاتك تبقى على جهازك. ألغ في أي وقت من المتجر.", style = MaterialTheme.typography.labelMedium, color = tokens.textTertiary)
            }
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { openUrl(ctx, TERMS_URL) }) { Text("شروط الاستخدام") }
                TextButton(onClick = { openUrl(ctx, PRIVACY_URL) }) { Text("سياسة الخصوصية") }
            }
            Spacer(Modifier.height(Space.l))
        }
    }
}

@Composable
private fun PlanCard(p: Billing.Plan, selected: Boolean, save: Int, onClick: () -> Unit) {
    val border = if (selected) tokens.accent else tokens.border
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(if (selected) tokens.accentSoft else tokens.surface)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(Radius.l)).pressable(onClick).padding(Space.l)
            .semantics { role = Role.RadioButton; this.selected = selected }.testTag("plan-${p.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, border, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(tokens.accent))
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                if (save > 0) { Spacer(Modifier.width(6.dp)); Text("وفر $save%", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Mint).padding(horizontal = 7.dp, vertical = 2.dp)) }
            }
            Text(if (p.trialDays > 0) "${p.trialDays} أيام مجانا • ${p.note}" else p.note, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Text("${p.price}\n/ ${p.period}", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, textAlign = TextAlign.End)
    }
}


/** «لديك كود مدرسة؟»: a teacher enters the code the school gave them, and Plus opens until the school licence ends. */
@Composable
fun SchoolCodeButton() {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = { open = true; error = null }, modifier = Modifier.testTag("schoolCode")) { Text("لديك كود من مدرستك؟", style = MaterialTheme.typography.labelLarge) }
    if (open) AlertDialog(
        onDismissRequest = { if (!busy) open = false },
        title = { Text("تفعيل باقة المدرسة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("اكتب الكود الذي وصلك من إدارة مدرستك. يرسل الكود فقط إلى خادم ميسر للتحقق منه، ويربط بهذا الجهاز.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                OutlinedTextField(code, { code = it.uppercase().take(20); error = null }, singleLine = true, placeholder = { Text("MSR-XXXX-XXXX") },
                    modifier = Modifier.fillMaxWidth().testTag("schoolCodeField"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("schoolCodeError")) }
            }
        },
        confirmButton = {
            TextButton(enabled = code.count { it.isLetterOrDigit() } >= 8 && !busy, modifier = Modifier.testTag("redeemCode"), onClick = {
                busy = true
                scope.launch {
                    runCatching { com.muyassir.app.core.Cloud.redeem(code) }
                        .onSuccess { if (Plus.schoolUntil > System.currentTimeMillis()) open = false else error = "تعذر تفعيل الكود" }
                        .onFailure { error = it.message }
                    busy = false
                }
            }) { Text(if (busy) "جار التحقق…" else "تفعيل") }
        },
        dismissButton = { TextButton(onClick = { open = false }, enabled = !busy) { Text("إلغاء") } },
    )
}
