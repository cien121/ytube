package com.ytube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.ytube.app.data.QualityOption
import com.ytube.app.data.VideoItem
import com.ytube.app.data.YoutubeRepository

/**
 * 播放页。
 * 画质：默认取该视频最高分辨率（qualities 首位，4K 优先）；点「画质」按钮可手动切换
 * 全部可用档位；切换时保留当前播放进度。
 * ExoPlayer 不设任何分辨率上限：不用 setMaxVideoSize、不约束 TrackSelector，
 * 有多高播多高。
 */
@Composable
fun PlayerScreen(video: VideoItem) {
    val context = LocalContext.current
    var qualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selected by remember { mutableStateOf<QualityOption?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var resumePositionMs by remember { mutableStateOf(0L) }

    // 单个播放器实例，切画质只换 MediaSource，不重建播放器
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // 首次取全量画质档，默认最高
    LaunchedEffect(video.url) {
        try {
            val options = YoutubeRepository.resolveStreamOptions(video.url)
            qualities = options.qualities
            selected = options.qualities.firstOrNull()
        } catch (e: Exception) {
            error = "取流失败：${e.message}"
        } finally {
            loading = false
        }
    }

    // 画质变化时换源并回到切换前进度
    LaunchedEffect(selected) {
        val q = selected ?: return@LaunchedEffect
        player.setMediaSource(buildSource(context, q))
        player.prepare()
        player.seekTo(resumePositionMs)
        player.playWhenReady = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = video.title,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            Box {
                TextButton(
                    onClick = { showMenu = true },
                    enabled = qualities.isNotEmpty()
                ) {
                    Text("画质 ${selected?.label ?: "…"}")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    qualities.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(if (q == selected) "✓ ${q.label}" else q.label) },
                            onClick = {
                                resumePositionMs = player.currentPosition
                                selected = q
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            error != null -> Text(error!!, modifier = Modifier.padding(12.dp))
            qualities.isEmpty() -> Text("该视频暂无可用画质", modifier = Modifier.padding(12.dp))
        }
    }
}

private fun buildSource(
    context: android.content.Context,
    quality: QualityOption
): androidx.media3.exoplayer.source.MediaSource {
    val dataSourceFactory = DefaultDataSource.Factory(context)
    val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(quality.videoUrl))
    // progressive 流音画合一直接播；video-only 流需合并一条音频流
    val audioUrl = quality.audioUrl ?: return videoSource
    val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(audioUrl))
    return MergingMediaSource(videoSource, audioSource)
}
