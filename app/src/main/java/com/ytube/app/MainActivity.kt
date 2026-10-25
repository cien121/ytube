package com.ytube.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.ytube.app.data.VideoItem
import com.ytube.app.player.PlayerHolder
import com.ytube.app.ui.ChannelScreen
import com.ytube.app.ui.HomeScreen
import com.ytube.app.ui.LibraryScreen
import com.ytube.app.ui.PlayerScreen
import com.ytube.app.ui.PlaylistScreen
import com.ytube.app.ui.SearchScreen
import com.ytube.app.ui.SettingsScreen
import com.ytube.app.ui.SubscriptionsScreen
import com.ytube.app.ui.YTubeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect

private enum class Tab(val label: String) {
    Home("首页"),
    Search("搜索"),
    Subscriptions("订阅"),
    Library("媒体库")
}

/** 覆盖层（非 tab 的全屏页）：播放页、频道页、播放列表页、账号页 */
private sealed interface Overlay {
    data class Player(val video: VideoItem) : Overlay
    data class Channel(val url: String, val name: String, val avatar: String) : Overlay
    data class Playlist(val id: String, val title: String) : Overlay
    data object Settings : Overlay
}

class MainActivity : ComponentActivity() {
    companion object {
        /** 通知栏点回播放页时带的标记 */
        const val EXTRA_OPEN_PLAYER = "open_player"
    }

    private val openPlayerRequests = MutableStateFlow(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            openPlayerRequests.value += 1
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YTubeTheme {
                val context = LocalContext.current
                var tab by remember { mutableStateOf(Tab.Home) }
                var overlay by remember { mutableStateOf<Overlay?>(null) }
                var currentVideo by remember {
                    mutableStateOf<VideoItem?>(PlayerHolder.currentVideo.value)
                }
                var isPlaying by remember {
                    mutableStateOf(PlayerHolder.isPlaying.value)
                }

                // 通知权限（Android 13+）：后台播放通知栏需要，启动时问一次
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // 通知栏点回来：打开当前播放的视频页
                    if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
                        PlayerHolder.currentVideo.value?.let {
                            overlay = Overlay.Player(it)
                        }
                    }
                    launch {
                        PlayerHolder.currentVideo.collect { currentVideo = it }
                    }
                    launch {
                        PlayerHolder.isPlaying.collect { isPlaying = it }
                    }
                    openPlayerRequests.collect { n ->
                        if (n > 0) {
                            PlayerHolder.currentVideo.value?.let {
                                overlay = Overlay.Player(it)
                            }
                        }
                    }
                }

                BackHandler(enabled = overlay != null) { overlay = null }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("YTube") },
                            navigationIcon = {
                                if (overlay != null) {
                                    IconButton(onClick = { overlay = null }) {
                                        Icon(
                                            Icons.Filled.ArrowBack,
                                            contentDescription = "返回"
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(onClick = { overlay = Overlay.Settings }) {
                                    Icon(
                                        Icons.Filled.AccountCircle,
                                        contentDescription = "账号"
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        // 覆盖层全屏时隐藏底部导航；tab 页显示迷你播放条 + 导航
                        if (overlay == null) {
                            Column {
                                val video = currentVideo
                                if (video != null) {
                                    MiniPlayerBar(
                                        video = video,
                                        isPlaying = isPlaying,
                                        onClick = { overlay = Overlay.Player(video) },
                                        onPlayPause = {
                                            PlayerHolder.togglePlayPause(context)
                                        },
                                        onClose = { PlayerHolder.stop(context) }
                                    )
                                }
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = tab == Tab.Home,
                                        onClick = { tab = Tab.Home },
                                        icon = {
                                            Icon(Icons.Filled.Home, contentDescription = null)
                                        },
                                        label = { Text("首页") }
                                    )
                                    NavigationBarItem(
                                        selected = tab == Tab.Search,
                                        onClick = { tab = Tab.Search },
                                        icon = {
                                            Icon(Icons.Filled.Search, contentDescription = null)
                                        },
                                        label = { Text("搜索") }
                                    )
                                    NavigationBarItem(
                                        selected = tab == Tab.Subscriptions,
                                        onClick = { tab = Tab.Subscriptions },
                                        icon = {
                                            Icon(
                                                Icons.Filled.Subscriptions,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text("订阅") }
                                    )
                                    NavigationBarItem(
                                        selected = tab == Tab.Library,
                                        onClick = { tab = Tab.Library },
                                        icon = {
                                            Icon(
                                                Icons.Filled.VideoLibrary,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text("媒体库") }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (val o = overlay) {
                            is Overlay.Player -> PlayerScreen(video = o.video)
                            is Overlay.Channel -> ChannelScreen(
                                channelUrl = o.url,
                                initialName = o.name,
                                initialAvatar = o.avatar,
                                onVideoClick = { overlay = Overlay.Player(it) }
                            )
                            is Overlay.Playlist -> PlaylistScreen(
                                playlistId = o.id,
                                title = o.title,
                                onVideoClick = { overlay = Overlay.Player(it) }
                            )
                            is Overlay.Settings -> SettingsScreen()
                            null -> when (tab) {
                                Tab.Home -> HomeScreen(
                                    onVideoClick = { overlay = Overlay.Player(it) },
                                    onSearchClick = { tab = Tab.Search }
                                )
                                Tab.Search -> SearchScreen(
                                    onVideoClick = { overlay = Overlay.Player(it) },
                                    onChannelClick = { url, name, avatar ->
                                        overlay = Overlay.Channel(url, name, avatar)
                                    }
                                )
                                Tab.Subscriptions -> SubscriptionsScreen(
                                    onChannelClick = { url, name, avatar ->
                                        overlay = Overlay.Channel(url, name, avatar)
                                    },
                                    onLoginClick = { overlay = Overlay.Settings }
                                )
                                Tab.Library -> LibraryScreen(
                                    onVideoClick = { overlay = Overlay.Player(it) },
                                    onPlaylistClick = { id, title ->
                                        overlay = Overlay.Playlist(id, title)
                                    },
                                    onLoginClick = { overlay = Overlay.Settings }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 迷你播放条（官方同款）：切出播放页后底部常驻，显示当前视频 + 播放/暂停 + 关闭。
 * 点条子回播放页；点关闭彻底停止（通知栏同时消失）。
 */
@Composable
private fun MiniPlayerBar(
    video: VideoItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                video.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                video.uploaderName,
                maxLines = 1,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放"
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "停止")
        }
    }
}
