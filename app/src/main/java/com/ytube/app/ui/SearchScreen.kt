package com.ytube.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytube.app.data.ChannelItem
import com.ytube.app.data.VideoItem
import com.ytube.app.data.YoutubeRepository
import kotlinx.coroutines.launch

/**
 * 搜索页（官方同款分类）：
 * - 视频 tab：搜视频，标题/封面/时长/UP 主/播放量，点开即播
 * - 频道 tab：搜频道，头像/名字/订阅数，点进频道页
 */
@Composable
fun SearchScreen(
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (url: String, name: String, avatar: String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var videoResults by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var channelResults by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        loading = true
        error = null
        scope.launch {
            try {
                if (tab == 0) {
                    videoResults = YoutubeRepository.searchVideos(q)
                    if (videoResults.isEmpty()) error = "没有搜到视频，换个关键词试试"
                } else {
                    channelResults = YoutubeRepository.searchChannels(q)
                    if (channelResults.isEmpty()) error = "没有搜到频道，换个关键词试试"
                }
            } catch (e: Exception) {
                error = "搜索失败：${e.message}"
            } finally {
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索 YouTube") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { doSearch() }, enabled = !loading) {
                Text("搜索")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("视频") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("频道") })
        }
        Spacer(modifier = Modifier.height(8.dp))
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            error != null -> Text(error!!, modifier = Modifier.align(Alignment.CenterHorizontally))
            tab == 0 && videoResults.isEmpty() -> Text(
                "输入关键词搜索视频，点开即播，无广告",
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            tab == 1 && channelResults.isEmpty() -> Text(
                "输入关键词搜索频道，点进频道看全部视频",
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            tab == 0 -> LazyColumn {
                items(videoResults) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoClick(item) }
                            .padding(vertical = 8.dp)
                    ) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(160.dp)
                                .aspectRatio(16f / 9f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.uploaderName, maxLines = 1)
                            Text(
                                "${formatDuration(item.durationSeconds)} · " +
                                    "${formatViews(item.viewCount)}次观看",
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            else -> LazyColumn {
                items(channelResults) { item ->
                    ChannelRow(
                        name = item.name,
                        avatarUrl = item.avatarUrl,
                        onClick = {
                            onChannelClick(item.url, item.name, item.avatarUrl)
                        }
                    )
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds < 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatViews(views: Long): String {
    if (views < 0) return ""
    return when {
        views >= 100_000_000 -> "%.1f亿".format(views / 100_000_000.0)
        views >= 10_000 -> "%.1f万".format(views / 10_000.0)
        else -> views.toString()
    }
}
