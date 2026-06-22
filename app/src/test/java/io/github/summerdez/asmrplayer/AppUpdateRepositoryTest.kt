package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.update.AppVersionComparator
import io.github.summerdez.asmrplayer.data.update.GitHubReleaseAssetPayload
import io.github.summerdez.asmrplayer.data.update.GitHubReleaseParser
import io.github.summerdez.asmrplayer.data.update.GitHubReleasePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun parserNormalizesTagAndFindsApkAsset() {
        val release = GitHubReleaseParser.releaseFromPayload(
            GitHubReleasePayload(
                tagName = "v1.0.2",
                releaseNotes = "- 设置页新增检查更新\n- 字幕优化",
                assets = listOf(
                    GitHubReleaseAssetPayload(
                        name = "notes.txt",
                        downloadUrl = "https://example.com/notes.txt",
                        sizeBytes = 12L,
                    ),
                    GitHubReleaseAssetPayload(
                        name = "ASMRPlayer-release.apk",
                        downloadUrl = "https://example.com/ASMRPlayer-release.apk",
                        sizeBytes = 9_437_184L,
                    ),
                ),
            ),
        )

        assertEquals("1.0.2", release.versionName)
        assertEquals("v1.0.2", release.tagName)
        assertEquals("ASMRPlayer-release.apk", release.apkName)
        assertEquals("https://example.com/ASMRPlayer-release.apk", release.apkDownloadUrl)
        assertEquals(9_437_184L, release.apkSizeBytes)
    }

    @Test
    fun versionComparisonHandlesVPrefixAndBuildMetadata() {
        assertTrue(AppVersionComparator.isNewer("v1.0.2", "1.0.1"))
        assertTrue(AppVersionComparator.isNewer("1.1.0+3", "1.0.9"))
        assertFalse(AppVersionComparator.isNewer("1.0.1", "1.0.1"))
        assertFalse(AppVersionComparator.isNewer("1.0.0", "1.0.1"))
    }
}
