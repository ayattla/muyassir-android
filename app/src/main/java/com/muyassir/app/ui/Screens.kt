package com.muyassir.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Assistant
import com.muyassir.app.core.Files
import com.muyassir.app.core.Prefs
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tools
import kotlinx.coroutines.delay

/** Page frame for the top-level tabs: centred title and subtitle, content limited to 760dp. */
@Composable
private fun TabPage(title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = Space.l)) {
            Column(Modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = tokens.textPrimary)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center)
            }
            content()
        }
    }
}

@Composable
private fun BigField(value: String, onChange: (String) -> Unit, hint: String, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, focus: FocusRequester? = null, onDone: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).cardSurface(Radius.l).padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tokens.textTertiary)
        Spacer(Modifier.width(Space.m))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = tokens.textTertiary, maxLines = 2)
            BasicTextField(
                value, onChange, modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier).semantics { contentDescription = desc },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.textPrimary), cursorBrush = SolidColor(tokens.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onDone() }, onDone = { onDone() }),
            )
        }
        if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "مسح", tint = tokens.textTertiary) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(open: (String) -> Unit, openAssistant: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
    val results = remember(q) { Tools.search(q) }
    val grouped = remember(results) { Tools.categories.mapNotNull { c -> results.filter { it.cats.first() == c.id }.takeIf { it.isNotEmpty() }?.let { c to it } } }
    val openAndRemember: (String) -> Unit = { id -> Prefs.addSearch(q); open(id) }
    TabPage("البحث", "اكتب اسم الأداة أو ما تريد فعله، بالعربية أو الإنجليزية") {
        BigField(q, { q = it }, "مثال: تصغير ملف، صور إلى PDF، راتب", Icons.Rounded.Search, "بحث", focus) { Prefs.addSearch(q) }
        Spacer(Modifier.height(Space.m))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.s), contentPadding = PaddingValues(bottom = Space.xxxl)) {
            if (q.isBlank()) {
                if (Prefs.searches.isNotEmpty()) {
                    item { Row(verticalAlignment = Alignment.CenterVertically) { Text("عمليات بحث سابقة", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f)); TextButton(onClick = { Prefs.clearSearches() }) { Text("مسح") } } }
                    item { FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) { Prefs.searches.forEach { s -> Chip(s) { q = s } } } }
                }
                item { Text("جرّب", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.padding(top = Space.s)) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) { listOf("تصغير ملف", "صور إلى PDF", "دمج", "إزالة الخلفية", "استخراج نص", "راتب", "ضريبة", "QR", "compress", "merge").forEach { s -> Chip(s) { q = s } } } }
                item { AssistantPromo(openAssistant) }
            } else if (results.isEmpty()) {
                item { EmptyState(Icons.Rounded.SearchOff, "لا توجد أداة بهذا الوصف", "جرّب كلمة أخرى، أو صف ما تريد للمساعد ليقترح عليك.") }
                item { AssistantPromo(openAssistant) }
            } else {
                item { Text("${results.size} نتيجة", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                grouped.forEach { (c, list) ->
                    item(key = "h" + c.id) {
                        Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(tone(c.color))); Spacer(Modifier.width(Space.s))
                            Text(c.title, style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                        }
                    }
                    items(list, key = { "s" + it.id }) { t -> ToolTile(t, compact = true) { openAndRemember(t.id) } }
                }
            }
        }
    }
}

@Composable
private fun AssistantPromo(openAssistant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.m).clip(RoundedCornerShape(Radius.l)).background(tokens.accentSoft).pressable(openAssistant).padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = tokens.accent)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text("لا تعرف أي أداة تحتاج؟", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
            Text("صف ما تريد إنجازه وسيقترح المساعد الأدوات والخطوات.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(open: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var asked by rememberSaveable { mutableStateOf("") }
    val plan = remember(asked) { Assistant.plan(asked) }
    TabPage("المساعد", "صف ما تريد إنجازه، وسأرشدك إلى الأدوات المناسبة بالترتيب") {
        BigField(q, { q = it }, "مثال: أريد تحويل خمس صور إلى PDF وتقليل حجمه", Icons.Rounded.AutoAwesome, "اكتب طلبك") { asked = q }
        Spacer(Modifier.height(Space.s))
        PrimaryButton("اقترح الأدوات", Icons.Rounded.Lightbulb, enabled = q.isNotBlank()) { asked = q }
        Spacer(Modifier.height(Space.m))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.s), contentPadding = PaddingValues(bottom = Space.xxxl)) {
            if (asked.isBlank()) {
                item { Text("أمثلة", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                item {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        listOf("حوّل صوري إلى PDF وصغّر حجمه", "أريد نص هذه الصورة", "صورة شخصية بمقاس الجواز", "ادمج ملفات PDF واحمها بكلمة مرور", "كم صافي راتبي؟", "صوّر أوراقًا ووقّعها")
                            .forEach { s -> Chip(s) { q = s; asked = s } }
                    }
                }
                item { Hint("المساعد يعمل على جهازك دون إنترنت، ويقترح فقط: أنت من يفتح الأداة ويختار ملفاتك وينفّذ.") }
            } else {
                plan.note?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
                itemsIndexed(plan.steps, key = { _, s -> s.tool.id }) { i, s ->
                    AnimatedVisibility(true, enter = fadeIn() + slideInVertically { it / 4 }) {
                        Row(Modifier.fillMaxWidth().cardSurface().padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(tokens.accent), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = tokens.onAccent)
                            }
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f)) {
                                Text(s.tool.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                Text(s.why, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            }
                            FilledTonalButton(onClick = { open(s.tool.id) }, shape = RoundedCornerShape(Radius.s)) { Text("افتح") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityScreen(open: (String) -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val files = remember(refresh, tab) { Files.outputs(ctx) }
    TabPage("نشاطي", "عملياتك وملفاتك الناتجة ومفضلتك، محفوظة على جهازك فقط") {
        Segmented(listOf("العمليات", "الملفات", "المفضلة"), tab) { tab = it }
        Spacer(Modifier.height(Space.m))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.s), contentPadding = PaddingValues(bottom = Space.xxxl)) {
            when (tab) {
                0 -> if (Store.recents.isEmpty()) item { EmptyState(Icons.Rounded.History, "لا توجد عمليات بعد", "كل ما تنجزه في الأدوات يظهر هنا لتعيده بلمسة.") }
                else {
                    items(Store.recents, key = { "r" + it.tool + it.time }) { r ->
                        val t = Tools.byId[r.tool] ?: return@items
                        ActivityRow(t.icon, Tools.colorOf(t), t.title, r.detail.ifBlank { t.sub }, ago(r.time), "إعادة") { open(t.id) }
                    }
                    item { TextButton(onClick = { Store.clearRecents() }, modifier = Modifier.fillMaxWidth()) { Text("مسح السجل") } }
                }
                1 -> if (files.isEmpty()) item { EmptyState(Icons.Rounded.FolderOpen, "لا توجد ملفات ناتجة", "نتائج التحويل والضغط تبقى هنا 24 ساعة ثم تُحذف تلقائيًا.") }
                else {
                    items(files, key = { it.path }) { f ->
                        Row(Modifier.fillMaxWidth().cardSurface(Radius.m).padding(start = Space.m, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(fileIcon(f), null, tint = tokens.accent)
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, maxLines = 1)
                                Text("${Files.human(f.length())} • ${ago(f.lastModified())}", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
                            }
                            IconButton(onClick = { Files.open(ctx, f) }) { Icon(Icons.Rounded.OpenInNew, "فتح", tint = tokens.textSecondary) }
                            IconButton(onClick = { Files.share(ctx, listOf(f)) }) { Icon(Icons.Rounded.Share, "مشاركة", tint = tokens.textSecondary) }
                            IconButton(onClick = { f.delete(); refresh++ }) { Icon(Icons.Rounded.DeleteOutline, "حذف", tint = tokens.textSecondary) }
                        }
                    }
                }
                else -> {
                    val favs = Store.favorites.mapNotNull { Tools.byId[it] }
                    if (favs.isEmpty()) item { EmptyState(Icons.Rounded.StarBorder, "لا توجد مفضلة", "اضغط النجمة أعلى أي أداة لتضيفها هنا وفي الرئيسية.") }
                    else items(favs, key = { "f" + it.id }) { t -> ToolTile(t, compact = true) { open(t.id) } }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(Prefs.name) }
    var cleared by remember { mutableStateOf<String?>(null) }
    TabPage("الإعدادات", "مظهر التطبيق والصفحة الرئيسية والخصوصية") {
        LazyColumn(Modifier.fillMaxSize().testTag("settings"), verticalArrangement = Arrangement.spacedBy(Space.m), contentPadding = PaddingValues(bottom = Space.xxxl)) {
            item {
                Panel("المظهر") {
                    Segmented(listOf("فاتح", "داكن", "حسب الجهاز"), when (Prefs.theme) { "dark" -> 1; "system" -> 2; else -> 0 }) { Prefs.chooseTheme(listOf("light", "dark", "system")[it]) }
                }
            }
            item {
                Panel("الترحيب") {
                    TextField2("اسمك (اختياري) يظهر في التحية", name, { name = it; Prefs.changeName(it) })
                }
            }
            item {
                Panel("أقسام الصفحة الرئيسية") {
                    Prefs.sections.forEach { (id, label) -> SwitchRow(label, Prefs.shows(id)) { Prefs.toggleSection(id) } }
                }
            }
            item {
                Panel("الخصوصية والتخزين") {
                    Text("كل المعالجة على جهازك، ولا تُرفع ملفاتك إلى أي خادم. النتائج تُحذف تلقائيًا بعد 24 ساعة.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("حذف الملفات المؤقتة والنتائج الآن", Icons.Rounded.DeleteSweep, Modifier.fillMaxWidth()) { Files.clearAll(ctx); cleared = "حُذفت الملفات المؤقتة والنتائج" }
                    SecondaryButton("مسح سجل العمليات والبحث", Icons.Rounded.History, Modifier.fillMaxWidth()) { Store.clearRecents(); Prefs.clearSearches(); cleared = "مُسح السجل" }
                    cleared?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Brand.Mint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = Space.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LogoMark(44.dp)
                    Text("مُيسّر", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
                    Text("الإصدار $v • ${Tools.all.size} أداة", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
                }
            }
        }
    }
}
