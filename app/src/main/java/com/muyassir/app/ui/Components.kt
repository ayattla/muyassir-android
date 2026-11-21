package com.muyassir.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tool
import com.muyassir.app.core.Tools
import java.util.Locale

/** Frame shared by every tool: centred title bar, a centred header that names the tool, then the tool's content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(tool: Tool, onBack: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val fav = tool.id in Store.favorites
    val c = tone(Tools.colorOf(tool))
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tool.title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { Store.toggleFavorite(tool.id) }) {
                        Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                            tint = if (fav) Brand.Saffron else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = Space.l, vertical = Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = Space.xs), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(toneSoft(Tools.colorOf(tool))), contentAlignment = Alignment.Center) {
                        Icon(tool.icon, null, tint = c, modifier = Modifier.size(26.dp))
                    }
                    Text(tool.sub, style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary, textAlign = TextAlign.Center)
                }
                content()
                Spacer(Modifier.height(Space.xxl))
            }
        }
    }
}

/** Card surface used for every group of inputs or results. */
@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().cardSurface().padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        content()
    }
}

/** Raised card: white with hairline border and a soft shadow in light mode; lifted surface in dark mode. */
@Composable
fun Modifier.cardSurface(radius: androidx.compose.ui.unit.Dp = Radius.l): Modifier {
    val k = tokens
    val shape = RoundedCornerShape(radius)
    return this.then(if (!k.dark) Modifier.shadow(2.dp, shape, ambientColor = k.shadow, spotColor = k.shadow) else Modifier)
        .clip(shape).background(k.surface).border(1.dp, k.border, shape)
}

/** Press feedback: a quick scale-down while pressed (instant when the system turns animations off). */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick)
}

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = { v -> onChange(v.map { if (it in '٠'..'٩') ('0' + (it - '٠')) else if (it == '٫') '.' else it }.joinToString("")) },
        label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(Radius.m),
        suffix = suffix?.let { { Text(it, color = tokens.textTertiary) } }, colors = fieldColors(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun TextField2(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, lines: Int = 1, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = lines == 1, minLines = lines,
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.m), keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = fieldColors(),
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = tokens.surface, focusedContainerColor = tokens.surface,
    unfocusedBorderColor = tokens.borderStrong, focusedBorderColor = tokens.accent,
    unfocusedLabelColor = tokens.textSecondary, focusedLabelColor = tokens.accent,
)

fun String.num(): Double = trim().replace(",", "").toDoubleOrNull() ?: 0.0

fun money(v: Double, unit: String = "ريال") = String.format(Locale.US, "%,.2f %s", v, unit)
fun fmt(v: Double, d: Int = 2): String = if (v.isNaN() || v.isInfinite()) "—" else {
    val s = String.format(Locale.US, "%,.${d}f", v); if (d > 0) s.trimEnd('0').trimEnd('.') else s
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Result block: the key figure centred and large, supporting rows underneath. */
@Composable
fun ResultCard(main: Pair<String, String>?, rows: List<Pair<String, String>>, note: String? = null) {
    val k = tokens
    androidx.compose.animation.AnimatedVisibility(visible = true, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(k.accentSoft)
                .border(1.dp, k.accent.copy(alpha = 0.18f), RoundedCornerShape(Radius.l)).padding(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            main?.let { (label, v) ->
                Text(label, color = k.textSecondary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(v, color = k.accent, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (rows.isNotEmpty()) HorizontalDivider(color = k.accent.copy(alpha = 0.15f))
            }
            rows.forEach { (label, v) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = k.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(v, color = k.textPrimary, style = MaterialTheme.typography.titleSmall)
                }
            }
            note?.let { Text(it, color = k.textTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(Radius.m),
        colors = ButtonDefaults.buttonColors(disabledContainerColor = tokens.surfaceSunken, disabledContentColor = tokens.textTertiary),
    ) {
        icon?.let { Icon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(Space.s)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(Radius.m),
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = tokens.surface, contentColor = tokens.textPrimary),
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = tokens.accent); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Tool card. Grid style (default): centred icon, name and purpose. Row style for compact lists. */
@Composable
fun ToolTile(tool: Tool, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val base = Tools.colorOf(tool); val c = tone(base)
    if (compact) {
        Row(
            modifier.fillMaxWidth().cardSurface(Radius.m).pressable(onClick).padding(horizontal = Space.m, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(toneSoft(base)), contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = c, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.m))
            Text(tool.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = tokens.textTertiary, modifier = Modifier.size(20.dp))
        }
        return
    }
    Column(
        modifier.fillMaxWidth().heightIn(min = 148.dp).cardSurface().pressable(onClick).padding(horizontal = Space.m, vertical = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(toneSoft(base)), contentAlignment = Alignment.Center) {
            Icon(tool.icon, null, tint = c, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(tool.title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, textAlign = TextAlign.Center, maxLines = 2)
        Text(tool.sub, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) tokens.accent else tokens.surface)
            .border(1.dp, if (selected) Color.Transparent else tokens.borderStrong, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) tokens.onAccent else tokens.textPrimary) }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun Hint(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(tokens.surfaceSunken).padding(Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Info, null, tint = tokens.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(tokens.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tokens.accent, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun <T> Picker(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = name(selected), onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp), trailingIcon = { Icon(Icons.Rounded.ExpandMore, null) },
        )
        Box(Modifier.matchParentSize().semantics { contentDescription = label }.clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(name(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", text))
    Toast.makeText(ctx, "تم النسخ", Toast.LENGTH_SHORT).show()
}

fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()

@Composable
fun TextResult(text: String, label: String = "النتيجة") {
    val ctx = LocalContext.current
    Panel(label) {
        Text(text.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("نسخ", Icons.Rounded.ContentCopy) { copy(ctx, text) }
            SecondaryButton("مشاركة", Icons.Rounded.Share) {
                ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "مشاركة"))
            }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.Bold else null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}
