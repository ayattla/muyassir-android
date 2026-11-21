package com.muyassir.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.muyassir.app.core.Prefs
import com.muyassir.app.core.Store
import com.muyassir.app.core.Tools
import com.muyassir.app.tools.ToolHost

/** True on tablets and wide windows: navigation moves to a side rail and grids gain columns. */
val LocalWide = staticCompositionLocalOf { false }

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selected: ImageVector)
private val tabs = listOf(
    Tab("home", "الرئيسية", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab("search", "البحث", Icons.Outlined.Search, Icons.Rounded.Search),
    Tab("assistant", "المساعد", Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    Tab("activity", "نشاطي", Icons.Outlined.History, Icons.Rounded.History),
    Tab("settings", "الإعدادات", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
fun AppNav(startTool: String? = null, startCat: String? = null, startTab: String? = null, startScreen: String? = null) {
    val ctx = LocalContext.current
    Store.init(ctx); Prefs.load(ctx)
    val nav = rememberNavController()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    // A launcher shortcut or the automated UI test can open a tool or a category directly.
    // Deep links are applied once the navigation graph exists (from the home destination), never before.
    var pendingStart by rememberSaveable { mutableStateOf(true) }
    var seenIncoming by rememberSaveable { mutableStateOf(0) }
    fun applyStart() {
        if (!pendingStart) return; pendingStart = false
        if (!startTool.isNullOrBlank() && Tools.byId.containsKey(startTool)) nav.navigate("tool/$startTool")
        else if (!startCat.isNullOrBlank() && Tools.categories.any { it.id == startCat }) nav.navigate("cat/$startCat")
        else if (startTab in setOf("search", "assistant", "activity", "settings")) nav.navigate(startTab!!)
        else if (startScreen == "recipes") nav.navigate("recipes")
        else if (startScreen?.startsWith("recipe:") == true) nav.navigate("recipe/${startScreen.removePrefix("recipe:")}")
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val top = tabs.any { it.route == route }
    fun go(r: String) = nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
    val openTool: (String) -> Unit = { nav.navigate("tool/$it") }

    CompositionLocalProvider(LocalWide provides wide) {
        Row(Modifier.fillMaxSize()) {
            if (wide && top) NavigationRail(containerColor = tokens.surface, header = { LogoMark(40.dp_()) }) {
                Spacer(Modifier.weight(1f))
                tabs.forEach { t -> NavigationRailItem(selected = route == t.route, onClick = { go(t.route) }, icon = { Icon(if (route == t.route) t.selected else t.icon, null) }, label = { Text(t.label) }) }
                Spacer(Modifier.weight(1f))
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    AnimatedVisibility(!wide && top, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        NavigationBar(containerColor = tokens.surface, tonalElevation = 0.dp_()) {
                            tabs.forEach { t ->
                                NavigationBarItem(
                                    selected = route == t.route, onClick = { go(t.route) },
                                    icon = { Icon(if (route == t.route) t.selected else t.icon, null) }, label = { Text(t.label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = tokens.accentSoft, selectedIconColor = tokens.accent, selectedTextColor = tokens.accent,
                                        unselectedIconColor = tokens.textTertiary, unselectedTextColor = tokens.textTertiary),
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                NavHost(
                    nav, startDestination = "home", modifier = Modifier.padding(pad),
                    enterTransition = { fadeIn(androidx.compose.animation.core.tween(180)) + slideInHorizontally(androidx.compose.animation.core.tween(220)) { -it / 12 } },
                    exitTransition = { fadeOut(androidx.compose.animation.core.tween(120)) },
                    popEnterTransition = { fadeIn(androidx.compose.animation.core.tween(180)) },
                    popExitTransition = { fadeOut(androidx.compose.animation.core.tween(120)) + slideOutHorizontally(androidx.compose.animation.core.tween(200)) { -it / 12 } },
                ) {
                    composable("home") {
                        LaunchedEffect(Unit) { applyStart() }
                        // Files shared from another app («فتح في مُيسّر») open the choice screen.
                        LaunchedEffect(com.muyassir.app.core.Incoming.version) { if (com.muyassir.app.core.Incoming.version > seenIncoming) { seenIncoming = com.muyassir.app.core.Incoming.version; nav.navigate("incoming") } }
                        Home(open = openTool, openCat = { nav.navigate("cat/$it") }, openSearch = { go("search") }, openAssistant = { go("assistant") }, openActivity = { go("activity") },
                            openRecipes = { nav.navigate("recipes") }, openRecipe = { nav.navigate("recipe/$it") })
                    }
                    composable("incoming") { IncomingScreen(openTool = { nav.navigate("tool/$it") { popUpTo("incoming") { inclusive = true } } }, openRecipe = { nav.navigate("recipe/$it") { popUpTo("incoming") { inclusive = true } } }, back = { nav.popBackStack() }) }
                    composable("recipes") { RecipesScreen(open = { nav.navigate("recipe/$it") }, back = { nav.popBackStack() }) }
                    composable("recipe/{id}") { e -> RecipeRun(e.arguments?.getString("id") ?: "", back = { nav.popBackStack() }) }
                    composable("search") { SearchScreen(open = openTool, openAssistant = { go("assistant") }) }
                    composable("assistant") { AssistantScreen(open = openTool, openRecipe = { nav.navigate("recipe/$it") }) }
                    composable("activity") { ActivityScreen(open = openTool) }
                    composable("settings") { SettingsScreen() }
                    composable("cat/{id}") { e -> CategoryScreen(e.arguments?.getString("id") ?: "files", open = openTool, back = { nav.popBackStack() }) }
                    composable("tool/{id}") { e -> ToolHost(e.arguments?.getString("id") ?: "", back = { nav.popBackStack() }, open = openTool) }
                }
            }
        }
    }
}

private fun Int.dp_() = androidx.compose.ui.unit.Dp(this.toFloat())
