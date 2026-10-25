package com.ytube.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

/**
 * NewPipeExtractor 的网络层实现（OkHttp）。
 * NewPipeExtractor 只解析网页，不自带网络请求，必须由调用方提供 Downloader。
 */
class OkHttpDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : Downloader() {

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (name, values) ->
            values.forEach { builder.addHeader(name, it) }
        }
        val data = request.dataToSend()
        if (data != null) {
            val body = data.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            builder.post(body)
        }
        val response = client.newCall(builder.build()).execute()
        val headers = mutableMapOf<String, List<String>>()
        response.headers.forEach { (name, value) ->
            headers[name] = headers.getOrDefault(name, emptyList()) + value
        }
        return Response(
            response.code,
            response.message,
            headers,
            response.body?.string(),
            response.request.url.toString()
        )
    }
}
