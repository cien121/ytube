package com.ytube.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytube.app.auth.MicroGAuthManager
import com.ytube.app.auth.SubscriptionChannel
import com.ytube.app.auth.YoutubeApiClient
import com.ytube.app.data.LocalChannel
import com.ytube.app.data.LocalStore
import kotlinx.coroutines.launch

/**
 * 订阅页：
 * - 登录后（MicroG）：官方 Data API 的订阅频道列表，点进频道看视频
 * - 未登录：本地订阅（播放页点的订阅，无需登录）+ 登录引导
 */
@Composable
fun SubscriptionsScreen(
    onChannelClick: (url: String, name: String, avatar: String) -> Unit,
    onLoginClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { MicroGAuthManager(context) }
    val apiClient = remember { YoutubeApiClient(authManager.tokenStore) }
    val store = remember { LocalStore(context) }

    var loggedIn by remember { mutableStateOf(authManager.tokenStore.isLoggedIn) }
    var apiSubs by remember { mutableStateOf<List<SubscriptionChannel>>(emptyList()) }
    var localSubs by remember { mutableStateOf<List<LocalChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        localSubs = store.getLocalSubscriptions()
        loggedIn = authManager.tokenStore.isLoggedIn
        if (loggedIn && authManager.tokenStore.hasValidApiToken) {
            loading = true
            scope.launch {
                apiSubs = apiClient.getSubscriptions()
                loading = false
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (!loggedIn) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "登录谷歌账号后，这里显示你的 YouTube 订阅",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onLoginClick) { Text("去登录") }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "不登录也能用：在播放页点「订阅」，频道会保存在本地订阅里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (loading) {
            item {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        if (apiSubs.isNotEmpty()) {
            item {
                Text(
                    "YouTube 订阅",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(apiSubs) { ch ->
                ChannelRow(
                    name = ch.title,
                    avatarUrl = ch.thumbnailUrl,
                    onClick = {
                        onChannelClick(
                            "https://www.youtube.com/channel/${ch.channelId}",
                            ch.title,
                            ch.thumbnailUrl
                        )
                    }
                )
            }
        }
        if (localSubs.isNotEmpty()) {
            item {
                Text(
                    "本地订阅",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(localSubs) { ch ->
                ChannelRow(
                    name = ch.name,
                    avatarUrl = ch.avatarUrl,
                    onClick = { onChannelClick(ch.channelUrl, ch.name, ch.avatarUrl) }
                )
            }
        }
        if (loggedIn && apiSubs.isEmpty() && localSubs.isEmpty() && !loading) {
            item {
                Text(
                    "还没有订阅。去首页或播放页订阅喜欢的频道吧。",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/** 频道行：头像 + 名字（头像缺失时用首字母圆） */
@Composable
fun ChannelRow(name: String, avatarUrl: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
            )
        } else {
            LetterAvatar(name = name, size = 40.dp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}
