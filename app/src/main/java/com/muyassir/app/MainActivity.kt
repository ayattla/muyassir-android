package com.muyassir.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.muyassir.app.ui.AppNav
import com.muyassir.app.ui.MuyassirTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.muyassir.app.core.Prefs.load(this)
        com.muyassir.app.core.Plus.load(this)
        val testPlus = if (BuildConfig.DEBUG) intent?.getStringExtra("plus") else null
        if (testPlus != null) com.muyassir.app.core.Plus.testState(testPlus) else com.muyassir.app.core.Billing.start(this)
        if (BuildConfig.DEBUG) intent?.getStringExtra("paywall")?.let { n -> com.muyassir.app.core.Plus.paywall = com.muyassir.app.core.Plus.Reason.entries.firstOrNull { it.name == n } }
        intent?.getStringExtra("theme")?.let { if (it in setOf("light", "dark", "system")) com.muyassir.app.core.Prefs.chooseTheme(it) }
        setContent {
            MuyassirTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { AppNav(intent?.getStringExtra("tool"), intent?.getStringExtra("cat"), intent?.getStringExtra("tab")); com.muyassir.app.ui.PaywallHost() }
            }
        }
    }

    override fun onResume() { super.onResume(); com.muyassir.app.core.Billing.refresh() }
}
