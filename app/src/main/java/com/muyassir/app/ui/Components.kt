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
import androidx.compose.ui.draw.clip
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

/** Frame shared by every tool: title bar with back and favourite, scrollable centred column (max 720dp for tablets). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(tool: Tool, onBack: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val fav = tool.id in Store.favorites
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(tool.title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { Store.toggleFavorite(tool.id) }) {
                        Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                            tint = if (fav) Brand.Saffron else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(tool.sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = { v -> onChange(v.map { if (it in '٠'..'٩') ('0' + (it - '٠')) else if (it == '٫') '.' else it }.joinToString("")) },
        label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp),
        suffix = suffix?.let { { Text(it) } },
    )
}

@Composable
fun TextField2(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, lines: Int = 1, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = lines == 1, minLines = lines,
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

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

/** Result block: one highlighted figure, then supporting rows. */
@Composable
fun ResultCard(main: Pair<String, String>?, rows: List<Pair<String, String>>, note: String? = null) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Ink).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        main?.let { (k, v) ->
            Text(k, color = Color(0xFFB7BFDB), style = MaterialTheme.typography.labelLarge)
            Text(v, color = Color.White, style = MaterialTheme.typography.headlineMedium)
            if (rows.isNotEmpty()) HorizontalDivider(color = Color(0x33FFFFFF))
        }
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, color = Color(0xFFB7BFDB), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(v, color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
        note?.let { Text(it, color = Color(0xFF9AA3C2), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
        icon?.let { Icon(it, null); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) {
        icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ToolTile(tool: Tool, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val color = Tools.colorOf(tool)
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(tool.icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tool.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (!compact) Text(tool.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface) }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
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
        Box(Modifier.matchParentSize().clickable { open = true })
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
