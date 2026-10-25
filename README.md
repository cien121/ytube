# YTube

YouTube 客户端（只做视频），无广告、支持 MicroG 登录、最高画质播放。

## 功能（MVP）

- 搜索 YouTube 视频（标题、封面、时长、UP 主），点开即播
- 天生无广告：不用官方播放器，数据走 NewPipeExtractor
- 画质：默认该视频最高分辨率（4K 优先），播放器里「画质」按钮可手动切换全部档位
- MicroG 登录（ReVanced 模式）：手机装 MicroG 后，设置页可用标准 Google Sign-In API 登录；
  登录后可拉取订阅、播放列表。未装 MicroG 时提示安装，匿名模式照常用
- 只打 arm64-v8a

## 技术

- NewPipeExtractor v0.26.5（JitPack）：公开搜索 + 全量取流（progressive + video-only）
- Media3 ExoPlayer：不设分辨率上限；video-only 档用 MergingMediaSource 合并音频流
- Jetpack Compose + Material3
- Token 存储：EncryptedSharedPreferences

## 登录说明

不要 WebView、不要 OAuth 网页流程。App 调 play-services-auth 的 Google Sign-In API，
由 MicroG 的 GmsCore 原生响应。检测包名：`com.google.android.gms` /
`com.mgoogle.android.gms` / `app.revanced.microg`。

`requestIdToken` 的 server client ID 是可选配置（不填也能登录），填入位置：
`app/build.gradle.kts` 的 `YTUBE_SERVER_CLIENT_ID`。

## 构建

CI 自动构建，产物在 Actions 的 `YTube-APK` artifact 里：

```bash
./gradlew assembleRelease
```

APK 位置：`app/build/outputs/apk/release/*.apk`
