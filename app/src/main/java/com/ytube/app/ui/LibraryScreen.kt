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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytube.app.auth.MicroGAuthManager
import com.ytube.app.auth.PlaylistItem
import com.ytube.app.auth.YoutubeApiClient
import com.ytube.app.data.DownloadEntry
import com.ytube.app.data.DownloadsManager
import com.ytube.app.data.LocalStore
import com.ytube.app.data.VideoItem
import kotlinx.coroutines.launch

/**
 * 媒体库（官方同款分区，全部免费）：
 * - 下载：离线视频，点开直接播本地文件，可删除
 * - 观看历史：本地记录，可一键清空
 * - 喜欢的视频：播放页点赞的本地收藏
 * - 我的播放列表：登录后（MicroG）从官方 Data API 拉取
 */
@Composable
fun LibraryScreen(
    onVideoClick: (VideoItem) -> Unit,
    onPlaylistClick: (playlistId: String, title: String) -> Unit,
    onLoginClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalStore(context) }
    val authManager = remember { MicroGAuthManager(context) }
    val apiClient = remember { YoutubeApiClient(authManager.tokenStore) }

    var loggedIn by remember { mutableStateOf(authManager.tokenStore.isLoggedIn) }
    var email by remember { mutableStateOf(authManager.tokenStore.accountEmail) }
    var history by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var liked by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var downloads by remember { mutableStateOf<List<DownloadEntry>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var loadingPlaylists by remember { mutableStateOf(false) }

    fun reload() {
        history = store.getHistory()
        liked = store.getLikedVideos()
        downloads = store.getDownloads()
        loggedIn = authManager.tokenStore.isLoggedIn
        email = authManager.tokenStore.accountEmail
    }

    LaunchedEffect(Unit) {
        reload()
        if (loggedIn && authManager.tokenStore.hasValidApiToken) {
            loadingPlaylists = true
            scope.launch {
                playlists = apiClient.getPlaylists()
                loadingPlaylists = false
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loggedIn) {
                    LetterAvatar(name = email ?: "?", size = 40.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(email ?: "", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "未登录",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onLoginClick) { Text("登录") }
                }
            }
        }
        // 下载
        if (downloads.isNotEmpty()) {
            item {
                SectionTitle("下载")
            }
            items(downloads) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onVideoClick(
                                VideoItem(
                                    title = entry.title,
                                    url = entry.videoUrl,
                                    uploaderName = entry.uploaderName,
                                    durationSeconds = -1,
                                    thumbnailUrl = entry.thumbnailUrl,
                                    viewCount = -1
                                )
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = entry.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(120.dp)
                            .height(68.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "离线 · ${entry.qualityLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        DownloadsManager.delete(context, entry)
                        downloads = store.getDownloads()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除下载")
                    }
                }
            }
        }
        // 观看历史
        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "观看历史",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "清空",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            store.clearHistory()
                            history = emptyList()
                        }
                    )
                }
            }
            items(history) { item ->
                LibraryVideoRow(item = item, onClick = { onVideoClick(item) })
            }
        }
        // 喜欢的视频
        if (liked.isNotEmpty()) {
            item {
                SectionTitle("喜欢的视频")
            }
            items(liked) { item ->
                LibraryVideoRow(item = item, onClick = { onVideoClick(item) })
            }
        }
        // 播放列表（登录后）
        if (loggedIn) {
            item {
                SectionTitle("我的播放列表")
            }
            if (loadingPlaylists) {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            } else if (playlists.isEmpty()) {
                item {
                    Text(
                        "暂无播放列表（或 Data API token 不可用）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            } else {
                items(playlists) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(pl.playlistId, pl.title) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            pl.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${pl.videoCount} 个视频",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (history.isEmpty() && liked.isEmpty() && downloads.isEmpty()) {
            item {
                Text(
                    "媒体库还是空的：看视频会自动记历史，点赞和下载也会出现在这里。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun LibraryVideoRow(item: VideoItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.uploaderName,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
