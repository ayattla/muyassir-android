package com.muyassir.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.muyassir.app.MainActivity
import com.muyassir.app.core.Store
import com.muyassir.app.engine.Cities
import com.muyassir.app.engine.PayDays
import com.muyassir.app.engine.PrayerTimes
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** «يومي» on the home screen: the next prayer with its time (not a countdown, so it stays right between refreshes),
 *  the next pay day and the nearest document. Reads only what is on the device. */
class TodayWidget : GlanceAppWidget() {
    data class Info(val prayer: String?, val city: String, val pay: String?, val doc: String?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val info = read()
        provideContent { Content(context, info) }
    }

    private fun read(): Info {
        val o = Store.obj("daily")
        val now = GregorianCalendar(TimeZone.getTimeZone("Asia/Riyadh"))
        val city = Cities.byId(o.optString("city", "riyadh"))
        val prayer = if (o.optBoolean("prayer", true)) {
            val t = PrayerTimes.today(city, now)
            val tom = PrayerTimes.today(city, (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) })
            val (i, _) = PrayerTimes.next(t, now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE), tom[0])
            val at = if (i == 0 && t[0] <= now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) tom[0] else t[i]
            "${PrayerTimes.names[i]} ${PrayerTimes.clock(at)}"
        } else null
        val rule = when (o.optString("salary", "gov")) { "gov" -> PayDays.gov; "custom" -> PayDays.custom(o.optInt("salaryDay", 27)); else -> null }
        val pay = rule?.let { r -> val (d, _) = PayDays.next(r, now); if (d == 0) "${r.title} اليوم" else "${r.title} بعد $d ${if (d in 3..10) "أيام" else "يومًا"}" }
        val doc = if (o.optBoolean("docs", true)) Store.list("documents").filter { it.optLong("expiry") > 0 }
            .map { it to com.muyassir.app.tools.daysUntil(it.optLong("expiry")) }.filter { it.second in 0..60 }.minByOrNull { it.second }
            ?.let { (_, d) -> if (d == 0L) "وثيقة تنتهي اليوم" else "وثيقة تنتهي بعد $d يومًا" } else null
        return Info(prayer, city.name, pay, doc)
    }

    @Composable
    private fun Content(ctx: Context, i: Info) {
        val open = actionStartActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        val onAccent = ColorProvider(Color.White); val soft = ColorProvider(Color(0xFFDDE1FB))
        Column(
            GlanceModifier.fillMaxSize().background(Color(0xFF3346D3)).cornerRadius(20.dp).padding(14.dp).clickable(open),
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (i.prayer != null) "الصلاة القادمة في ${i.city}" else "يومي", style = TextStyle(color = soft, fontSize = 12.sp, textAlign = TextAlign.Center))
            Text(i.prayer ?: "مُيسّر", style = TextStyle(color = onAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            if (i.pay != null || i.doc != null) {
                Spacer(GlanceModifier.height(8.dp))
                listOfNotNull(i.pay, i.doc).forEach {
                    Text(it, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(color = onAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }
