package com.ytube.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.ytube.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MicroG 登录管理器（ReVanced 模式）。
 *
 * 原理：App 调标准 Google Sign-In API（play-services-auth），手机上装了 MicroG 时，
 * 由 MicroG 的 GmsCore 实现响应登录并返回谷歌账号——App 本身不区分 MicroG 还是真 Play Services，
 * 也不弹 WebView、不走 OAuth 网页流程。未装 MicroG 时 signInIntent 不可用，UI 提示先装 MicroG。
 *
 * Server client ID 说明：requestIdToken 需要一个「Web 应用」类型的 OAuth 客户端 ID，
 * 仅用于拿 ID token；不填也能登录（只拿邮箱和基本资料）。用户后续提供真实 ID 后填到
 * app/build.gradle.kts 的 buildConfigField YTUBE_SERVER_CLIENT_ID 即可。
 */
class MicroGAuthManager(private val context: Context) {
    val tokenStore = TokenStore(context)

    private val signInClient: GoogleSignInClient by lazy {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(YOUTUBE_READONLY_SCOPE))
        val serverClientId = BuildConfig.YTUBE_SERVER_CLIENT_ID
        if (serverClientId.isNotBlank() && !serverClientId.startsWith("YOUR_SERVER_CLIENT_ID")) {
            builder.requestIdToken(serverClientId)
        }
        GoogleSignIn.getClient(context, builder.build())
    }

    /** 发起登录：拉起 MicroG 的登录界面（系统级账号选择，不是 WebView） */
    fun launchSignIn(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(signInClient.signInIntent)
    }

    /** 处理登录结果：成功则保存账号信息；失败返回错误信息 */
    suspend fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> =
        withContext(Dispatchers.IO) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(Exception::class.java)
                tokenStore.accountEmail = account.email
                tokenStore.accountDisplayName = account.displayName
                tokenStore.idToken = account.idToken
                // 尝试换取 Data API 访问 token（MicroG 支持 GoogleAuthUtil 时才成功；
                // 失败不影响登录本身，订阅/播放列表功能届时提示）
                refreshApiToken(account)?.let {
                    tokenStore.apiAccessToken = it
                    // GoogleAuthUtil 返回的 token 有效期通常 1 小时，保守按 50 分钟算
                    tokenStore.apiTokenExpiresAtMillis =
                        System.currentTimeMillis() + 50 * 60 * 1000L
                }
                Result.success(account)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 用 GoogleAuthUtil 向 MicroG 换取带 youtube.readonly scope 的访问 token。
     * 抛 UserRecoverableAuthException（需用户再次授权）时返回 null，由 UI 决定是否引导。
     */
    suspend fun refreshApiToken(account: GoogleSignInAccount): String? =
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(
                    context,
                    account.account!!,
                    "oauth2:$YOUTUBE_READONLY_SCOPE"
                )
            } catch (e: UserRecoverableAuthException) {
                null
            } catch (e: Exception) {
                null
            }
        }

    /** 退出登录：清本地 token + 通知 MicroG 登出 */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            signInClient.signOut()
        } catch (e: Exception) {
            // 忽略登出失败，本地 token 照清
        }
        tokenStore.clear()
    }

    companion object {
        const val YOUTUBE_READONLY_SCOPE =
            "https://www.googleapis.com/auth/youtube.readonly"
    }
}
