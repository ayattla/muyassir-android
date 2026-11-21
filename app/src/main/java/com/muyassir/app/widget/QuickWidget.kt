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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.muyassir.app.MainActivity
import com.muyassir.app.core.Store
import java.util.concurrent.TimeUnit

/** Home-screen widget: four one-tap tools and the nearest exam or document deadline (read from the device only). */
class QuickWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val next = nextDeadline()
        provideContent { Content(context, next) }
    }

    private fun nextDeadline(): String? {
        val now = System.currentTimeMillis()
        val items = Store.list("exams").map { it.optString("subject") to it.optLong("at") } +
            Store.list("documents").map { it.optString("name") to it.optLong("expiry") }
        val (name, at) = items.filter { it.second > now }.minByOrNull { it.second } ?: return null
        val d = TimeUnit.MILLISECONDS.toDays(at - now)
        return "$name ${if (d == 0L) "اليوم" else if (d == 1L) "غدًا" else "بعد $d يوم"}"
    }

    @Composable
    private fun Content(ctx: Context, next: String?) {
        val ink = ColorProvider(Color(0xFF141A2E)); val accent = ColorProvider(Color(0xFF3346D3)); val muted = ColorProvider(Color(0xFF4F586E))
        Column(GlanceModifier.fillMaxSize().background(Color.White).cornerRadius(20.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("مُيسّر", style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(Triple("مسح", "tool", "scan"), Triple("ضغط PDF", "tool", "pdf_compress"), Triple("صور إلى PDF", "tool", "img_pdf"), Triple("الوصفات", "screen", "recipes"))
                    .forEachIndexed { i, (label, k, v) ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        Box(
                            GlanceModifier.background(Color(0xFFECEEFD)).cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = 10.dp)
                                .clickable(actionStartActivity(Intent(ctx, MainActivity::class.java).putExtra(k, v).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)) }
                    }
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(next?.let { "أقرب موعد: $it" } ?: "لا مواعيد قادمة", style = TextStyle(color = muted, fontSize = 12.sp, textAlign = TextAlign.Center))
        }
    }
}

class QuickWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickWidget() }
