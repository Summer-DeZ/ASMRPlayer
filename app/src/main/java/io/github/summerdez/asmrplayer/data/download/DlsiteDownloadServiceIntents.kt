package io.github.summerdez.asmrplayer.data.download

import android.content.Context
import android.content.Intent

internal sealed class DlsiteDownloadStartCommand {
    object Empty : DlsiteDownloadStartCommand()
    object Unsupported : DlsiteDownloadStartCommand()
    data class Download(val workId: String?, val optionIds: List<String>) : DlsiteDownloadStartCommand()
    data class Pause(val workId: String?) : DlsiteDownloadStartCommand()
    data class Delete(val workId: String?) : DlsiteDownloadStartCommand()
}

internal object DlsiteDownloadServiceIntents {
    fun parseStartCommand(intent: Intent?): DlsiteDownloadStartCommand {
        if (intent == null || intent.action.isNullOrEmpty()) {
            return DlsiteDownloadStartCommand.Empty
        }
        return when (intent.action) {
            DlsiteDownloadService.ACTION_PAUSE -> {
                DlsiteDownloadStartCommand.Pause(intent.getStringExtra(DlsiteDownloadService.EXTRA_WORK_ID))
            }
            DlsiteDownloadService.ACTION_DELETE -> {
                DlsiteDownloadStartCommand.Delete(intent.getStringExtra(DlsiteDownloadService.EXTRA_WORK_ID))
            }
            DlsiteDownloadService.ACTION_DOWNLOAD -> {
                DlsiteDownloadStartCommand.Download(
                    intent.getStringExtra(DlsiteDownloadService.EXTRA_WORK_ID),
                    requestedOptionIds(intent),
                )
            }
            else -> DlsiteDownloadStartCommand.Unsupported
        }
    }

    fun downloadIntent(context: Context?, workId: String?, optionId: String?): Intent {
        val optionIds = ArrayList<String>()
        if (optionId != null) {
            optionIds.add(optionId)
        }
        return downloadIntent(context, workId, optionIds)
    }

    fun downloadIntent(context: Context?, workId: String?, optionIds: List<String>?): Intent {
        val ids = ArrayList<String>()
        if (optionIds != null) {
            ids.addAll(optionIds)
        }
        return Intent(context, DlsiteDownloadService::class.java).apply {
            action = DlsiteDownloadService.ACTION_DOWNLOAD
            putExtra(DlsiteDownloadService.EXTRA_WORK_ID, workId ?: "")
            putStringArrayListExtra(DlsiteDownloadService.EXTRA_OPTION_IDS, ids)
            putExtra(DlsiteDownloadService.EXTRA_OPTION_ID, if (ids.isEmpty()) "" else ids[0])
        }
    }

    fun pauseIntent(context: Context?, workId: String?): Intent {
        return Intent(context, DlsiteDownloadService::class.java).apply {
            action = DlsiteDownloadService.ACTION_PAUSE
            putExtra(DlsiteDownloadService.EXTRA_WORK_ID, workId ?: "")
        }
    }

    fun deleteIntent(context: Context?, workId: String?): Intent {
        return Intent(context, DlsiteDownloadService::class.java).apply {
            action = DlsiteDownloadService.ACTION_DELETE
            putExtra(DlsiteDownloadService.EXTRA_WORK_ID, workId ?: "")
        }
    }

    private fun requestedOptionIds(intent: Intent): List<String> {
        var optionIds = intent.getStringArrayListExtra(DlsiteDownloadService.EXTRA_OPTION_IDS)
        if (optionIds == null) {
            optionIds = ArrayList()
            val optionId = intent.getStringExtra(DlsiteDownloadService.EXTRA_OPTION_ID)
            if (optionId != null) {
                optionIds.add(optionId)
            }
        }
        return ArrayList(optionIds.filter { it.isNotBlank() })
    }
}
