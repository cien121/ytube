package com.ytube.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 登录状态存储：EncryptedSharedPreferences（AES-256-GCM，密钥存 Android Keystore）。
 * 存的是 MicroG 返回的谷歌账号信息（邮箱、展示名、ID token、Data API 访问 token），
 * 不进普通 SharedPreferences。退出登录时全部清除。
 */
class TokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ytube_auth",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accountEmail: String?
        get() = prefs.getString("account_email", null)
        set(v) = prefs.edit().putString("account_email", v).apply()

    var accountDisplayName: String?
        get() = prefs.getString("account_display_name", null)
        set(v) = prefs.edit().putString("account_display_name", v).apply()

    var idToken: String?
        get() = prefs.getString("id_token", null)
        set(v) = prefs.edit().putString("id_token", v).apply()

    /** YouTube Data API 访问 token（GoogleAuthUtil 换取，带 youtube.readonly scope） */
    var apiAccessToken: String?
        get() = prefs.getString("api_access_token", null)
        set(v) = prefs.edit().putString("api_access_token", v).apply()

    var apiTokenExpiresAtMillis: Long
        get() = prefs.getLong("api_token_expires_at", 0L)
        set(v) = prefs.edit().putLong("api_token_expires_at", v).apply()

    val isLoggedIn: Boolean
        get() = !accountEmail.isNullOrBlank()

    /** Data API token 是否可用（存在且未过期，提前 60 秒算过期） */
    val hasValidApiToken: Boolean
        get() = !apiAccessToken.isNullOrBlank() &&
            System.currentTimeMillis() + 60_000 < apiTokenExpiresAtMillis

    fun clear() {
        prefs.edit().clear().apply()
    }
}
