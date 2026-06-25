package io.github.summerdez.asmrplayer.data.ai

import io.github.summerdez.asmrplayer.domain.model.AiSubtitleSettings
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal suspend fun <T> executeRemoteJsonRequest(
    request: Request,
    requestClient: OkHttpClient,
    parser: (String) -> T,
): T {
    val call = requestClient.newCall(request)
    val response = try {
        executeCancellable(call)
    } catch (error: IOException) {
        throw IOException(remoteNetworkError(error), error)
    }
    response.use { activeResponse ->
        val body = activeResponse.body?.string().orEmpty()
        if (!activeResponse.isSuccessful) {
            throw IOException("远程转写服务失败：HTTP ${activeResponse.code}${remoteErrorHint(body)}")
        }
        if (body.isBlank()) {
            throw IOException("远程转写服务响应为空")
        }
        return parser(body)
    }
}

internal fun Request.Builder.applyAuthorization(settings: AiSubtitleSettings): Request.Builder {
    val token = settings.remoteWhisperToken.trim()
    if (token.isNotBlank()) {
        header("Authorization", "Bearer $token")
    }
    return this
}

internal fun remoteBaseUrl(settings: AiSubtitleSettings): String {
    val baseUrl = settings.normalizedRemoteWhisperBaseUrl
    if (baseUrl.isBlank()) {
        throw IOException("请先填写远程转写服务地址")
    }
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
        throw IOException("远程转写服务地址需要以 http:// 或 https:// 开头")
    }
    return baseUrl
}

internal suspend fun executeCancellable(call: Call): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isCancelled) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            },
        )
    }
}

internal fun remoteNetworkError(error: IOException): String {
    return when (error) {
        is SocketTimeoutException ->
            "远程转写服务请求超时，请检查服务器是否仍在转写、网络是否稳定"
        is UnknownHostException ->
            "远程转写服务地址无法解析，请检查服务器地址和网络"
        else ->
            "远程转写服务请求失败：${error.message ?: "网络异常"}"
    }
}

internal fun remoteErrorHint(body: String): String {
    val message = runCatching { RemoteWhisperTranscriber.parseErrorMessage(body) }.getOrDefault("")
    return if (message.isBlank()) "" else "：$message"
}
