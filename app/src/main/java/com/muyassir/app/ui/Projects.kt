package com.muyassir.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Files
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tools
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** «مشاريعي»: named groups of kept result files and the tools used, on the device, to come back to any earlier work. */
object Projects {
    var version by mutableIntStateOf(0)
    fun all(): List<JSONObject> = Store.list("projects")
    private fun save(l: List<JSONObject>) { Store.saveList("projects", l); version++ }
    fun create(name: String): JSONObject { val p = JSONObject().put("id", System.currentTimeMillis()).put("name", name.trim()).put("files", JSONArray()).put("tools", JSONArray()); save(all() + p); return p }
    fun remove(id: Long) = save(all().filter { it.optLong("id") != id })
    fun files(p: JSONObject): List<File> = (0 until (p.optJSONArray("files")?.length() ?: 0)).map { File(p.getJSONArray("files").getString(it)) }.filter { it.exists() }
    fun tools(p: JSONObject): List<String> = (0 until (p.optJSONArray("tools")?.length() ?: 0)).map { p.getJSONArray("tools").getString(it) }
    /** Keeps the file (so it is not auto-deleted after 24 h) and records it, with the tool that made it if known. */
    fun add(ctx: android.content.Context, id: Long, f: File, tool: String? = null) {
        val kept = if (Files.isKept(ctx, f)) f else Files.keep(ctx, f)
        save(all().map { p ->
            if (p.optLong("id") != id) p else {
                val fs = files(p).map { it.absolutePath }.toMutableList(); if (kept.absolutePath !in fs) fs += kept.absolutePath
                val ts = tools(p).toMutableList(); if (tool != null && tool !in ts) ts += tool
                JSONObject(p.toString()).put("files", JSONArray(fs)).put("tools", JSONArray(ts)).put("updated", System.currentTimeMillis())
            }
        })
    }
    fun detach(id: Long, f: File) = save(all().map { p -> if (p.optLong("id") != id) p else JSONObject(p.toString()).put("files", JSONArray(files(p).map { it.absolutePath }.filter { it != f.absolutePath })) })
}

/** Picks (or creates) a project for a file. */
@Composable
fun AddToProjectDialog(file: File, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    val list = remember(Projects.version) { Projects.all() }
    AlertDialog(onDismissRequest = onDone, title = { Text("أضف «${file.name}» إلى مشروع") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { p -> TextButton(onClick = { Projects.add(ctx, p.optLong("id"), file); onDone() }, modifier = Modifier.fillMaxWidth()) { Text(p.optString("name")) } }
            TextField2("أو مشروع جديد", name, { name = it }, Modifier.testTag("newProject"))
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { val p = Projects.create(name); Projects.add(ctx, p.optLong("id"), file); onDone() }, modifier = Modifier.testTag("createProject")) { Text("إنشاء وإضافة") } },
        dismissButton = { TextButton(onClick = onDone) { Text("إلغاء") } })
}

fun LazyListScope.projectsTab(open: (String) -> Unit, opened: Long?, onOpen: (Long?) -> Unit, onNew: () -> Unit) {
    item(key = "newProjectBtn") { SecondaryButton("مشروع جديد", Icons.Rounded.CreateNewFolder, Modifier.fillMaxWidth().testTag("projectNew")) { onNew() } }
    val list = Projects.all()
    if (list.isEmpty()) item { EmptyState(Icons.Rounded.FolderSpecial, "لا توجد مشاريع بعد", "اجمع ملفات عمل واحد (تقرير، اختبار، معاملة) في مشروع لتعود إليه وتكمله. أضف أي ملف من تبويب «الملفات».") }
    items(list, key = { "p" + it.optLong("id") }) { p ->
        val id = p.optLong("id"); val fs = Projects.files(p); val ctx = LocalContext.current
        Column(Modifier.fillMaxWidth().cardSurface(Radius.m).padding(Space.m), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, tint = tokens.accent); Spacer(Modifier.width(Space.s))
                Column(Modifier.weight(1f)) {
                    Text(p.optString("name"), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    Text("${fs.size} ملف", style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
                }
                TextButton(onClick = { onOpen(if (opened == id) null else id) }) { Text(if (opened == id) "إغلاق" else "فتح") }
            }
            if (opened == id) {
                fs.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        IconButton(onClick = { Files.open(ctx, f) }) { Icon(Icons.Rounded.OpenInNew, "فتح", tint = tokens.textSecondary) }
                        IconButton(onClick = { Files.share(ctx, listOf(f)) }) { Icon(Icons.Rounded.Share, "مشاركة", tint = tokens.textSecondary) }
                        IconButton(onClick = { Projects.detach(id, f) }) { Icon(Icons.Rounded.RemoveCircleOutline, "إزالة من المشروع", tint = tokens.textSecondary) }
                    }
                }
                val ts = Projects.tools(p).mapNotNull { Tools.byId[it] }
                if (ts.isNotEmpty()) ChipRow { ts.forEach { t -> Chip(t.title) { open(t.id) } } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fs.isNotEmpty()) SecondaryButton("مشاركة الكل", Icons.Rounded.Share, Modifier.weight(1f)) { Files.share(ctx, fs) }
                    SecondaryButton("حذف المشروع", Icons.Rounded.DeleteOutline, Modifier.weight(1f)) { Projects.remove(id); onOpen(null) }
                }
            }
        }
    }
}
