package com.muyassir.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Plus
import com.muyassir.app.core.Templates
import com.muyassir.app.engine.ImageEngine

/** Template, colour and logo for the document tools. Locked choices open the paywall. */
@Composable
fun TemplatePanel(useLogo: Boolean = true, styles: Boolean = true) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Templates.load(ctx) }
    val sel = Templates.all[Templates.selected]
    val shown = Templates.current
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        u?.let { runCatching { Templates.setLogo(ctx, ImageEngine.load(ctx, it, 600)) }.onFailure { toast(ctx, "تعذر فتح الصورة") } }
    }
    fun choose(layout: Int, palette: Int) {
        val i = layout * Templates.palettes.size + palette
        if (i == 0 || Plus.allow(Plus.Reason.Template)) Templates.choose(i)
    }
    Panel(modifier = Modifier.testTag("templates")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text(if (styles) "القالب" else "شعار المنشأة", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary); Spacer(Modifier.width(6.dp)); PlusBadge()
        }
        if (styles) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Templates.layouts.forEachIndexed { li, name ->
                val on = shown.layout == li
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(Radius.s)).border(if (on) 2.dp else 1.dp, if (on) tokens.accent else tokens.border, RoundedCornerShape(Radius.s))
                        .pressable { choose(li, if (Plus.unlocked) sel.index % Templates.palettes.size else 0) }.padding(6.dp)
                        .semantics { contentDescription = "قالب $name" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MiniPage(li, Color(shown.accent))
                    Text(name, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary, maxLines = 1)
                }
            }
        }
        if (styles) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Templates.palettes.forEachIndexed { pi, (name, c) ->
                val on = shown.accent == c
                Box(
                    Modifier.padding(4.dp).size(30.dp).clip(CircleShape).background(Color(c))
                        .border(if (on) 3.dp else 0.dp, if (on) tokens.textPrimary else Color.Transparent, CircleShape)
                        .pressable { choose(shown.layout, pi) }.semantics { contentDescription = "لون $name" },
                )
            }
        }
        Text(
            if (!styles) (if (Plus.unlocked) "يظهر شعارك أعلى المستند" else "مع بلس: شعارك دون سطر «${Plus.FOOTER}».")
            else if (Plus.unlocked) "${Templates.all.size} قالبا: ${shown.name}" else "المجاني: القالب الكلاسيكي مع سطر «${Plus.FOOTER}». بلس: ${Templates.all.size} قالبا وشعارك دون السطر.",
            style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary, modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (useLogo) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SecondaryButton(if (Templates.hasLogo) "تغيير شعار المنشأة" else "إضافة شعار المنشأة", Icons.Rounded.AddPhotoAlternate, Modifier.weight(1f)) {
                if (Plus.allow(Plus.Reason.Template)) pickLogo.launch("image/*")
            }
            if (Templates.hasLogo) SecondaryButton("إزالة", Icons.Rounded.Delete, Modifier.weight(0.6f)) { Templates.setLogo(ctx, null) }
        }
    }
}

/** Tiny drawing of a page in the given layout. */
@Composable
private fun MiniPage(layout: Int, accent: Color) {
    val line = tokens.borderStrong
    Canvas(Modifier.width(44.dp).height(58.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, tokens.border, RoundedCornerShape(4.dp))) {
        val w = size.width; val h = size.height
        when (layout) {
            1 -> drawRect(accent, Offset.Zero, Size(w, h * 0.22f))
            2 -> drawRect(accent, Offset(w - w * 0.08f, 0f), Size(w * 0.08f, h))
        }
        if (layout == 3) {
            drawRect(Color(0xFF141A2E), Offset(w * 0.25f, h * 0.1f), Size(w * 0.5f, h * 0.06f))
            drawRect(accent, Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, 1.5f)); drawRect(accent, Offset(w * 0.2f, h * 0.23f), Size(w * 0.6f, 1.5f))
        } else if (layout != 1) {
            drawRect(Color(0xFF141A2E), Offset(w * 0.4f, h * 0.1f), Size(w * 0.45f, h * 0.06f))
            drawRect(accent, Offset(w * 0.7f, h * 0.2f), Size(w * 0.15f, h * 0.025f))
        }
        for (k in 0 until 5) drawRect(line, Offset(w * 0.15f, h * (0.34f + k * 0.11f)), Size(w * 0.7f, h * 0.025f))
    }
}
