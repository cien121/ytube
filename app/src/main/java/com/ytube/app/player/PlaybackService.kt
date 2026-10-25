package com.ytube.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ytube.app.MainActivity

/**
 * 后台播放服务（官方 Premium 功能，这里免费）：
 * MediaSessionService 会在播放时自动拉起前台通知（标题、播放/暂停、点通知回播放页），
 * 切后台、锁屏都能继续听；用户在迷你条或通知栏点停止时，PlayerHolder.stop() 停掉服务。
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.getPlayer(this)
        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val activity = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(activity)
            .build()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
