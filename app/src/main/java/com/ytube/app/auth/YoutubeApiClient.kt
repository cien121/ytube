package com.ytube.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class SubscriptionChannel(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String
)

data class PlaylistItem(
    val playlistId: String,
    val title: String,
    val videoCount: Long
)

/**
 * YouTube Data API v3 客户端（登录后增强功能）。
 * 用 MicroG 换取的访问 token 调官方接口：订阅列表、播放列表。
 * 未登录或 token 无效时调用方应先引导登录，不静默失败。
 */
class YoutubeApiClient(
    private val tokenStore: TokenStore,
    private val http: OkHttpClient = OkHttpClient()
) {
    private fun authHeader(): String? {
        val token = tokenStore.apiAccessToken ?: return null
        return "Bearer $token"
    }

    /** 我的订阅频道列表 */
    suspend fun getSubscriptions(): List<SubscriptionChannel> = withContext(Dispatchers.IO) {
        val auth = authHeader() ?: return@withContext emptyList()
        val req = Request.Builder()
            .url("https://www.googleapis.com/youtube/v3/subscriptions" +
                "?part=snippet&mine=true&maxResults=50")
            .header("Authorization", auth)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val items = JSONObject(resp.body!!.string()).optJSONArray("items")
                ?: return@withContext emptyList()
            (0 until items.length()).map { i ->
                val snippet = items.getJSONObject(i).getJSONObject("snippet")
                val resourceId = snippet.getJSONObject("resourceId")
                SubscriptionChannel(
                    channelId = resourceId.optString("channelId"),
                    title = snippet.optString("title"),
                    thumbnailUrl = snippet.optJSONObject("thumbnails")
                        ?.optJSONObject("default")?.optString("url") ?: ""
                )
            }
        }
    }

    /** 我的播放列表 */
    suspend fun getPlaylists(): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val auth = authHeader() ?: return@withContext emptyList()
        val req = Request.Builder()
            .url("https://www.googleapis.com/youtube/v3/playlists" +
                "?part=snippet,contentDetails&mine=true&maxResults=50")
            .header("Authorization", auth)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val items = JSONObject(resp.body!!.string()).optJSONArray("items")
                ?: return@withContext emptyList()
            (0 until items.length()).map { i ->
                val obj = items.getJSONObject(i)
                PlaylistItem(
                    playlistId = obj.optString("id"),
                    title = obj.getJSONObject("snippet").optString("title"),
                    videoCount = obj.optJSONObject("contentDetails")?.optLong("itemCount") ?: 0L
                )
            }
        }
    }
}
