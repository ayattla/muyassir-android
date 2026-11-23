package com.muyassir.app.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muyassir.app.engine.Cities
import com.muyassir.app.engine.PayDays
import org.json.JSONArray
import org.json.JSONObject

/** What «يومي» shows. Everything stays on the device (Store obj "daily"). */
object Daily {
    var setupDone by mutableStateOf(false); private set
    val roles = mutableStateListOf<String>()               // student, teacher, employee, business
    var city by mutableStateOf("riyadh"); private set
    var showPrayer by mutableStateOf(true); private set
    var salary by mutableStateOf("gov"); private set      // gov | custom | none
    var salaryDay by mutableStateOf(27); private set
    var showCitizen by mutableStateOf(false); private set
    var showPension by mutableStateOf(false); private set
    var showDocs by mutableStateOf(true); private set
    var showAdhkar by mutableStateOf(true); private set
    private var loaded = false

    fun load() {
        if (loaded) return; loaded = true
        val o = Store.obj("daily")
        setupDone = o.optBoolean("done")
        roles.clear(); o.optJSONArray("roles")?.let { a -> for (i in 0 until a.length()) roles.add(a.optString(i)) }
        if (roles.isEmpty() && Prefs.role.isNotBlank() && Prefs.role != "none") roles.add(Prefs.role)
        city = o.optString("city", "riyadh"); showPrayer = o.optBoolean("prayer", true)
        salary = o.optString("salary", "gov"); salaryDay = o.optInt("salaryDay", 27)
        showCitizen = o.optBoolean("citizen", false); showPension = o.optBoolean("pension", false)
        showDocs = o.optBoolean("docs", true); showAdhkar = o.optBoolean("adhkar", true)
    }

    private fun save() = Store.saveObj("daily", JSONObject().put("done", setupDone).put("roles", JSONArray(roles.toList())).put("city", city)
        .put("prayer", showPrayer).put("salary", salary).put("salaryDay", salaryDay).put("citizen", showCitizen).put("pension", showPension)
        .put("docs", showDocs).put("adhkar", showAdhkar))

    fun toggleRole(r: String) {
        if (r in roles) roles.remove(r) else roles.add(r)
        Prefs.chooseRole(roles.firstOrNull() ?: "none"); save()
    }
    fun chooseCity(id: String) { city = Cities.byId(id).id; save() }
    fun setPrayer(v: Boolean) { showPrayer = v; save() }
    fun setSalary(kind: String, day: Int = salaryDay) { salary = kind; salaryDay = day.coerceIn(1, 31); save() }
    fun setCitizen(v: Boolean) { showCitizen = v; save() }
    fun setPension(v: Boolean) { showPension = v; save() }
    fun setDocs(v: Boolean) { showDocs = v; save() }
    fun setAdhkar(v: Boolean) { showAdhkar = v; save() }
    fun finishSetup() { setupDone = true; save() }

    /** Payment rules the person follows, in display order. */
    fun payRules(): List<PayDays.Rule> = buildList {
        when (salary) { "gov" -> add(PayDays.gov); "custom" -> add(PayDays.custom(salaryDay)) }
        if (showCitizen) add(PayDays.citizen)
        if (showPension) add(PayDays.pension)
    }

    fun resetForTest() { loaded = false; Store.saveObj("daily", JSONObject()); load() }
}
