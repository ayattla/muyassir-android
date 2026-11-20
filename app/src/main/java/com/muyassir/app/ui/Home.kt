package com.muyassir.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
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
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize().testTag("homeGrid"),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header() }
            item(span = { GridItemSpan(maxLineSpan) }) { SearchBox(q) { q = it } }
            if (q.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(if (results.isEmpty()) "لا توجد أداة بهذا الاسم" else "${results.size} نتيجة") }
                items(results, key = { "s" + it.id }) { t -> ToolTile(t) { open(t.id) } }
                return@LazyVerticalGrid
            }
            item(span = { GridItemSpan(maxLineSpan) }) { QuickActions(open) }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("التصنيفات") }
            items(Tools.categories, key = { "c" + it.id }) { c -> CategoryCard(c.title, c.blurb, Tools.inCat(c.id).size, c.icon, c.color) { openCat(c.id) } }
            val favs = Store.favorites.mapNotNull { Tools.byId[it] }
            if (favs.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("المفضلة") }
                items(favs, key = { "f" + it.id }) { t -> ToolTile(t, compact = true) { open(t.id) } }
            }
            val most = Store.usage.entries.sortedByDescending { it.value }.mapNotNull { Tools.byId[it.key] }.take(6)
            if (most.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("الأكثر استخدامًا") }
                items(most, key = { "m" + it.id }) { t -> ToolTile(t, compact = true) { open(t.id) } }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("مقترحة لك") }
            items(Tools.suggestions(), key = { "g" + it.id }) { t -> ToolTile(t) { open(t.id) } }
            if (Store.recents.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("آخر العمليات", Modifier.weight(1f))
                        TextButton(onClick = { Store.clearRecents() }) { Text("مسح") }
                    }
                }
                items(Store.recents.take(8), key = { "r" + it.tool + it.time }) { r ->
                    val t = Tools.byId[r.tool] ?: return@items
                    RecentRow(t.title, r.detail, r.time, t.icon, Tools.colorOf(t)) { open(t.id) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Footer() }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("مُيسّر", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Text("${Tools.all.size} أداة تعمل على جهازك", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF3E5BFF), Brand.LapisDeep))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color.White)
            Box(Modifier.align(Alignment.TopStart).padding(7.dp).size(9.dp).clip(RoundedCornerShape(50)).background(Brand.Saffron))
        }
    }
}

@Composable
private fun SearchBox(q: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = q, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text("ابحث: دمج PDF، ضريبة، إزالة خلفية…") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "مسح") } },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun QuickActions(open: (String) -> Unit) {
    val quick = listOf("scan", "pdf_merge", "ocr", "img_bg", "qr_read", "vat")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(quick) { id ->
            val t = Tools.byId[id]!!
            val c = Tools.colorOf(t)
            Column(
                Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).clickable { open(id) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(c), contentAlignment = Alignment.Center) {
                    Icon(t.icon, null, tint = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun CategoryCard(title: String, blurb: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(color), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text("$count", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentRow(title: String, detail: String, time: Long, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
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
    Text(
        "ملفاتك تُعالَج على جهازك. لا حسابات ولا إعلانات. الملفات المؤقتة تُحذف تلقائيًا بعد 24 ساعة.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(id: String, open: (String) -> Unit, back: () -> Unit) {
    val c = Tools.category(id)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(c.title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                GridCells.Adaptive(300.dp), Modifier.widthIn(max = 1100.dp).fillMaxSize().testTag("catGrid"), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text(c.blurb, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(Tools.inCat(id), key = { it.id }) { t -> ToolTile(t) { open(t.id) } }
            }
        }
    }
}
