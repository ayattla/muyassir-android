@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.muyassir.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Plus
import com.muyassir.app.core.Recipes
import com.muyassir.app.core.Tool

/** A recipe card: title, the chain of steps, and a run button. */
@Composable
fun RecipeCard(r: Recipes.Recipe, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().cardSurface(Radius.m).pressable(onClick).padding(Space.m).testTag("recipe-${r.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tokens.accentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoMode, null, tint = tokens.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.s))
            Text(r.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
            if (r.custom) Text("وصفتي", style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary)
        }
        Text(r.sub, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2)
    }
}

@Composable
fun RecipesScreen(open: (String) -> Unit, back: () -> Unit) {
    LaunchedEffect(Unit) { Recipes.load() }
    var building by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("الوصفات", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.l).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Text("سلسلة أدوات تعمل بلمسة واحدة. اختر وصفة ثم ملفاتك.", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Recipes.builtIn.forEach { r -> RecipeCard(r) { open(r.id) } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Text("وصفاتي", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary); Spacer(Modifier.width(6.dp)); PlusBadge()
            }
            if (Recipes.custom.isEmpty()) Text("اصنع وصفة تناسب عملك المتكرر، مثل: صور الفواتير ← تحسين ← PDF ← ضغط.", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Recipes.custom.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecipeCard(r, Modifier.weight(1f)) { open(r.id) }
                    IconButton(onClick = { Recipes.delete(r.id) }) { Icon(Icons.Rounded.Delete, "حذف الوصفة", tint = tokens.textTertiary) }
                }
            }
            SecondaryButton("وصفة جديدة", Icons.Rounded.Add, Modifier.fillMaxWidth().testTag("newRecipe")) { if (Plus.allow(Plus.Reason.Recipe)) building = true }
        }
    }
    if (building) RecipeBuilder { building = false }
}

@Composable
fun RecipeBuilder(close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("image") }
    val steps = remember { mutableStateListOf<Recipes.Step>() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("وصفة جديدة", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                TextField2("اسم الوصفة", name, { name = it })
                Text("الملفات التي تبدأ بها", style = MaterialTheme.typography.labelLarge)
                ChipRow { Recipes.inputs.forEach { (k, v) -> Chip(v, input == k) { input = k } } }
                Text("الخطوات بالترتيب", style = MaterialTheme.typography.labelLarge)
                if (steps.isEmpty()) Text("أضف خطوة من الأسفل", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. " + Recipes.describe(listOf(s)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Rounded.Close, "إزالة الخطوة") }
                    }
                }
                ChipRow { Recipes.ops.forEach { (k, v) -> Chip("+ $v") { steps.add(Recipes.Step(k, Recipes.defaultArg[k] ?: 0)) } } }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && steps.isNotEmpty(), onClick = {
                Recipes.save(Recipes.Recipe("c" + System.currentTimeMillis(), name.trim().take(40), "", input, steps.toList(), custom = true)); close()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

/** Runs one recipe through the standard file-tool screen (picker, progress, results, Plus limits). */
@Composable
fun RecipeRun(id: String, back: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Recipes.load() }
    val r = Recipes.byId(id) ?: run { LaunchedEffect(Unit) { back() }; return }
    val tool: Tool = remember(r) { Recipes.asTool(r) }
    FileTool(tool, back, Recipes.mimes(r), true, "اختر ${Recipes.inputs[r.input]}", "تشغيل الوصفة", options = { ins ->
        Panel("الخطوات") {
            r.steps.forEachIndexed { i, s -> Text("${i + 1}. " + Recipes.describe(listOf(s)), style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary) }
            Hint("${ins.size} ملف. تعمل كل الخطوات على جهازك، وتُحتسب الوصفة عملية واحدة.")
        }
    }) { ins, out, p -> Recipes.run(ctx, r, ins, out, p) }
}
