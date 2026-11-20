package com.muyassir.app.engine

object Units {
    data class U(val name: String, val toBase: Double)
    data class Kind(val name: String, val units: List<U>)

    val kinds = listOf(
        Kind("الطول", listOf(U("مليمتر", 0.001), U("سنتيمتر", 0.01), U("متر", 1.0), U("كيلومتر", 1000.0), U("بوصة", 0.0254), U("قدم", 0.3048), U("ياردة", 0.9144), U("ميل", 1609.344))),
        Kind("الوزن", listOf(U("جرام", 0.001), U("كيلوجرام", 1.0), U("طن", 1000.0), U("أونصة", 0.0283495), U("رطل", 0.453592))),
        Kind("المساحة", listOf(U("متر مربع", 1.0), U("كيلومتر مربع", 1e6), U("هكتار", 10000.0), U("دونم", 1000.0), U("قدم مربع", 0.092903), U("فدان", 4046.86))),
        Kind("الحجم", listOf(U("مليلتر", 0.001), U("لتر", 1.0), U("متر مكعب", 1000.0), U("جالون أمريكي", 3.78541), U("كوب", 0.24))),
        Kind("السرعة", listOf(U("متر/ث", 1.0), U("كم/س", 1 / 3.6), U("ميل/س", 0.44704), U("عقدة", 0.514444))),
        Kind("البيانات", listOf(U("بايت", 1.0), U("كيلوبايت", 1024.0), U("ميجابايت", 1048576.0), U("جيجابايت", 1073741824.0), U("تيرابايت", 1099511627776.0))),
        Kind("الوقت", listOf(U("ثانية", 1.0), U("دقيقة", 60.0), U("ساعة", 3600.0), U("يوم", 86400.0), U("أسبوع", 604800.0))),
        Kind("الحرارة", listOf(U("مئوية", 1.0), U("فهرنهايت", 1.0), U("كلفن", 1.0))),
    )

    fun convert(kind: Kind, v: Double, from: U, to: U): Double {
        if (kind.name == "الحرارة") {
            val c = when (from.name) { "فهرنهايت" -> (v - 32) * 5 / 9; "كلفن" -> v - 273.15; else -> v }
            return when (to.name) { "فهرنهايت" -> c * 9 / 5 + 32; "كلفن" -> c + 273.15; else -> c }
        }
        return v * from.toBase / to.toBase
    }

    // ---- areas & volumes ----
    data class Shape(val name: String, val fields: List<String>, val volume: Boolean, val f: (List<Double>) -> Double)

    val shapes = listOf(
        Shape("مستطيل", listOf("الطول", "العرض"), false) { it[0] * it[1] },
        Shape("مربع", listOf("الضلع"), false) { it[0] * it[0] },
        Shape("دائرة", listOf("نصف القطر"), false) { Math.PI * it[0] * it[0] },
        Shape("مثلث", listOf("القاعدة", "الارتفاع"), false) { it[0] * it[1] / 2 },
        Shape("شبه منحرف", listOf("القاعدة الأولى", "القاعدة الثانية", "الارتفاع"), false) { (it[0] + it[1]) / 2 * it[2] },
        Shape("متوازي أضلاع", listOf("القاعدة", "الارتفاع"), false) { it[0] * it[1] },
        Shape("مكعب", listOf("الضلع"), true) { it[0] * it[0] * it[0] },
        Shape("متوازي مستطيلات", listOf("الطول", "العرض", "الارتفاع"), true) { it[0] * it[1] * it[2] },
        Shape("أسطوانة", listOf("نصف القطر", "الارتفاع"), true) { Math.PI * it[0] * it[0] * it[1] },
        Shape("كرة", listOf("نصف القطر"), true) { 4.0 / 3 * Math.PI * it[0] * it[0] * it[0] },
        Shape("مخروط", listOf("نصف القطر", "الارتفاع"), true) { Math.PI * it[0] * it[0] * it[1] / 3 },
        Shape("هرم", listOf("مساحة القاعدة", "الارتفاع"), true) { it[0] * it[1] / 3 },
    )
}
