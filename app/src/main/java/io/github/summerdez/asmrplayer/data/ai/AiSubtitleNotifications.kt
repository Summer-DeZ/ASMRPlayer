package io.github.summerdez.asmrplayer.data.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleStage
import io.github.summerdez.asmrplayer.domain.model.AiSubtitleTaskState
import io.github.summerdez.asmrplayer.domain.model.transcriptionDetailLabel

object AiSubtitleNotifications {
    const val CHANNEL_ID = "ai_subtitles"
    const val NOTIFICATION_ID = 4303

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AI 字幕生成",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, state: AiSubtitleTaskState): Notification {
        ensureChannel(context)
        val progress = (state.overallProgress * 100).toInt().coerceIn(0, 100)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_subtitles)
            .setContentTitle("正在生成 AI 字幕")
            .setContentText("${stageText(state)} · ${state.target.trackTitle}")
            .setOngoing(state.stage != AiSubtitleStage.COMPLETED && state.stage != AiSubtitleStage.FAILED)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, state.stage == AiSubtitleStage.TRANSCRIBING && progress == 0)
            .build()
    }

    private fun stageText(state: AiSubtitleTaskState): String {
        return when (state.stage) {
            AiSubtitleStage.TRANSCRIBING -> state.transcriptionDetailLabel().ifBlank { state.transcriptionTitle }
            AiSubtitleStage.TRANSLATING -> "翻译"
            AiSubtitleStage.BINDING -> "绑定字幕"
            AiSubtitleStage.COMPLETED -> "已完成"
            AiSubtitleStage.PAUSED -> "已暂停"
            AiSubtitleStage.FAILED -> "失败"
            AiSubtitleStage.CANCELED -> "已取消"
        }
    }
}
