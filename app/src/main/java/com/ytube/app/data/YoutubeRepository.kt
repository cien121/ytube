package com.ytube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class VideoItem(
    val title: String,
    val url: String,
    val uploaderName: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val viewCount: Long
)

/**
 * 一个可选画质档。
 * @param label 显示用，如 2160p / 1080p60 / 720p
 * @param resolutionPx 数值分辨率，用于排序（如 2160、1080）
 * @param videoUrl 视频流地址
 * @param audioUrl 音频流地址；null 表示 progressive 流（音画合一，一个 URL 就能播）
 */
data class QualityOption(
    val label: String,
    val resolutionPx: Int,
    val videoUrl: String,
    val audioUrl: String?
)

data class StreamOptions(
    val title: String,
    /** 按分辨率从高到低排好，首位即默认最高画质 */
    val qualities: List<QualityOption>
)

/**
 * YouTube 公开数据仓库（NewPipeExtractor，无需登录、无需 API key）。
 * 未登录时全部功能走这里；登录只是增强（订阅/播放列表），不是前置条件。
 */
object YoutubeRepository {
    private val service = ServiceList.YouTube

    /** 搜索视频：返回标题、封面、时长、UP 主等 */
    suspend fun searchVideos(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        val info = SearchInfo.getInfo(service, handler)
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                VideoItem(
                    title = item.name,
                    url = item.url,
                    uploaderName = item.uploaderName ?: "",
                    durationSeconds = item.duration,
                    thumbnailUrl = item.thumbnails.maxByOrNull { it.height * it.width }?.url ?: "",
                    viewCount = item.viewCount
                )
            }
    }

    /**
     * 取全量可播放画质（不要只拿默认档）：
     * - videoOnlyStreams：自适应视频流，最高可到 2160p/4K（需另配一条音频流）
     * - videoStreams：progressive 音画合一流（一般最高 720p，一个 URL 直接播）
     * 按分辨率去重（同分辨率优先 progressive，单源更稳），从高到低排序。
     * 调用方默认取 qualities.first() 即最高画质。
     */
    suspend fun resolveStreamOptions(url: String): StreamOptions =
        withContext(Dispatchers.IO) {
            val info = StreamInfo.getInfo(service, url)
            val bestAudio = info.audioStreams.maxByOrNull { it.averageBitrate }?.content

            val byResolution = linkedMapOf<Int, QualityOption>()
            // 先放 progressive（单源优先），再用更高清的 video-only 补位
            info.videoStreams.forEach { stream ->
                val res = parseResolution(stream.resolution)
                if (res > 0) {
                    byResolution[res] = QualityOption(
                        label = stream.resolution,
                        resolutionPx = res,
                        videoUrl = stream.content,
                        audioUrl = null
                    )
                }
            }
            info.videoOnlyStreams.forEach { stream ->
                val res = parseResolution(stream.resolution)
                if (res > 0 && !byResolution.containsKey(res)) {
                    byResolution[res] = QualityOption(
                        label = stream.resolution,
                        resolutionPx = res,
                        videoUrl = stream.content,
                        audioUrl = bestAudio
                    )
                }
            }
            // 1080p 以上只有 video-only；若某高清档只有 video-only 且无音频则照常保留
            //（播放器用 MergingMediaSource 合并；极端情况下 bestAudio 为 null 时仍可播纯画面）
            val qualities = byResolution.values.sortedByDescending { it.resolutionPx }
            if (qualities.isEmpty()) throw IllegalStateException("no playable stream")
            StreamOptions(title = info.name, qualities = qualities)
        }

    /** 兼容旧调用：直接返回最高画质的可播流 */
    suspend fun resolveStream(url: String): PlayableStream {
        val options = resolveStreamOptions(url)
        val top = options.qualities.first()
        return PlayableStream(options.title, top.videoUrl, top.audioUrl)
    }

    private fun parseResolution(resolution: String): Int {
        // "720p" -> 720, "1080p60" -> 1080, "2160p" -> 2160
        return resolution.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
    }
}

data class PlayableStream(
    val title: String,
    val videoUrl: String,
    val audioUrl: String?
)
