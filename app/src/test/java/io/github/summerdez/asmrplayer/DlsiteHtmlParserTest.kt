package io.github.summerdez.asmrplayer

import io.github.summerdez.asmrplayer.data.remote.DlsiteHtmlParser

import org.junit.Assert.assertEquals
import org.junit.Test

class DlsiteHtmlParserTest {
    @Test
    fun parsePurchasedWorksUsesWorkIdTitleAndLinks() {
        val html = """
            <html>
              <a href="/maniax/work/=/product_id/RJ123456.html">RJ123456 Whisper Work</a>
              <a href="/home/mypage/download/=/product_id/RJ123456.html">Download</a>
            </html>
        """.trimIndent()

        val works = DlsiteHtmlParser.parsePurchasedWorks(html, "https://www.dlsite.com/home/mypage/userbuy")

        assertEquals(1, works.size)
        assertEquals("RJ123456", works[0].workId)
        assertEquals("Whisper Work", works[0].title)
        assertEquals("https://www.dlsite.com/maniax/work/=/product_id/RJ123456.html", works[0].detailUrl)
        assertEquals("https://www.dlsite.com/home/mypage/download/=/product_id/RJ123456.html", works[0].downloadUrl)
    }

    @Test
    fun findFirstDownloadUrlDoesNotRequireWorkIdInButtonText() {
        val html = """<a href="/home/mypage/download/=/product_id/RJ654321.html">作品ファイルをダウンロード</a>"""

        val url = DlsiteHtmlParser.findFirstDownloadUrl(html, "https://www.dlsite.com/maniax/work/=/product_id/RJ654321.html")

        assertEquals("https://www.dlsite.com/home/mypage/download/=/product_id/RJ654321.html", url)
    }

    @Test
    fun findCoverUrlUsesDlsiteImageAttributes() {
        val html = """
            <html>
              <meta property="og:image" content="//img.dlsite.jp/modpub/images2/work/doujin/RJ433000/RJ432317_img_main.jpg">
            </html>
        """.trimIndent()

        val url = DlsiteHtmlParser.findCoverUrl(html, "https://www.dlsite.com/maniax/work/=/product_id/RJ432317.html", "RJ432317")

        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ433000/RJ432317_img_main.jpg",
            url
        )
    }

    @Test
    fun findCoverUrlAllowsTranslatedWorkRedirectCover() {
        val html = """
            <html>
              <meta property="og:image" content="//img.dlsite.jp/modpub/images2/work/doujin/RJ01256000/RJ01255129_img_main.jpg">
            </html>
        """.trimIndent()

        val url = DlsiteHtmlParser.findCoverUrl(
            html,
            "https://www.dlsite.com/maniax/work/=/product_id/RJ01255130.html",
            "RJ01255130",
        )

        assertEquals(
            "https://img.dlsite.jp/modpub/images2/work/doujin/RJ01256000/RJ01255129_img_main.jpg",
            url,
        )
    }
}
