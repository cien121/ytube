package com.ytube.app.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ytube.app.data.QualityOption
import com.ytube.app.data.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局共享播放器（官方 Premium 的后台播放，这里免费、默认开启）：
 * - 播放器实例常驻，不随播放页销毁而释放；切到首页/搜索/后台，音频继续播
 * - PlaybackService 只负责挂通知栏（MediaSession），播放本身就在这个单例里
 * - 迷你播放条、通知栏、播放页都操作同一个实例，状态天然一致
 */
object PlayerHolder {
    private var player: ExoPlayer? = null

    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    /** 离开播放页时记住的进度，同视频重进时续播 */
    var lastPositionMs: Long = 0L

    fun getPlayer(context: Context): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(context.applicationContext).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                })
            }
        }
        return player!!
    }

    /** 播新视频（或同视频换画质）：换源、准备、播放，并拉起后台服务挂通知 */
    fun play(
        context: Context,
        video: VideoItem,
        quality: QualityOption,
        startPositionMs: Long = 0L
    ) {
        val p = getPlayer(context)
        val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext)
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(quality.videoUrl))
        val source = if (quality.audioUrl != null) {
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(quality.audioUrl))
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }
        p.setMediaSource(source)
        p.prepare()
        p.seekTo(startPositionMs)
        p.playWhenReady = true
        _currentVideo.value = video
        lastPositionMs = startPositionMs
        // 拉起前台服务：切后台也能继续播，通知栏可暂停/返回
        context.startService(Intent(context, PlaybackService::class.java))
    }

    /** 播本地下载文件（离线播放）：audioPath 为空表示音画合一单文件 */
    fun playLocal(
        context: Context,
        video: VideoItem,
        videoPath: String,
        audioPath: String,
        startPositionMs: Long = 0L
    ) {
        val p = getPlayer(context)
        val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext)
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoPath))
        val source = if (audioPath.isNotBlank()) {
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioPath))
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }
        p.setMediaSource(source)
        p.prepare()
        p.seekTo(startPositionMs)
        p.playWhenReady = true
        _currentVideo.value = video
        lastPositionMs = startPositionMs
        context.startService(Intent(context, PlaybackService::class.java))
    }

    fun togglePlayPause(context: Context) {
        val p = getPlayer(context)
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
            context.startService(Intent(context, PlaybackService::class.java))
        }
    }

    /** 彻底停止：停播、清状态、停服务（迷你条和通知同时消失） */
    fun stop(context: Context) {
        player?.pause()
        player?.stop()
        _currentVideo.value = null
        _isPlaying.value = false
        lastPositionMs = 0L
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}
