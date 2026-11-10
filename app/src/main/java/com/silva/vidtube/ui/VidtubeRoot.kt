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
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.outlined.Slideshow
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
import androidx.compose.runtime.setValue
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
    var showSplash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!splashShown) }
    LaunchedEffect(Unit) {
        if (showSplash) {
            kotlinx.coroutines.delay(1400)
            splashShown = true
            showSplash = false
        }
    }

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
    val openSettings: () -> Unit = { nav.navigate("settings") { launchSingleTop = true } }

    Box(Modifier.fillMaxSize().background(VColors.Background)) {
        Scaffold(
            containerColor = VColors.Background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                Column {
                    val current = now
                    val onShorts = nav.currentBackStackEntryAsState().value?.destination?.route == "shorts"
                    if (current != null && !expanded && !onShorts) MiniPlayer(current)
                    val route = nav.currentBackStackEntryAsState().value?.destination?.route
                    VBottomBar(route) { r ->
                        // Primero cerramos las pantallas abiertas encima (Configuración, canal, búsqueda…),
                        // así no quedan guardadas dentro de la pestaña y no "reaparecen" al tocarla
                        val tabRoutes = navTabs.map { it.route }
                        while (nav.currentBackStackEntry?.destination?.route !in tabRoutes &&
                            nav.previousBackStackEntry != null
                        ) {
                            if (!nav.popBackStack()) break
                        }
                        if (nav.currentBackStackEntry?.destination?.route == r) return@VBottomBar
                        if (r == "home") {
                            if (!nav.popBackStack("home", inclusive = false)) nav.navigate("home")
                            return@VBottomBar
                        }
                        nav.navigate(r) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                composable("home") {
                    HomeScreen(openVideo, openChannel, openSearch, openSettings, onMore = { id, title ->
                        nav.navigate("more/" + Uri.encode(id) + "/" + Uri.encode(title))
                    })
                }
                composable("more/{id}/{title}") { entry ->
                    MoreScreen(
                        chipId = Uri.decode(entry.arguments?.getString("id") ?: "todo"),
                        title = Uri.decode(entry.arguments?.getString("title") ?: "Tendencias"),
                        onBack = { nav.popBackStack() },
                        onVideo = openVideo,
                        onChannel = openChannel,
                    )
                }
                composable("shorts") { ShortsScreen(onChannel = openChannel) }
                composable("subs") { SubscriptionsScreen(openVideo, openChannel, openSearch) }
                composable("library") {
                    LibraryScreen(
                        openVideo,
                        onOpenList = { if (it == "downloads") nav.navigate("downloads") else nav.navigate("list/$it") },
                        onSearch = openSearch,
                        onSettings = openSettings,
                    )
                }
                composable("settings") {
                    SettingsScreen(onBack = { nav.popBackStack() }, onDownloads = { nav.navigate("downloads") })
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

        // Bienvenida al abrir la app
        AnimatedVisibility(
            visible = showSplash,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(450)),
        ) { SplashScreen() }
    }
}

/** La bienvenida se muestra una sola vez por apertura de la app. */
private var splashShown = false
