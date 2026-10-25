package com.ytube.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地数据存储（全部免费功能，无需登录、无需任何付费）：
 * - 观看历史：最近看过的视频，最多 100 条，按时间倒序
 * - 点赞：本地点赞的视频地址集合
 * - 本地订阅：匿名模式下在播放页点的订阅频道（频道地址+名字+头像）
 * - 下载记录：离线下载的视频文件信息
 * 全部存在普通 SharedPreferences 的 JSON 里，不走账号、不上云。
 */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("ytube_local", Context.MODE_PRIVATE)

    // ---------- 观看历史 ----------

    fun addHistory(video: VideoItem) {
        val list = getHistory().filter { it.url != video.url }.toMutableList()
        list.add(0, video)
        putVideoList(KEY_HISTORY, list.take(100))
    }

    fun getHistory(): List<VideoItem> = getVideoList(KEY_HISTORY)

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    // ---------- 点赞（存完整视频，媒体库可直接显示） ----------

    fun toggleLike(video: VideoItem): Boolean {
        val list = getLikedVideos().toMutableList()
        val existing = list.indexOfFirst { it.url == video.url }
        val liked = if (existing >= 0) {
            list.removeAt(existing)
            false
        } else {
            list.add(0, video)
            true
        }
        putVideoList(KEY_LIKES, list)
        return liked
    }

    fun isLiked(videoUrl: String): Boolean =
        getLikedVideos().any { it.url == videoUrl }

    fun getLikedVideos(): List<VideoItem> = getVideoList(KEY_LIKES)

    // ---------- 本地订阅 ----------

    fun toggleLocalSubscription(channelUrl: String, name: String, avatarUrl: String): Boolean {
        val list = getLocalSubscriptions().toMutableList()
        val existing = list.indexOfFirst { it.channelUrl == channelUrl }
        val subscribed = if (existing >= 0) {
            list.removeAt(existing)
            false
        } else {
            list.add(0, LocalChannel(channelUrl, name, avatarUrl))
            true
        }
        val arr = JSONArray()
        list.forEach { ch ->
            arr.put(JSONObject().apply {
                put("url", ch.channelUrl)
                put("name", ch.name)
                put("avatar", ch.avatarUrl)
            })
        }
        prefs.edit().putString(KEY_LOCAL_SUBS, arr.toString()).apply()
        return subscribed
    }

    fun isLocalSubscribed(channelUrl: String): Boolean =
        getLocalSubscriptions().any { it.channelUrl == channelUrl }

    fun getLocalSubscriptions(): List<LocalChannel> {
        val raw = prefs.getString(KEY_LOCAL_SUBS, null) ?: return emptyList()
        val out = mutableListOf<LocalChannel>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    LocalChannel(
                        channelUrl = o.optString("url"),
                        name = o.optString("name"),
                        avatarUrl = o.optString("avatar")
                    )
                )
            }
        } catch (e: Exception) {
            // 损坏的缓存直接丢弃
        }
        return out
    }

    // ---------- 下载记录 ----------

    fun addDownload(entry: DownloadEntry) {
        val list = getDownloads().filter { it.videoUrl != entry.videoUrl }.toMutableList()
        list.add(0, entry)
        putDownloadList(list)
    }

    fun getDownloads(): List<DownloadEntry> {
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        val out = mutableListOf<DownloadEntry>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    DownloadEntry(
                        videoUrl = o.optString("videoUrl"),
                        title = o.optString("title"),
                        thumbnailUrl = o.optString("thumbnailUrl"),
                        uploaderName = o.optString("uploaderName"),
                        videoPath = o.optString("videoPath"),
                        audioPath = o.optString("audioPath"),
                        qualityLabel = o.optString("qualityLabel"),
                        downloadedAtMillis = o.optLong("downloadedAtMillis")
                    )
                )
            }
        } catch (e: Exception) {
            // 损坏的缓存直接丢弃
        }
        return out
    }

    fun removeDownload(videoUrl: String) {
        putDownloadList(getDownloads().filter { it.videoUrl != videoUrl })
    }

    private fun putDownloadList(list: List<DownloadEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("videoUrl", e.videoUrl)
                put("title", e.title)
                put("thumbnailUrl", e.thumbnailUrl)
                put("uploaderName", e.uploaderName)
                put("videoPath", e.videoPath)
                put("audioPath", e.audioPath)
                put("qualityLabel", e.qualityLabel)
                put("downloadedAtMillis", e.downloadedAtMillis)
            })
        }
        prefs.edit().putString(KEY_DOWNLOADS, arr.toString()).apply()
    }

    // ---------- VideoItem 序列化 ----------

    private fun putVideoList(key: String, list: List<VideoItem>) {
        val arr = JSONArray()
        list.forEach { v ->
            arr.put(JSONObject().apply {
                put("title", v.title)
                put("url", v.url)
                put("uploaderName", v.uploaderName)
                put("durationSeconds", v.durationSeconds)
                put("thumbnailUrl", v.thumbnailUrl)
                put("viewCount", v.viewCount)
                put("channelUrl", v.channelUrl)
            })
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun getVideoList(key: String): List<VideoItem> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val out = mutableListOf<VideoItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    VideoItem(
                        title = o.optString("title"),
                        url = o.optString("url"),
                        uploaderName = o.optString("uploaderName"),
                        durationSeconds = o.optLong("durationSeconds"),
                        thumbnailUrl = o.optString("thumbnailUrl"),
                        viewCount = o.optLong("viewCount"),
                        channelUrl = o.optString("channelUrl")
                    )
                )
            }
        } catch (e: Exception) {
            // 损坏的缓存直接丢弃
        }
        return out
    }

    companion object {
        private const val KEY_HISTORY = "history"
        private const val KEY_LIKES = "likes"
        private const val KEY_LOCAL_SUBS = "local_subs"
        private const val KEY_DOWNLOADS = "downloads"
    }
}

data class LocalChannel(
    val channelUrl: String,
    val name: String,
    val avatarUrl: String
)

/** 一条离线下载记录：videoPath 为视频文件，audioPath 为空表示音画合一单文件 */
data class DownloadEntry(
    val videoUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val videoPath: String,
    val audioPath: String,
    val qualityLabel: String,
    val downloadedAtMillis: Long
)
