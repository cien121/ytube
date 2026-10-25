package com.ytube.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.ytube.app.data.DownloadEntry
import com.ytube.app.data.DownloadsManager
import com.ytube.app.data.LocalStore
import com.ytube.app.data.QualityOption
import com.ytube.app.data.StreamOptions
import com.ytube.app.data.VideoItem
import com.ytube.app.data.YoutubeRepository
import com.ytube.app.player.PlayerHolder
import kotlinx.coroutines.launch

private sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data object Downloaded : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * 播放页（对标官方体验）：
 * - 视频 + 标题 + UP 主行（头像、名字、订阅按钮，本地订阅无需登录）
 * - 操作行：点赞（本地）、下载（离线免费）、评论（真实评论区）、画质（默认最高）
 * - 简介可展开；离线视频直接播本地文件、不再取流
 * - 播放器是全局共享的 PlayerHolder：离开本页、切后台，音频继续播（通知栏/迷你条可控）
 */
@Composable
fun PlayerScreen(video: VideoItem) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalStore(context) }
    val player = remember { PlayerHolder.getPlayer(context) }

    var qualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selected by remember { mutableStateOf<QualityOption?>(null) }
    var details by remember { mutableStateOf<StreamOptions?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var descExpanded by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(store.isLiked(video.url)) }
    var subscribed by remember {
        mutableStateOf(
            video.channelUrl.isNotBlank() && store.isLocalSubscribed(video.channelUrl)
        )
    }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var offlineEntry by remember { mutableStateOf<DownloadEntry?>(null) }

    // 离开播放页只 detach 画面、不释放播放器（后台继续播）；记下进度供重进续播
    DisposableEffect(Unit) {
        onDispose {
            try {
                PlayerHolder.lastPositionMs = player.currentPosition
            } catch (e: Exception) {
                // 忽略
            }
        }
    }

    LaunchedEffect(video.url) {
        store.addHistory(video)
        val entry = store.getDownloads().firstOrNull { it.videoUrl == video.url }
        offlineEntry = entry
        if (entry != null) {
            // 离线模式：直接播本地文件
            val resume =
                if (PlayerHolder.currentVideo.value?.url == video.url) {
                    PlayerHolder.lastPositionMs
                } else {
                    0L
                }
            PlayerHolder.playLocal(
                context, video, entry.videoPath, entry.audioPath, resume
            )
            downloadState = DownloadState.Downloaded
            loading = false
            return@LaunchedEffect
        }
        try {
            val options = YoutubeRepository.resolveStreamOptions(video.url)
            details = options
            qualities = options.qualities
            val top = options.qualities.firstOrNull()
            selected = top
            if (top != null) {
                val resume =
                    if (PlayerHolder.currentVideo.value?.url == video.url) {
                        PlayerHolder.lastPositionMs
                    } else {
                        0L
                    }
                PlayerHolder.play(context, video, top, resume)
            }
        } catch (e: Exception) {
            error = "取流失败：${e.message}"
        } finally {
            loading = false
        }
    }

    fun changeQuality(q: QualityOption) {
        try {
            PlayerHolder.lastPositionMs = player.currentPosition
        } catch (e: Exception) {
            // 忽略
        }
        selected = q
        PlayerHolder.play(context, video, q, PlayerHolder.lastPositionMs)
    }

    fun startDownload() {
        val q = selected ?: return
        downloadState = DownloadState.Downloading(0f)
        scope.launch {
            try {
                DownloadsManager.download(context, video, q) { d, t ->
                    if (t > 0) downloadState = DownloadState.Downloading(d.toFloat() / t)
                }
                downloadState = DownloadState.Downloaded
            } catch (e: Exception) {
                downloadState = DownloadState.Failed(e.message ?: "下载失败")
            }
        }
    }

    val uploaderName = details?.uploaderName?.takeIf { it.isNotBlank() } ?: video.uploaderName
    val likeCount = details?.likeCount ?: -1

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize()
            )
        }
        // 标题
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        // UP 主行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LetterAvatar(name = uploaderName, size = 36.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(uploaderName, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                val views = details?.viewCount?.takeIf { it >= 0 } ?: video.viewCount
                Text(
                    "${formatViews(views)}次观看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (video.channelUrl.isNotBlank()) {
                OutlinedButton(onClick = {
                    subscribed = store.toggleLocalSubscription(
                        video.channelUrl, uploaderName, ""
                    )
                }) {
                    Text(if (subscribed) "已订阅" else "订阅")
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 操作行：点赞 / 下载 / 评论 / 画质（全部免费，无付费墙）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                liked = store.toggleLike(video)
            }) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = "点赞",
                    tint = if (liked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                if (likeCount >= 0) formatViews(likeCount) else "",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (offlineEntry != null) {
                Text(
                    "离线视频 · ${offlineEntry!!.qualityLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                when (val ds = downloadState) {
                    is DownloadState.Idle -> TextButton(onClick = { startDownload() }) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("下载")
                    }
                    is DownloadState.Downloading -> TextButton(onClick = {}) {
                        Text("下载中 ${(ds.progress * 100).toInt()}%")
                    }
                    is DownloadState.Downloaded -> TextButton(onClick = {}) {
                        Icon(Icons.Filled.DownloadDone, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("已下载")
                    }
                    is DownloadState.Failed -> TextButton(onClick = { startDownload() }) {
                        Text("下载失败，重试")
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = { showComments = true }) {
                Icon(Icons.Filled.Comment, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("评论")
            }
            Spacer(modifier = Modifier.weight(1f))
            if (offlineEntry == null) {
                Box {
                    TextButton(
                        onClick = { showQualityMenu = true },
                        enabled = qualities.isNotEmpty()
                    ) {
                        Text("画质 ${selected?.label ?: "…"}")
                    }
                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false }
                    ) {
                        qualities.forEach { q ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (q == selected) "✓ ${q.label}" else q.label)
                                },
                                onClick = {
                                    changeQuality(q)
                                    showQualityMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
        // 简介（可展开）
        val description = details?.description ?: ""
        if (description.isNotBlank()) {
            Text(
                text = description,
                maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { descExpanded = !descExpanded }
            )
        }
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            error != null -> Text(error!!, modifier = Modifier.padding(12.dp))
            qualities.isEmpty() && offlineEntry == null -> Text(
                "该视频暂无可用画质",
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    if (showComments) {
        CommentsSheet(
            videoUrl = video.url,
            onDismiss = { showComments = false }
        )
    }
}
