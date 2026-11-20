package com.muyassir.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.muyassir.app.core.Store
import com.muyassir.app.tools.ToolHost

@Composable
fun AppNav(startTool: String? = null) {
    Store.init(LocalContext.current)
    val nav = rememberNavController()
    // A launcher shortcut or the automated UI test can open a tool directly.
    androidx.compose.runtime.LaunchedEffect(startTool) { if (!startTool.isNullOrBlank() && com.muyassir.app.core.Tools.byId.containsKey(startTool)) nav.navigate("tool/$startTool") }
    NavHost(nav, startDestination = "home") {
        composable("home") { Home(open = { nav.navigate("tool/$it") }, openCat = { nav.navigate("cat/$it") }) }
        composable("cat/{id}") { e -> CategoryScreen(e.arguments?.getString("id") ?: "files", open = { nav.navigate("tool/$it") }, back = { nav.popBackStack() }) }
        composable("tool/{id}") { e -> ToolHost(e.arguments?.getString("id") ?: "", back = { nav.popBackStack() }, open = { nav.navigate("tool/$it") }) }
    }
}
