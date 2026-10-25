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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytube.app.data.VideoItem
import com.ytube.app.data.YoutubeRepository

/** 播放列表详情页：登录后从媒体库点进来，列出该列表全部视频，点开即播 */
@Composable
fun PlaylistScreen(
    playlistId: String,
    title: String,
    onVideoClick: (VideoItem) -> Unit
) {
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        try {
            videos = YoutubeRepository.getPlaylistVideos(playlistId)
            if (videos.isEmpty()) error = "该播放列表暂无视频"
        } catch (e: Exception) {
            error = "加载失败：${e.message}"
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(24.dp)
            )
            error != null -> Text(error!!, modifier = Modifier.padding(16.dp))
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(videos) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoClick(item) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
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
                                overflow = TextOverflow.Ellipsis
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
            }
        }
    }
}
