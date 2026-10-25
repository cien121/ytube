package com.ytube.app.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * MicroG 安装检测。
 *
 * MicroG（原版 GmsCore / Vanced MicroG / ReVanced MicroG / MicroG RE）实现的是
 * Google Play Services 的接口，包名可能是以下任一：
 * - com.google.android.gms（原版 microG 直接顶替 Play Services 的包名）
 * - com.mgoogle.android.gms（Vanced MicroG）
 * - app.revanced.microg（ReVanced MicroG / MicroG RE）
 *
 * 注意：Android 11+ 查其他应用包名必须在 Manifest 里声明 <queries>，见 AndroidManifest。
 * 未安装时 App 不阻塞：照常用 NewPipeExtractor 匿名搜索播放，只是登录按钮会提示先装 MicroG。
 */
object MicroGDetector {
    private val candidatePackages = listOf(
        "com.google.android.gms",
        "com.mgoogle.android.gms",
        "app.revanced.microg"
    )

    /** 返回已安装的 MicroG 包名，未安装返回 null */
    fun installedPackage(context: Context): String? {
        val pm = context.packageManager
        return candidatePackages.firstOrNull { isInstalled(pm, it) }
    }

    fun isInstalled(context: Context): Boolean = installedPackage(context) != null

    /**
     * GMS API 可用性检查（MicroG 实现了 GMS 接口，这里应返回 SUCCESS）。
     * 仅作辅助判断：包名检测不到时也可用它兜底。
     */
    fun gmsAvailable(context: Context): Boolean {
        return try {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
