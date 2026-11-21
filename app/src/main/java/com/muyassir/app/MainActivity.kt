package com.muyassir.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.muyassir.app.ui.AppNav
import com.muyassir.app.ui.MuyassirTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.muyassir.app.core.Prefs.load(this)
        intent?.getStringExtra("theme")?.let { if (it in setOf("light", "dark", "system")) com.muyassir.app.core.Prefs.chooseTheme(it) }
        setContent {
            MuyassirTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { AppNav(intent?.getStringExtra("tool"), intent?.getStringExtra("cat"), intent?.getStringExtra("tab")) }
            }
        }
    }
}
