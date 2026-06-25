package io.github.summerdez.asmrplayer.presentation

import android.text.TextUtils
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class DlsiteEventMessageHelper {
    private val mutableEvents = MutableSharedFlow<DlsiteEvent>(extraBufferCapacity = 8)

    val events: SharedFlow<DlsiteEvent> = mutableEvents.asSharedFlow()

    fun showMessage(message: String) {
        if (message.isNotEmpty()) {
            mutableEvents.tryEmit(DlsiteEvent.Message(message))
        }
    }
}

internal fun shortDlsiteError(exception: Exception?): String {
    var message = exception?.message.orEmpty()
    if (TextUtils.isEmpty(message)) {
        message = "DLsite 操作失败"
    }
    return if (message.length > 42) message.substring(0, 42) + "..." else message
}

internal fun rethrowIfCancellationOrInterrupted(exception: Exception) {
    when (exception) {
        is CancellationException -> throw exception
        is InterruptedException -> {
            Thread.currentThread().interrupt()
            throw exception
        }
        is InterruptedIOException -> throw exception
    }
}
