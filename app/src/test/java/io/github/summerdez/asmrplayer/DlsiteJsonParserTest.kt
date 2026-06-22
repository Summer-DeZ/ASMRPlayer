package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.*
import io.github.summerdez.asmrplayer.data.remote.*
import io.github.summerdez.asmrplayer.data.download.*
import io.github.summerdez.asmrplayer.data.files.*
import io.github.summerdez.asmrplayer.domain.*
import io.github.summerdez.asmrplayer.domain.model.*
import io.github.summerdez.asmrplayer.playback.*
import io.github.summerdez.asmrplayer.presentation.*
import io.github.summerdez.asmrplayer.ui.*
import io.github.summerdez.asmrplayer.ui.activity.*
import io.github.summerdez.asmrplayer.ui.components.*
import io.github.summerdez.asmrplayer.ui.screens.*
import io.github.summerdez.asmrplayer.ui.theme.*
import io.github.summerdez.asmrplayer.ui.util.*
import io.github.summerdez.asmrplayer.di.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteJsonParserTest {
    @Test
    fun parseContentCountUsesOfficialFieldNames() {
        val count = DlsiteJsonParser.parseContentCount(
            """{"user":20,"production":0,"page_limit":50,"concurrency":500}"""
        )

        assertEquals(20, count.user)
        assertEquals(0, count.production)
        assertEquals(50, count.pageLimit)
    }

    @Test
    fun parseSalesWorkIdsKeepsOfficialOrderAndDeduplicates() {
        val ids = DlsiteJsonParser.parseSalesWorkIds(
            """
            [
              {"workno":"RJ01468283","sales_date":"2026-01-01T00:00:00Z"},
              {"workno":"RJ01468283","sales_date":"2026-01-01T00:00:00Z"},
              {"workno":"RJ432317","sales_date":"2025-01-01T00:00:00Z"}
            ]
            """.trimIndent()
        )

        assertEquals(listOf("RJ01468283", "RJ432317"), ids)
    }

    @Test
    fun parseContentWorksBuildsPlayUrlsAndKeepsOnlyDownloadableAudio() {
        val works = DlsiteJsonParser.parseContentWorks(
            """
            {
              "works": [
                {
                  "workno": "RJ01078989",
                  "name": {"zh_CN": "小穴按摩・特别之夜", "ja_JP": "日本語タイトル"},
                  "image_main": "//img.dlsite.jp/modpub/images2/work/doujin/RJ01078000/RJ01078989_img_main.jpg",
                  "work_type": "SOU",
                  "downloadable": true,
                  "content_count": 1,
                  "app_type": "sound"
                },
                {
                  "workno": "BJ000001",
                  "name": {"zh_CN": "漫画"},
                  "work_type": "MNG",
                  "downloadable": true,
                  "content_count": 1
                },
                {
                  "workno": "RJ000002",
                  "name": {"zh_CN": "不可下载音频"},
                  "work_type": "SOU",
                  "downloadable": false,
                  "content_count": 1
                }
              ],
              "series": []
            }
            """.trimIndent()
        )

        assertEquals(1, works.size)
        assertEquals("RJ01078989", works[0].workId)
        assertEquals("小穴按摩・特别之夜", works[0].title)
        assertEquals("https://play.dlsite.com/work/RJ01078989/tree", works[0].detailUrl)
        assertEquals("https://play.dlsite.com/api/v3/download?workno=RJ01078989", works[0].downloadUrl)
        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ01078000/RJ01078989_img_main.jpg",
            works[0].coverUrl
        )
    }

    @Test
    fun parseWorkDetailExtractsNestedCoverUrl() {
        val work = DlsiteJsonParser.parseWorkDetail(
            """
            {
              "work": {
                "workno": "RJ01432570",
                "name": "音声作品",
                "work_type": "SOU",
                "downloadable": true,
                "content_count": 1,
                "thumbnail": {
                  "large": "/assets/RJ01432570_cover.webp"
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("RJ01432570", work.workId)
        assertEquals("https://play.dlsite.com/assets/RJ01432570_cover.webp", work.coverUrl)
    }

    @Test
    fun parseContentWorksInfersDlsiteCoverUrlWhenImageFieldIsMissing() {
        val works = DlsiteJsonParser.parseContentWorks(
            """
            {
              "works": [
                {
                  "workno": "RJ432317",
                  "name": "音声作品",
                  "work_type": "SOU",
                  "downloadable": true,
                  "content_count": 1
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ433000/RJ432317_img_main.jpg",
            works[0].coverUrl
        )
    }

    @Test
    fun isAuthorizedTreatsEmptyArrayAsLoggedOut() {
        assertFalse(DlsiteJsonParser.isAuthorized("[]"))
        assertTrue(DlsiteJsonParser.isAuthorized("""{"customer_id":"redacted"}"""))
    }

    @Test
    fun parseZiptreeExtractsAudioAndVttContentPaths() {
        val ziptree = DlsiteJsonParser.parseZiptree(
            """
            {
              "workno": "RJ01432570",
              "revision": "00000000-0000-7000-8000-000000000001",
              "tree": [
                {
                  "type": "folder",
                  "name": "本編",
                  "children": [
                    {"type": "file", "name": "01 track.mp3", "hashname": "audio_hash.mp3"},
                    {"type": "file", "name": "01 track.mp3.vtt", "hashname": "subtitle_hash.vtt"}
                  ]
                }
              ],
              "playfile": {
                "audio_hash.mp3": {
                  "type": "audio",
                  "audio": {
                    "optimized": {"name": "audio_optimized.mp3", "length": 1234},
                    "vtt": "subtitle_hash.vtt"
                  }
                },
                "subtitle_hash.vtt": {
                  "type": "text",
                  "text": {
                    "optimized": {"name": "subtitle_optimized.vtt", "length": 200}
                  }
                }
              }
            }
            """.trimIndent()
        )

        assertEquals("RJ01432570", ziptree.workId)
        assertEquals("00000000-0000-7000-8000-000000000001", ziptree.revision)
        assertEquals(1, ziptree.audioFiles.size)
        assertEquals("本編/01 track.mp3", ziptree.audioFiles[0].displayPath)
        assertEquals("optimized/audio_optimized.mp3", ziptree.audioFiles[0].contentPath)
        assertEquals("optimized/subtitle_optimized.vtt", ziptree.audioFiles[0].subtitleContentPath)
        assertEquals("01 track.mp3.vtt", ziptree.audioFiles[0].subtitleName)
    }

    @Test
    fun parseSignUrlParamsUsesParamsObject() {
        val params = DlsiteJsonParser.parseSignUrlParams(
            """{"expires":"2026-01-01T00:00:00Z","params":{"Policy":"abc","Signature":"def","Key-Pair-Id":"ghi"}}"""
        )

        assertEquals(mapOf("Policy" to "abc", "Signature" to "def", "Key-Pair-Id" to "ghi"), params)
    }

    @Test
    fun parseWebvttJsonConvertsSecondBasedCues() {
        val vtt = DlsiteJsonParser.parseWebvttJson(
            """
            {
              "webvtt": [
                {"start": 0.5, "end": 2.0, "text": "第一句"},
                {"startTime": "00:00:02.500", "endTime": "00:00:04.000", "lines": ["第二句", "下一行"]}
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            """
            WEBVTT

            00:00:00.500 --> 00:00:02.000
            第一句

            00:00:02.500 --> 00:00:04.000
            第二句
            下一行

            """.trimIndent() + "\n",
            vtt
        )
    }

    @Test
    fun parseWebvttJsonConvertsMillisecondCues() {
        val vtt = DlsiteJsonParser.parseWebvttJson(
            """{"webvtt":[{"startMs":1500,"endMs":2750,"body":"ミリ秒字幕"}]}"""
        )

        assertEquals(
            """
            WEBVTT

            00:00:01.500 --> 00:00:02.750
            ミリ秒字幕

            """.trimIndent() + "\n",
            vtt
        )
    }

    @Test
    fun parseWebvttJsonConvertsNestedTimingCues() {
        val vtt = DlsiteJsonParser.parseWebvttJson(
            """
            {
              "data": {
                "cues": [
                  {
                    "timing": {"from_ms": 3000, "to_ms": 4100},
                    "parts": [{"text": "入れ子の字幕"}]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(
            """
            WEBVTT

            00:00:03.000 --> 00:00:04.100
            入れ子の字幕

            """.trimIndent() + "\n",
            vtt
        )
    }

    @Test
    fun parseWebvttJsonConvertsDlsiteSubtitleArrays() {
        val vtt = DlsiteJsonParser.parseWebvttJson(
            """
            {
              "webvtt": [
                {
                  "index": 1,
                  "start_time": "00:00:05.369",
                  "end_time": "00:00:06.900",
                  "subtitles": ["DLsite 字幕"]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            """
            WEBVTT

            00:00:05.369 --> 00:00:06.900
            DLsite 字幕

            """.trimIndent() + "\n",
            vtt
        )
    }
}
