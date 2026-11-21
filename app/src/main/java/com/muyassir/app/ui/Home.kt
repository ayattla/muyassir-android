package com.muyassir.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tools
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Home(open: (String) -> Unit, openCat: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val results = remember(q) { Tools.search(q) }
    // Phones get two category tiles per row; tool rows take the full width (half on tablets).
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    val row: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(if (maxLineSpan >= 4) maxLineSpan / 2 else maxLineSpan) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize().testTag("homeGrid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = full) { Hero(q) { q = it } }
            if (q.isNotBlank()) {
                item(span = full) { SectionTitle(if (results.isEmpty()) "لا توجد أداة بهذا الاسم" else "${results.size} نتيجة") }
                if (results.isEmpty()) item(span = full) { EmptyState(Icons.Rounded.SearchOff, "لم نجد «$q»", "جرّب كلمة أقصر مثل: PDF، صورة، راتب، QR") }
                items(results, key = { "s" + it.id }, span = { row() }) { t -> ToolTile(t) { open(t.id) } }
                return@LazyVerticalGrid
            }
            item(span = full) { QuickActions(open) }
            item(span = full) { SectionTitle("التصنيفات") }
            items(Tools.categories, key = { "c" + it.id }) { c -> CategoryCard(c.title, c.blurb, Tools.inCat(c.id).size, c.icon, c.color) { openCat(c.id) } }
            val favs = Store.favorites.mapNotNull { Tools.byId[it] }
            if (favs.isNotEmpty()) {
                item(span = full) { SectionTitle("المفضلة") }
                items(favs, key = { "f" + it.id }, span = { row() }) { t -> ToolTile(t, compact = true) { open(t.id) } }
            }
            val most = Store.usage.entries.sortedByDescending { it.value }.mapNotNull { Tools.byId[it.key] }.take(4)
            if (most.isNotEmpty()) {
                item(span = full) { SectionTitle("الأكثر استخدامًا") }
                items(most, key = { "m" + it.id }, span = { row() }) { t -> ToolTile(t, compact = true) { open(t.id) } }
            }
            item(span = full) { SectionTitle("مقترحة لك") }
            items(Tools.suggestions(), key = { "g" + it.id }, span = { row() }) { t -> ToolTile(t) { open(t.id) } }
            if (Store.recents.isNotEmpty()) {
                item(span = full) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("آخر العمليات", Modifier.weight(1f))
                        TextButton(onClick = { Store.clearRecents() }) { Text("مسح السجل") }
                    }
                }
                items(Store.recents.take(6), key = { "r" + it.tool + it.time }, span = { row() }) { r ->
                    val t = Tools.byId[r.tool] ?: return@items
                    RecentRow(t.title, r.detail, r.time, t.icon, Tools.colorOf(t)) { open(t.id) }
                }
            }
            item(span = full) { Footer() }
        }
    }
}

/** Lapis band with the name, the promise and the search field: the one bold element of the home screen. */
@Composable
private fun Hero(q: String, onChange: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp, topStart = 28.dp, topEnd = 28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B57FF), Color(0xFF1E2FC4))))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("مُيسّر", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("${Tools.all.size} أداة تعمل على جهازك، بلا رفع ولا اشتراك", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
            }
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Color.White)
                Box(Modifier.align(Alignment.TopStart).padding(7.dp).size(8.dp).clip(CircleShape).background(Brand.Saffron))
            }
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Brand.Muted)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("ابحث عن أداة", style = MaterialTheme.typography.bodyLarge, color = Brand.Muted)
                BasicTextField(
                    value = q, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "بحث" },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Brand.Ink), cursorBrush = SolidColor(Brand.Lapis),
                )
            }
            if (q.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "مسح البحث", tint = Brand.Muted) }
        }
    }
}

@Composable
private fun QuickActions(open: (String) -> Unit) {
    val quick = listOf("pdf_merge", "pdf_compress", "ocr", "img_compress", "img_bg", "scan", "vat", "qr_read")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("اختصارات سريعة")
        quick.chunked(4).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { id ->
                    val t = Tools.byId[id]!!; val c = Tools.colorOf(t)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { open(id) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(c.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                            Icon(t.icon, null, tint = c, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(t.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, minLines = 2, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 12.dp, bottom = 2.dp), color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun CategoryCard(title: String, blurb: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.weight(1f))
            Text("$count أداة", style = MaterialTheme.typography.labelMedium, color = color,
                modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
private fun RecentRow(title: String, detail: String, time: Long, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(SimpleDateFormat("d/M HH:mm", Locale.US).format(Date(time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Footer() {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("ملفاتك تُعالَج على جهازك. لا حسابات ولا إعلانات، والملفات المؤقتة تُحذف تلقائيًا بعد 24 ساعة.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(id: String, open: (String) -> Unit, back: () -> Unit) {
    val c = Tools.categories.firstOrNull { it.id == id }
    val tools = if (c == null) emptyList() else Tools.inCat(c.id)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(c?.title ?: "تصنيف", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                GridCells.Adaptive(320.dp), Modifier.widthIn(max = 1100.dp).fillMaxSize().testTag("catGrid"), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (c != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.color.copy(alpha = 0.12f)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(c.color), contentAlignment = Alignment.Center) { Icon(c.icon, null, tint = Color.White) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${tools.size} أداة", style = MaterialTheme.typography.titleMedium, color = c.color)
                            Text(c.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (tools.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.Category, "لا توجد أدوات هنا", "ارجع إلى الرئيسية واختر تصنيفًا آخر.") }
                items(tools, key = { it.id }) { t -> ToolTile(t) { open(t.id) } }
            }
        }
    }
}
