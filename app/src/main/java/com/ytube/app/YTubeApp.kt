package com.ytube.app

import android.app.Application
import com.ytube.app.data.OkHttpDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe

class YTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // NewPipeExtractor 初始化：一次即可，全局复用
        NewPipe.init(OkHttpDownloader(OkHttpClient()))
    }
}
