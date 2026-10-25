package com.ytube.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ytube.app.data.CommentItem
import com.ytube.app.data.YoutubeRepository

/**
 * 评论区（底部弹出层）：真实评论数据，无需登录。
 * 评论不可关闭时（如 UP 主关闭评论）显示提示，不崩溃。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(videoUrl: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var comments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoUrl) {
        try {
            comments = YoutubeRepository.getComments(videoUrl)
            if (comments.isEmpty()) error = "暂无评论"
        } catch (e: Exception) {
            error = "评论加载失败：${e.message}"
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            "评论",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(24.dp)
            )
            error != null -> Text(
                error!!,
                modifier = Modifier.padding(16.dp)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(comments) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        LetterAvatar(name = item.author, size = 32.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.likeCount >= 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "👍 ${formatViews(item.likeCount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(item.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
