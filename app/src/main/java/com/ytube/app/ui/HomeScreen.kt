package com.ytube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ytube.app.data.HomeCategory
import com.ytube.app.data.VideoItem
import com.ytube.app.data.YoutubeRepository
import kotlinx.coroutines.launch

/**
 * 首页：打开就有真实内容，不用先搜索。
 * - 顶部搜索入口（点进搜索页）
 * - 分类 chips：全部=YouTube 热门，其余=该分类关键词搜索
 * - 推荐区：横向滚动卡片（取当前内容前 8 条）
 * - 热门/分类视频流：纵向列表，点开即播
 * 全部数据走 NewPipeExtractor，匿名模式无需登录；切换回看过的分类直接读缓存，不重复请求。
 */
@Composable
fun HomeScreen(
    onVideoClick: (VideoItem) -> Unit,
    onSearchClick: () -> Unit
) {
    var selected by remember { mutableStateOf(HomeCategory.ALL) }
    val cache = remember { mutableStateMapOf<HomeCategory, List<VideoItem>>() }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load(category: HomeCategory, force: Boolean = false) {
        if (!force) {
            val cached = cache[category]
            if (cached != null) {
                videos = cached
                error = null
                return
            }
        }
        loading = true
        error = null
        scope.launch {
            try {
                val list = YoutubeRepository.getHomeFeed(category)
                cache[category] = list
                videos = list
                if (list.isEmpty()) error = "没有拿到视频，点刷新重试"
            } catch (e: Exception) {
                error = "加载失败：${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(selected) { load(selected) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { onSearchClick() }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "搜索 YouTube 视频",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // 分类 chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HomeCategory.entries) { cat ->
                FilterChip(
                    selected = selected == cat,
                    onClick = { selected = cat },
                    label = { Text(cat.label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            error != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { load(selected, force = true) }) { Text("重试") }
                }
            }
            videos.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无内容")
            }
            else -> {
                val recommended = videos.take(8)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        SectionHeader(
                            title = "推荐",
                            onRefresh = { load(selected, force = true) }
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recommended) { item ->
                                RecommendCard(item = item, onClick = { onVideoClick(item) })
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item {
                        SectionHeader(
                            title = if (selected == HomeCategory.ALL) "热门视频" else selected.label,
                            onRefresh = null
                        )
                    }
                    items(videos) { item ->
                        HomeVideoRow(item = item, onClick = { onVideoClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onRefresh: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }
    }
}

@Composable
private fun RecommendCard(item: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .clickable { onClick() }
    ) {
        Box {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            DurationBadge(
                text = formatDuration(item.durationSeconds),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "${item.uploaderName} · ${formatViews(item.viewCount)}次观看",
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 官方首页样式的视频卡：通栏 16:9 大图 + 右下角时长角标，下方头像 + 标题 + UP 主/播放量。
 * UP 主头像用首字母圆（不依赖头像地址，任何视频都有）。
 */
@Composable
private fun HomeVideoRow(item: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(bottom = 16.dp)
    ) {
        Box {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            DurationBadge(
                text = formatDuration(item.durationSeconds),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            LetterAvatar(name = item.uploaderName, size = 36.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${item.uploaderName} · ${formatViews(item.viewCount)}次观看",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 时长角标：黑底白字，官方同款位置 */
@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** 首字母头像圆：UP 主名首字 + 红底白字 */
@Composable
fun LetterAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val letter = name.firstOrNull()?.toString()?.uppercase() ?: "?"
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(size)
            .background(
                androidx.compose.ui.graphics.Color(0xFFCC0000),
                androidx.compose.foundation.shape.CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
