package io.github.summerdez.asmrplayer.ui.activity

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import io.github.summerdez.asmrplayer.data.remote.DlsiteClient
import io.github.summerdez.asmrplayer.ui.theme.AppUi
import kotlin.math.max

open class DlsiteLoginActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private var progressView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        AppUi.refreshTheme(this)
        AppUi.applySystemBars(this)
        setContentView(buildUi())
        configureWebView()
        registerBackHandler()
        webView?.loadUrl(DlsiteClient.LOGIN_URL)
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView?.canGoBack() == true) {
                        webView?.goBack()
                        return
                    }
                    finishWithResult()
                }
            },
        )
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(AppUi.BG)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(AppUi.BG)
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val header = AppUi.horizontalRow(this)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(10), dp(12), dp(8))
        header.setBackgroundColor(AppUi.BG)

        val close = AppUi.compactTitleText(this, "关闭")
        close.setTextColor(AppUi.ACCENT)
        close.textSize = AppUi.TEXT_BODY.toFloat()
        close.gravity = Gravity.CENTER_VERTICAL
        close.setOnClickListener { finishWithResult() }
        header.addView(close, LinearLayout.LayoutParams(dp(64), dp(44)))

        val title = AppUi.compactTitleText(this, "DLsite 登录")
        title.textSize = AppUi.TEXT_HEADLINE.toFloat()
        title.gravity = Gravity.CENTER
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        header.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )

        val done = AppUi.compactTitleText(this, "完成")
        done.setTextColor(AppUi.ACCENT)
        done.textSize = AppUi.TEXT_BODY.toFloat()
        done.gravity = Gravity.CENTER
        done.setOnClickListener { finishWithResult() }
        header.addView(done, LinearLayout.LayoutParams(dp(64), dp(44)))
        content.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64),
            ),
        )

        progressView = View(this)
        progressView?.setBackgroundColor(AppUi.ACCENT)
        content.addView(
            progressView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                max(1, dp(2)),
            ),
        )

        webView = WebView(this)
        content.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return root
    }

    private fun configureWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val settings = webView?.settings ?: return
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.safeBrowsingEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        webView?.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressView?.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                progressView?.visibility = View.GONE
            }
        }
    }

    private fun finishWithResult() {
        CookieManager.getInstance().flush()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun dp(value: Int): Int {
        return AppUi.dp(this, value)
    }
}
