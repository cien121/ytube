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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytube.app.data.ChannelPage
import com.ytube.app.data.LocalStore
import com.ytube.app.data.VideoItem
import com.ytube.app.data.YoutubeRepository

/**
 * 频道页：头像、名字、订阅数、该频道视频列表，点开即播。
 * 订阅按钮是本地订阅（无需登录），和播放页的订阅同一个数据。
 */
@Composable
fun ChannelScreen(
    channelUrl: String,
    initialName: String,
    initialAvatar: String,
    onVideoClick: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var page by remember { mutableStateOf<ChannelPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var subscribed by remember {
        mutableStateOf(store.isLocalSubscribed(channelUrl))
    }

    LaunchedEffect(channelUrl) {
        try {
            page = YoutubeRepository.getChannelPage(channelUrl)
        } catch (e: Exception) {
            error = "频道加载失败：${e.message}"
        } finally {
            loading = false
        }
    }

    val name = page?.name?.takeIf { it.isNotBlank() } ?: initialName

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (initialAvatar.isNotBlank()) {
                AsyncImage(
                    model = initialAvatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(56.dp)
                        .height(56.dp)
                )
            } else {
                LetterAvatar(name = name, size = 56.dp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                val subs = page?.subscriberCount ?: -1
                if (subs >= 0) {
                    Text(
                        "${formatViews(subs)}位订阅者",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(onClick = {
                subscribed = store.toggleLocalSubscription(channelUrl, name, initialAvatar)
            }) {
                Text(if (subscribed) "已订阅" else "订阅")
            }
        }
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(24.dp)
            )
            error != null -> Text(error!!, modifier = Modifier.padding(16.dp))
            page == null || page!!.videos.isEmpty() -> Text(
                "该频道暂无视频",
                modifier = Modifier.padding(16.dp)
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(page!!.videos) { item ->
                    ChannelVideoRow(item = item, onClick = { onVideoClick(item) })
                }
            }
        }
    }
}

@Composable
private fun ChannelVideoRow(item: VideoItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${formatDuration(item.durationSeconds)} · " +
                    "${formatViews(item.viewCount)}次观看",
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
