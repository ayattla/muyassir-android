package com.muyassir.app.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.muyassir.app.core.Store
import org.json.JSONObject

/** Plus: lock notes and documents behind the device's fingerprint / face / screen lock. Everything stays on the device. */
object AppLock {
    var enabled by mutableStateOf(false); private set
    /** Unlocked for this app session. */
    var open by mutableStateOf(false)
    private var loaded = false

    fun load() { if (loaded) return; loaded = true; enabled = Store.obj("lock").optBoolean("on") }
    fun set(v: Boolean) { enabled = v; open = v; Store.saveObj("lock", JSONObject().put("on", v)) }

    private fun authenticators() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun available(a: FragmentActivity) = BiometricManager.from(a).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(a: FragmentActivity, title: String, ok: () -> Unit, fail: (String) -> Unit = {}) {
        val p = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = ok()
            override fun onAuthenticationError(code: Int, msg: CharSequence) = fail(msg.toString())
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("بالبصمة أو الوجه أو قفل الشاشة").setAllowedAuthenticators(authenticators())
        if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("إلغاء")
        p.authenticate(info.build())
    }
}

/** Shows [content] only after the user unlocks, when the lock is on. */
@Composable
fun LockGate(title: String, back: (() -> Unit)? = null, content: @Composable () -> Unit) {
    LaunchedEffect(Unit) { AppLock.load() }
    if (!AppLock.enabled || AppLock.open) { content(); return }
    val ctx = LocalContext.current
    val act = ctx.activity() as? FragmentActivity
    var msg by remember { mutableStateOf<String?>(null) }
    fun ask() { act?.let { AppLock.prompt(it, "فتح $title", { AppLock.open = true }, { m -> msg = m }) } }
    LaunchedEffect(Unit) { ask() }
    Column(Modifier.fillMaxSize().padding(Space.xxl).testTag("locked"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Lock, null, tint = tokens.accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Space.m))
        Text("$title مقفلة", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        Text("افتحها ببصمتك أو وجهك أو رمز قفل الجهاز.", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, textAlign = TextAlign.Center)
        msg?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(Space.l))
        PrimaryButton("فتح", Icons.Rounded.Fingerprint) { ask() }
        back?.let { TextButton(onClick = it) { Text("رجوع") } }
    }
}
