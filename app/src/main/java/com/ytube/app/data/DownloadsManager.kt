package com.ytube.app.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 离线下载（官方 Premium 收费功能，这里默认免费、无任何付费墙）：
 * - 优先下载当前选择的画质档；video-only 档同时下载最高码率音频，离线播放时用
 *   MergingMediaSource 合并（和在线播放同一套逻辑，画质不打折）
 * - progressive 档（音画合一）只下一个文件
 * - 文件存在应用私有目录 files/downloads/<url md5>/，卸载即清，不污染相册
 * - 下载失败自动清理半截文件，不留坏记录
 */
object DownloadsManager {
    private val http = OkHttpClient()

    /**
     * @param onProgress 已下载字节数 / 总字节数（总字节未知时 total = -1）
     * @return 成功返回 DownloadEntry 并已写入 LocalStore；失败抛异常
     */
    suspend fun download(
        context: Context,
        video: VideoItem,
        quality: QualityOption,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): DownloadEntry = withContext(Dispatchers.IO) {
        val store = LocalStore(context)
        val dir = File(context.filesDir, "downloads/${md5(video.url)}")
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        try {
            val videoFile = File(dir, "video.mp4")
            downloadToFile(quality.videoUrl, videoFile) { d, t -> onProgress(d, t) }
            var audioPath = ""
            val audioUrl = quality.audioUrl
            if (audioUrl != null) {
                val audioFile = File(dir, "audio.m4a")
                downloadToFile(audioUrl, audioFile) { _, _ -> }
                audioPath = audioFile.absolutePath
            }
            val entry = DownloadEntry(
                videoUrl = video.url,
                title = video.title,
                thumbnailUrl = video.thumbnailUrl,
                uploaderName = video.uploaderName,
                videoPath = videoFile.absolutePath,
                audioPath = audioPath,
                qualityLabel = quality.label,
                downloadedAtMillis = System.currentTimeMillis()
            )
            store.addDownload(entry)
            entry
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    fun delete(context: Context, entry: DownloadEntry) {
        try {
            File(entry.videoPath).parentFile?.deleteRecursively()
        } catch (e: Exception) {
            // 忽略文件删除失败
        }
        LocalStore(context).removeDownload(entry.videoUrl)
    }

    private fun downloadToFile(
        url: String,
        outFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        val req = Request.Builder().url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载失败：HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("下载失败：空响应")
            val total = body.contentLength()
            var downloaded = 0L
            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }
        }
    }

    private fun md5(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
