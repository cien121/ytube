package com.ytube.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ytube.app.auth.MicroGAuthManager
import com.ytube.app.auth.MicroGDetector
import com.ytube.app.auth.PlaylistItem
import com.ytube.app.auth.SubscriptionChannel
import com.ytube.app.auth.YoutubeApiClient
import kotlinx.coroutines.launch

/**
 * 设置 / 账号页（MicroG 登录占位版）：
 * - 检测 MicroG 是否安装，未装提示先装，匿名模式照常用
 * - 已装则走标准 Google Sign-In API 登录（MicroG 原生响应，无 WebView、无网页 OAuth）
 * - 登录后展示账号，并拉取订阅 / 播放列表（token 有效时）
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { MicroGAuthManager(context) }
    val apiClient = remember { YoutubeApiClient(authManager.tokenStore) }

    var microgPackage by remember { mutableStateOf<String?>(null) }
    var loggedIn by remember { mutableStateOf(authManager.tokenStore.isLoggedIn) }
    var email by remember { mutableStateOf(authManager.tokenStore.accountEmail) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var subscriptions by remember { mutableStateOf<List<SubscriptionChannel>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        microgPackage = MicroGDetector.installedPackage(context)
    }

    fun loadAccountData() {
        if (!authManager.tokenStore.hasValidApiToken) {
            message = "已登录，但 Data API token 不可用，订阅列表暂不可用"
            return
        }
        scope.launch {
            subscriptions = apiClient.getSubscriptions()
            playlists = apiClient.getPlaylists()
        }
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) loadAccountData()
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        busy = true
        scope.launch {
            val r = authManager.handleSignInResult(result.data)
            if (r.isSuccess) {
                loggedIn = true
                email = authManager.tokenStore.accountEmail
                message = "登录成功"
            } else {
                message = "登录失败：${r.exceptionOrNull()?.message}"
            }
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("账号")
        Spacer(modifier = Modifier.height(8.dp))
        if (microgPackage != null) {
            Text("MicroG 已安装（$microgPackage）")
        } else {
            Text("未检测到 MicroG：请先安装 MicroG RE 或 ReVanced MicroG 再登录。")
            Text("未登录时仍可匿名搜索播放（NewPipeExtractor 公开数据）。")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (loggedIn) {
            Text("已登录：${email ?: ""}")
            Spacer(modifier = Modifier.height(8.dp))
            if (subscriptions.isNotEmpty()) {
                Text("订阅：")
                subscriptions.take(20).forEach { Text("· ${it.title}") }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (playlists.isNotEmpty()) {
                Text("播放列表：")
                playlists.take(20).forEach { Text("· ${it.title}（${it.videoCount}）") }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(onClick = {
                scope.launch {
                    authManager.signOut()
                    loggedIn = false
                    subscriptions = emptyList()
                    playlists = emptyList()
                    message = "已退出登录"
                }
            }) { Text("退出登录") }
        } else {
            Button(
                onClick = { authManager.launchSignIn(signInLauncher) },
                enabled = microgPackage != null && !busy
            ) { Text("用 MicroG 登录谷歌账号") }
        }
        if (busy) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it)
        }
    }
}
