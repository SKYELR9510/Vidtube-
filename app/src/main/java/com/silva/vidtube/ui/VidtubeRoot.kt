package com.silva.vidtube.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.silva.vidtube.data.VideoItem
import com.silva.vidtube.player.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow

private data class Tab(val route: String, val label: String, val selected: ImageVector, val normal: ImageVector)

private val tabs = listOf(
    Tab("home", "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("subs", "Suscripciones", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
    Tab("library", "Tú", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
)

/** Canal pendiente de abrir (por ejemplo desde un link compartido). */
val pendingChannel = MutableStateFlow<String?>(null)

/** Pantalla pendiente de abrir (por ejemplo al tocar la notificación de descarga). */
val pendingRoute = MutableStateFlow<String?>(null)

@Composable
fun VidtubeRoot(isInPip: Boolean) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val now by NowPlaying.state.collectAsState()
    val expanded by NowPlaying.expanded.collectAsState()
    val channelToOpen by pendingChannel.collectAsState()
    val routeToOpen by pendingRoute.collectAsState()

    LaunchedEffect(routeToOpen) {
        val route = routeToOpen ?: return@LaunchedEffect
        pendingRoute.value = null
        NowPlaying.minimize()
        nav.navigate(route) { launchSingleTop = true }
    }

    LaunchedEffect(channelToOpen) {
        val url = channelToOpen ?: return@LaunchedEffect
        pendingChannel.value = null
        NowPlaying.minimize()
        nav.navigate("channel/" + Uri.encode(url))
    }

    if (isInPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(Modifier.fillMaxSize(), useController = false)
        }
        return
    }

    val openVideo: (VideoItem) -> Unit = { NowPlaying.open(context, it) }
    val openChannel: (String) -> Unit = { url ->
        NowPlaying.minimize()
        nav.navigate("channel/" + Uri.encode(url))
    }
    val openSearch: () -> Unit = { nav.navigate("search") { launchSingleTop = true } }

    Box(Modifier.fillMaxSize().background(VColors.Background)) {
        Scaffold(
            containerColor = VColors.Background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                Column {
                    val current = now
                    if (current != null && !expanded) MiniPlayer(current)
                    BottomBar(nav)
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                composable("home") { HomeScreen(openVideo, openChannel, openSearch) }
                composable("subs") { SubscriptionsScreen(openVideo, openChannel, openSearch) }
                composable("library") {
                    LibraryScreen(
                        openVideo,
                        onOpenList = { if (it == "downloads") nav.navigate("downloads") else nav.navigate("list/$it") },
                        onSearch = openSearch,
                    )
                }
                composable("search") {
                    SearchScreen(onBack = { nav.popBackStack() }, onVideo = openVideo, onChannel = openChannel)
                }
                composable("channel/{url}") { entry ->
                    val url = Uri.decode(entry.arguments?.getString("url") ?: "")
                    ChannelScreen(url, onBack = { nav.popBackStack() }, onVideo = openVideo, onSearch = openSearch)
                }
                composable("downloads") { DownloadsScreen(onBack = { nav.popBackStack() }) }
                composable("list/{kind}") { entry ->
                    VideoListScreen(
                        kind = entry.arguments?.getString("kind") ?: "history",
                        onBack = { nav.popBackStack() },
                        onVideo = openVideo,
                        onChannel = openChannel,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && now != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            val current = now
            if (current != null) VideoScreen(current, onChannel = openChannel)
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    NavigationBar(containerColor = VColors.Background, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
        tabs.forEach { tab ->
            val selected = route == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(if (selected) tab.selected else tab.normal, tab.label) },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VColors.Text,
                    selectedTextColor = VColors.Text,
                    unselectedIconColor = VColors.Text,
                    unselectedTextColor = VColors.Text,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

