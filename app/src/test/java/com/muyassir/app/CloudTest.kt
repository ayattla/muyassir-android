package com.muyassir.app

import com.muyassir.app.core.Cloud
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudTest {
    @Test fun mapsEveryAutoGradedType() {
        val qs = listOf(
            JSONObject().put("type", 0).put("q", "كم عدد أيام الأسبوع؟").put("o0", "خمسة").put("o1", "سبعة").put("ans", 1).put("mark", "2"),
            JSONObject().put("type", 1).put("q", "الشمس نجم").put("ans", 0),
            JSONObject().put("type", 2).put("q", "اشرح دورة الماء"),
            JSONObject().put("type", 3).put("q", "يغلي الماء عند ___ درجة").put("answer", "100"),
            JSONObject().put("type", 4).put("q", "").put("pairs", "الرياض = السعودية\nالقاهرة = مصر"),
            JSONObject().put("type", 0).put("q", "ناقص").put("o0", "واحد"),
        )
        val m = Cloud.toActivity("اختبار", qs, 15, 3)
        val a = m.activity.getJSONArray("questions")
        assertEquals(4, a.length()); assertEquals(2, m.skipped)
        assertEquals("choice", a.getJSONObject(0).getString("type")); assertEquals("سبعة", a.getJSONObject(0).getString("answer")); assertEquals(2.0, a.getJSONObject(0).getDouble("mark"), 0.0)
        assertEquals("صح", a.getJSONObject(1).getString("answer"))
        assertEquals("يغلي الماء عند ...... درجة", a.getJSONObject(2).getString("text"))
        val s = a.getJSONObject(3)
        assertEquals("sort", s.getString("type")); assertEquals(listOf("السعودية", "مصر"), (0 until 2).map { s.getJSONArray("answer").getString(it) })
        assertEquals(true, s.getString("text").contains("1) الرياض"))
        assertEquals(15, m.activity.getInt("minutes")); assertEquals(3, m.activity.getInt("closes_in_days"))
    }
}
