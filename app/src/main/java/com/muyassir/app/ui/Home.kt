package com.muyassir.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Category
import com.muyassir.app.core.Files
import com.muyassir.app.core.Prefs
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tools
import java.io.File
import java.util.Calendar

private val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
// Two cards per row on phones, three or four on tablets.
private val half: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(2) }

/** The smart home: brand header, greeting, search, featured tools, categories, then the user's own history. */
@Composable
fun Home(open: (String) -> Unit, openCat: (String) -> Unit, openSearch: () -> Unit, openAssistant: () -> Unit, openActivity: () -> Unit,
         openRecipes: () -> Unit = {}, openRecipe: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val outputs = remember(Store.recents.size) { Files.outputs(ctx).take(4) }
    val wideHome = LocalWide.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (LocalWide.current) 8 else 4),
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize().testTag("homeGrid"),
            contentPadding = PaddingValues(start = Space.l, end = Space.l, bottom = Space.xxxl),
            horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item(span = full) { Header() }
            item(span = full) { Greeting() }
            item(span = full) { SearchEntry(openSearch, openAssistant) }
            if (Prefs.role.isEmpty()) item(span = full) { RoleCard() }

            if (Prefs.shows("quick")) {
                item(span = full) { SectionHead("أدوات سريعة", "أكثر ما يحتاجه الناس يوميا") }
                items(featuredFor(Prefs.role), key = { "q$it" }, span = { half(this) }) { id -> FeaturedCard(id) { open(id) } }
            }
            if (Prefs.shows("recipes")) {
                item(span = full) { SectionHead("الوصفات", "عدة أدوات بلمسة واحدة", "الكل" to openRecipes) }
                items(com.muyassir.app.core.Recipes.builtIn.take(if (wideHome) 4 else 2), key = { "rc" + it.id }, span = { half(this) }) { r -> RecipeCard(r) { openRecipe(r.id) } }
            }
            if (Prefs.shows("cats")) {
                item(span = full) { SectionHead("الأقسام", "${Tools.all.size} أداة في ${Tools.categories.size} أقسام") }
                items(Tools.categories, key = { "c" + it.id }, span = { half(this) }) { c -> CategoryCard(c) { openCat(c.id) } }
            }
            val most = Store.usage.entries.sortedByDescending { it.value }.mapNotNull { Tools.byId[it.key] }.take(4)
            if (Prefs.shows("most") && most.isNotEmpty()) {
                item(span = full) { SectionHead("الأكثر استخداما") }
                items(most, key = { "m" + it.id }, span = { half(this) }) { t -> ToolTile(t, compact = true) { open(t.id) } }
            }
            if (Prefs.shows("rec")) {
                item(span = full) { SectionHead("مقترحة لك", "بناء على آخر ما استخدمته") }
                items(Tools.suggestions().take(4), key = { "g" + it.id }, span = { half(this) }) { t -> ToolTile(t) { open(t.id) } }
            }
            val favs = Store.favorites.mapNotNull { Tools.byId[it] }
            if (Prefs.shows("favs")) {
                item(span = full) { SectionHead("المفضلة") }
                if (favs.isEmpty()) item(span = full) { InlineEmpty(Icons.Rounded.StarBorder, "اضغط النجمة في أي أداة لتظهر هنا") }
                else items(favs.take(6), key = { "f" + it.id }, span = { half(this) }) { t -> ToolTile(t, compact = true) { open(t.id) } }
            }
            if (Prefs.shows("recent") && Store.recents.isNotEmpty()) {
                item(span = full) { SectionHead("آخر العمليات", action = "السجل" to openActivity) }
                items(Store.recents.take(4), key = { "r" + it.tool + it.time }, span = { if (maxLineSpan >= 8) GridItemSpan(4) else full(this) }) { r ->
                    val t = Tools.byId[r.tool] ?: return@items
                    ActivityRow(t.icon, Tools.colorOf(t), t.title, r.detail.ifBlank { t.sub }, ago(r.time), "إعادة") { open(t.id) }
                }
            }
            if (Prefs.shows("files") && outputs.isNotEmpty()) {
                item(span = full) { SectionHead("ملفاتك الناتجة", "تحذف تلقائيا بعد 24 ساعة", "الكل" to openActivity) }
                items(outputs, key = { "o" + it.path }, span = { if (maxLineSpan >= 8) GridItemSpan(4) else full(this) }) { f ->
                    ActivityRow(fileIcon(f), Brand.Indigo, f.name, Files.human(f.length()), ago(f.lastModified()), "فتح") { Files.open(ctx, f) }
                }
            }
            item(span = full) { Footer() }
        }
    }
}

private val featured = listOf("pdf_word", "pdf_merge", "img_compress", "ocr", "scan", "img_convert")

/** Quick tools: the user's role first, then the general favourites. */
private fun featuredFor(role: String) = ((Tools.byRole[role] ?: emptyList()).take(4) + featured).distinct().take(6)

/** First-run question: who are you? It only reorders the home screen; nothing is locked. */
@Composable
private fun RoleCard() {
    Column(Modifier.fillMaxWidth().cardSurface(Radius.l).padding(Space.l).testTag("roleCard"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text("لنرتب ميسر لك", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        Text("اختر ما يصفك لنقدم الأدوات التي تحتاجها أولا. يمكنك تغييره من الإعدادات.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        ChipRow { Prefs.roles.forEach { (id, label) -> Chip(label) { Prefs.chooseRole(id) } } }
        TextButton(onClick = { Prefs.chooseRole("none") }) { Text("تخط") }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
        val dark = tokens.dark
        IconButton(onClick = { Prefs.chooseTheme(if (dark) "light" else "dark") }) {
            Icon(if (tokens.dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, if (tokens.dark) "الوضع الفاتح" else "الوضع الداكن", tint = tokens.textSecondary)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LogoMark(34.dp)
            Spacer(Modifier.width(Space.s))
            Text("ميسر", style = MaterialTheme.typography.headlineSmall, color = tokens.textPrimary)
        }
        Spacer(Modifier.size(48.dp))
    }
}

/** The brand mark: ink tile, the "seen + tick" stroke and the saffron dot. */
@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color(0xFF1C2453)), contentAlignment = Alignment.Center) {
        Icon(androidx.compose.ui.res.painterResource(com.muyassir.app.R.drawable.ic_mark), null, tint = Color.Unspecified, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Greeting() {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = when (h) { in 4..11 -> "صباح الخير"; in 12..16 -> "مساء النور"; else -> "مساء الخير" }
    Column(Modifier.fillMaxWidth().padding(top = Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (Prefs.name.isBlank()) hello else "$hello، ${Prefs.name}", style = MaterialTheme.typography.headlineMedium, color = tokens.textPrimary, textAlign = TextAlign.Center)
        Text("ماذا تريد أن تنجز اليوم؟ كل المعالجة على جهازك.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SearchEntry(openSearch: () -> Unit, openAssistant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).cardSurface(Radius.l).pressable(openSearch).semantics { contentDescription = "بحث" }.padding(start = Space.l, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = tokens.textTertiary)
        Spacer(Modifier.width(Space.m))
        Text("ابحث بالاسم أو الوظيفة: تصغير ملف، دمج…", style = MaterialTheme.typography.bodyMedium, color = tokens.textTertiary, maxLines = 1, modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(Radius.s)).background(tokens.accentSoft).pressable(openAssistant).padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = tokens.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); Text("المساعد", style = MaterialTheme.typography.labelMedium, color = tokens.accent)
            }
        }
    }
}

@Composable
fun SectionHead(title: String, sub: String? = null, action: Pair<String, () -> Unit>? = null) {
    Column(Modifier.fillMaxWidth().padding(top = Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).height(2.dp).clip(CircleShape).background(tokens.border))
            Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary, modifier = Modifier.padding(horizontal = Space.s))
            Box(Modifier.width(18.dp).height(2.dp).clip(CircleShape).background(tokens.border))
        }
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary, textAlign = TextAlign.Center)
        if (action != null) TextButton(onClick = action.second, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(action.first, style = MaterialTheme.typography.labelMedium) }
    }
}

/** Featured tool: bigger card with the tool's own colour, centred content. */
@Composable
private fun FeaturedCard(id: String, onClick: () -> Unit) {
    val t = Tools.byId[id] ?: return
    val base = Tools.colorOf(t)
    Column(
        Modifier.fillMaxWidth().height(132.dp).cardSurface().pressable(onClick).padding(Space.m),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(toneSoft(base)), contentAlignment = Alignment.Center) {
            Icon(t.icon, null, tint = tone(base), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(Space.s))
        Text(t.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, textAlign = TextAlign.Center, maxLines = 1)
        Text(t.sub, style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun CategoryCard(c: Category, onClick: () -> Unit) {
    val n = Tools.inCat(c.id).size
    Column(
        Modifier.fillMaxWidth().height(164.dp).cardSurface().pressable(onClick).padding(Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(tone(c.color)), contentAlignment = Alignment.Center) {
            Icon(c.icon, null, tint = if (tokens.dark) Color(0xFF10131D) else Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(Space.s))
        Text(c.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, textAlign = TextAlign.Center, maxLines = 1)
        Text(c.blurb, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$n أداة", style = MaterialTheme.typography.labelMedium, color = tone(c.color))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = tone(c.color), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ActivityRow(icon: ImageVector, color: Color, title: String, detail: String, time: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().cardSurface(Radius.m).pressable(onClick).padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(toneSoft(color)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tone(color), modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, maxLines = 1)
            Text("$detail • $time", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary, maxLines = 1)
        }
        Text(action, style = MaterialTheme.typography.labelMedium, color = tokens.accent)
    }
}

@Composable
fun InlineEmpty(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).border(1.dp, tokens.border, RoundedCornerShape(Radius.m)).padding(Space.l), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tokens.textTertiary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Space.s))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Footer() {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lock, null, tint = tokens.textTertiary, modifier = Modifier.size(16.dp))
        Text("ملفاتك تعالج على جهازك. لا حسابات ولا إعلانات، والملفات المؤقتة تحذف تلقائيا بعد 24 ساعة.",
            style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary, textAlign = TextAlign.Center)
    }
}

fun fileIcon(f: File): ImageVector = when (f.extension.lowercase()) {
    "pdf" -> Icons.Rounded.PictureAsPdf; "jpg", "jpeg", "png", "webp" -> Icons.Rounded.Image; "zip" -> Icons.Rounded.FolderZip
    "docx" -> Icons.Rounded.Description; "xlsx" -> Icons.Rounded.TableChart; "pptx" -> Icons.Rounded.Slideshow; "wav", "m4a" -> Icons.Rounded.GraphicEq
    else -> Icons.Rounded.InsertDriveFile
}

fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when { m < 1 -> "الآن"; m < 60 -> "قبل $m د"; m < 1440 -> "قبل ${m / 60} س"; else -> "قبل ${m / 1440} يوم" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(id: String, open: (String) -> Unit, back: () -> Unit) {
    val c = Tools.categories.firstOrNull { it.id == id }
    val tools = if (c == null) emptyList() else Tools.inCat(c.id)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(c?.title ?: "قسم", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                GridCells.Adaptive(160.dp), Modifier.widthIn(max = 1100.dp).fillMaxSize().testTag("catGrid"),
                contentPadding = PaddingValues(start = Space.l, end = Space.l, bottom = Space.xxxl),
                horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                if (c != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = Space.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(tone(c.color)), contentAlignment = Alignment.Center) {
                            Icon(c.icon, null, tint = if (tokens.dark) Color(0xFF10131D) else Color.White, modifier = Modifier.size(32.dp))
                        }
                        Text(c.blurb, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary, textAlign = TextAlign.Center)
                        Text("${tools.size} أداة", style = MaterialTheme.typography.labelLarge, color = tone(c.color),
                            modifier = Modifier.clip(CircleShape).background(toneSoft(c.color)).padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                }
                if (tools.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.Category, "لا توجد أدوات هنا", "ارجع إلى الرئيسية واختر قسما آخر.") }
                items(tools, key = { it.id }) { t -> ToolTile(t) { open(t.id) } }
            }
        }
    }
}
