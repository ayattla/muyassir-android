package com.muyassir.app.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/** User preferences (theme, home sections, search history), saved on the device. */
object Prefs {
    var theme by mutableStateOf("light") // light | dark | system
        private set
    var name by mutableStateOf("")
        private set
    /** Plus: app accent colour index (0 = default indigo) and launcher icon. */
    var accent by mutableStateOf(0)
        private set
    var icon by mutableStateOf("Default")
        private set
    /** student | teacher | employee | business, or "" before the first-run question (skip = "none"). */
    var role by mutableStateOf("")
        private set
    var successes by mutableStateOf(0)
        private set
    val hidden = mutableStateListOf<String>()      // home sections the user switched off
    val searches = mutableStateListOf<String>()    // recent search terms
    private var loaded = false

    val sections = listOf("quick" to "الأدوات السريعة", "recipes" to "الوصفات", "cats" to "التصنيفات", "favs" to "المفضلة", "most" to "الأكثر استخدامًا",
        "rec" to "مقترحة لك", "recent" to "آخر العمليات", "files" to "الملفات الناتجة")

    fun load(ctx: Context) {
        if (loaded) return; loaded = true; Store.init(ctx)
        val o = Store.obj("settings")
        theme = o.optString("theme", "light"); name = o.optString("name", ""); accent = o.optInt("accent", 0); icon = o.optString("icon", "Default"); role = o.optString("role", ""); successes = o.optInt("ok", 0)
        hidden.clear(); o.optJSONArray("hidden")?.let { a -> (0 until a.length()).forEach { hidden.add(a.optString(it)) } }
        searches.clear(); o.optJSONArray("searches")?.let { a -> (0 until a.length()).forEach { searches.add(a.optString(it)) } }
    }
    private fun save() = Store.saveObj("settings", org.json.JSONObject().put("theme", theme).put("name", name).put("accent", accent).put("icon", icon).put("role", role).put("ok", successes)
        .put("hidden", JSONArray(hidden.toList())).put("searches", JSONArray(searches.toList())))

    fun chooseTheme(v: String) { theme = v; save() }
    val roles = listOf("student" to "طالب", "teacher" to "معلم", "employee" to "موظف", "business" to "صاحب منشأة")
    fun chooseRole(v: String) { role = v; save() }
    /** Counts successful operations; returns true exactly once, on the third, to ask for a store rating. */
    fun success(): Boolean { successes++; save(); return successes == 3 }

    fun chooseAccent(v: Int) { accent = v; save() }

    val icons = listOf("Default" to "الأصلية", "Paper" to "فاتحة", "Saffron" to "زعفرانية")

    /** Switch the launcher icon by enabling exactly one activity-alias. The launcher may take a moment to refresh. */
    fun chooseIcon(ctx: Context, v: String) {
        val pm = ctx.packageManager
        icons.forEach { (id, _) ->
            pm.setComponentEnabledSetting(android.content.ComponentName(ctx, "com.muyassir.app.Icon$id"),
                if (id == v) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP)
        }
        icon = v; save()
    }

    fun changeName(v: String) { name = v.trim().take(24); save() }
    fun toggleSection(id: String) { if (id in hidden) hidden.remove(id) else hidden.add(id); save() }
    fun shows(id: String) = id !in hidden
    fun addSearch(q: String) {
        val v = q.trim(); if (v.length < 2) return
        searches.remove(v); searches.add(0, v); while (searches.size > 8) searches.removeAt(searches.lastIndex); save()
    }
    fun clearSearches() { searches.clear(); save() }
}
