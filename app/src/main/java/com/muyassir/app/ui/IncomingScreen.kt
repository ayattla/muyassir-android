@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.muyassir.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.Incoming
import com.muyassir.app.core.Recipes
import com.muyassir.app.core.Tools

/** «فتح في ميسر»: what to do with files shared from another app. */
@Composable
fun IncomingScreen(openTool: (String) -> Unit, openRecipe: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Recipes.load() }
    val files = Incoming.files.toList()
    val kinds = files.map { Incoming.kind(it) }.toSet()
    val tools = Incoming.toolsFor(kinds).mapNotNull { Tools.byId[it] }
    val recipes = Recipes.forKinds(kinds)
    fun handTo(go: () -> Unit) { Incoming.handTo(files); go() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ماذا تريد أن تفعل؟", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.l).widthIn(max = 720.dp).testTag("incoming"),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Panel("${files.size} ${if (files.size == 1) "ملف" else "ملفات"} وصلت إلى ميسر") {
                files.take(8).forEach { u ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)), verticalAlignment = Alignment.CenterVertically) {
                        Thumb(u); Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(u.lastPathSegment ?: "ملف", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, maxLines = 1)
                            Text(Incoming.kindLabel[Incoming.kind(u)] + " • " + Files.human(java.io.File(u.path ?: "").length()), style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
                        }
                    }
                }
                if (files.size > 8) Text("و${files.size - 8} ملفات أخرى", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
                Text("نسخت إلى جهازك فقط ولم ترفع إلى أي مكان.", style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (recipes.isNotEmpty()) {
                Text("وصفات بلمسة واحدة", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                recipes.forEach { r -> RecipeCard(r) { handTo { openRecipe(r.id) } } }
            }
            Text("الأدوات المناسبة", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            tools.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    row.forEach { t -> ToolTile(t, Modifier.weight(1f), compact = true) { handTo { openTool(t.id) } } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
