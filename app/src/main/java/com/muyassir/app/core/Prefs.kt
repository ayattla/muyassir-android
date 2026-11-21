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
    val hidden = mutableStateListOf<String>()      // home sections the user switched off
    val searches = mutableStateListOf<String>()    // recent search terms
    private var loaded = false

    val sections = listOf("quick" to "الأدوات السريعة", "cats" to "التصنيفات", "favs" to "المفضلة", "most" to "الأكثر استخدامًا",
        "rec" to "مقترحة لك", "recent" to "آخر العمليات", "files" to "الملفات الناتجة")

    fun load(ctx: Context) {
        if (loaded) return; loaded = true; Store.init(ctx)
        val o = Store.obj("settings")
        theme = o.optString("theme", "light"); name = o.optString("name", "")
        hidden.clear(); o.optJSONArray("hidden")?.let { a -> (0 until a.length()).forEach { hidden.add(a.optString(it)) } }
        searches.clear(); o.optJSONArray("searches")?.let { a -> (0 until a.length()).forEach { searches.add(a.optString(it)) } }
    }
    private fun save() = Store.saveObj("settings", org.json.JSONObject().put("theme", theme).put("name", name)
        .put("hidden", JSONArray(hidden.toList())).put("searches", JSONArray(searches.toList())))

    fun chooseTheme(v: String) { theme = v; save() }
    fun changeName(v: String) { name = v.trim().take(24); save() }
    fun toggleSection(id: String) { if (id in hidden) hidden.remove(id) else hidden.add(id); save() }
    fun shows(id: String) = id !in hidden
    fun addSearch(q: String) {
        val v = q.trim(); if (v.length < 2) return
        searches.remove(v); searches.add(0, v); while (searches.size > 8) searches.removeAt(searches.lastIndex); save()
    }
    fun clearSearches() { searches.clear(); save() }
}
