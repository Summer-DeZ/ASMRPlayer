package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.ai.RemoteWhisperTranscriber
import io.github.summerdez.asmrplayer.data.ai.remoteTranscriptionUploadMultipart
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteWhisperTranscriberParsingTest {
    @Test
    fun asyncTranscriptionsMultipartOnlyUsesContractFields() {
        val multipart = remoteTranscriptionUploadMultipart(
            fileName = "audio.mp3",
            uploadBody = ByteArray(0).toRequestBody("application/octet-stream".toMediaTypeOrNull()),
        )

        assertEquals(
            setOf("file", "language", "task"),
            multipart.formFieldNames(),
        )
    }

    @Test
    fun createJobResponseParsesJobIdAliases() {
        assertEquals(
            "job-123",
            RemoteWhisperTranscriber.parseCreateJobResponse("""{"job_id":"job-123"}""").jobId,
        )
        assertEquals(
            "task-456",
            RemoteWhisperTranscriber.parseCreateJobResponse("""{"task_id":"task-456"}""").jobId,
        )
    }

    @Test
    fun createJobResponseRejectsMissingJobId() {
        val error = assertThrows(IOException::class.java) {
            RemoteWhisperTranscriber.parseCreateJobResponse("""{"status":"queued"}""")
        }

        assertTrue(error.message.orEmpty().contains("job_id"))
    }

    @Test
    fun statusResponseParsesProgressFieldsAndPreviewSegments() {
        val status = RemoteWhisperTranscriber.parseTranscriptionStatusResponse(
            """
                {
                  "status":"running",
                  "stage":"asr",
                  "progress":45,
                  "processed_ms":120000,
                  "duration_ms":300000,
                  "updated_at":"2026-06-24T10:00:00Z",
                  "preview_segments":[
                    {"id":1,"start_ms":0,"end_ms":1000,"text":"おはよう"}
                  ]
                }
            """.trimIndent(),
        )

        assertEquals("running", status.status)
        assertEquals("asr", status.stage)
        assertEquals(0.45f, status.progress ?: -1f, 0.0001f)
        assertEquals(120000L, status.processedMs)
        assertEquals(300000L, status.durationMs)
        assertEquals("2026-06-24T10:00:00Z", status.updatedAt)
        assertEquals("おはよう", status.previewLines.single().sourceText)
    }

    @Test
    fun statusResponseTreatsStageAsTerminalSignal() {
        val completed = RemoteWhisperTranscriber.parseTranscriptionStatusResponse(
            """{"status":"succeeded","stage":"done","progress":100}""",
        )
        val failed = RemoteWhisperTranscriber.parseTranscriptionStatusResponse(
            """{"status":"running","stage":"failed","message":"decode failed"}""",
        )

        assertTrue(completed.isCompleted)
        assertTrue(failed.isFailed)
        assertEquals("decode failed", failed.message)
    }

    @Test
    fun resultResponseParsesWrappedSegmentsWithSecondTimestamps() {
        val lines = RemoteWhisperTranscriber.parseTranscriptionResultResponse(
            """
                {
                  "result": {
                    "segments": [
                      {"id": null, "start": 1.25, "end": 2.5, "text": "そっと"},
                      {"id": "b", "start": 3.0, "end": 4.75, "text": "囁くね"}
                    ]
                  }
                }
            """.trimIndent(),
        )

        assertEquals(listOf("1", "b"), lines.map { it.id })
        assertEquals(listOf(1250L, 3000L), lines.map { it.startMs })
        assertEquals(listOf(2500L, 4750L), lines.map { it.endMs })
        assertEquals(listOf("そっと", "囁くね"), lines.map { it.sourceText })
    }

    @Test
    fun errorResponseParsesNestedAndLegacyMessages() {
        assertEquals(
            "model unavailable",
            RemoteWhisperTranscriber.parseErrorMessage("""{"error":{"message":"model unavailable"}}"""),
        )
        assertEquals(
            "bad request",
            RemoteWhisperTranscriber.parseErrorMessage("""{"detail":"bad request"}"""),
        )
        assertEquals(
            "远程转写模型尚未就绪，请稍后重试",
            RemoteWhisperTranscriber.parseErrorMessage("""{"error":{"code":"MODEL_NOT_READY"}}"""),
        )
        assertEquals(
            "远程转写任务不存在或已过期，请重新生成",
            RemoteWhisperTranscriber.parseErrorMessage("""{"error_code":"JOB_NOT_FOUND"}"""),
        )
    }

    private fun MultipartBody.formFieldNames(): Set<String> {
        return parts.mapNotNull { part ->
            val contentDisposition = part.headers?.get("Content-Disposition").orEmpty()
            Regex("""name="([^"]+)"""").find(contentDisposition)?.groupValues?.get(1)
        }.toSet()
    }
}
