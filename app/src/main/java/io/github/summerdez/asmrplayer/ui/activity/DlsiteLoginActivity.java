package io.github.summerdez.asmrplayer.ui.activity;

import io.github.summerdez.asmrplayer.R;
import io.github.summerdez.asmrplayer.data.*;
import io.github.summerdez.asmrplayer.data.remote.*;
import io.github.summerdez.asmrplayer.data.download.*;
import io.github.summerdez.asmrplayer.data.files.*;
import io.github.summerdez.asmrplayer.domain.*;
import io.github.summerdez.asmrplayer.domain.model.*;
import io.github.summerdez.asmrplayer.playback.*;
import io.github.summerdez.asmrplayer.presentation.*;
import io.github.summerdez.asmrplayer.ui.*;
import io.github.summerdez.asmrplayer.ui.activity.*;
import io.github.summerdez.asmrplayer.ui.components.*;
import io.github.summerdez.asmrplayer.ui.screens.*;
import io.github.summerdez.asmrplayer.ui.theme.*;
import io.github.summerdez.asmrplayer.ui.util.*;
import io.github.summerdez.asmrplayer.di.*;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class DlsiteLoginActivity extends AppCompatActivity {
    private WebView webView;
    private View progressView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        AppUi.refreshTheme(this);
        AppUi.applySystemBars(this);
        setContentView(buildUi());
        configureWebView();
        registerBackHandler();
        webView.loadUrl(DlsiteClient.LOGIN_URL);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void registerBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                finishWithResult();
            }
        });
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(AppUi.BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(AppUi.BG);
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout header = AppUi.horizontalRow(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(12), dp(8));
        header.setBackgroundColor(AppUi.BG);

        TextView close = AppUi.compactTitleText(this, "关闭");
        close.setTextColor(AppUi.ACCENT);
        close.setTextSize(AppUi.TEXT_BODY);
        close.setGravity(Gravity.CENTER_VERTICAL);
        close.setOnClickListener(view -> finishWithResult());
        header.addView(close, new LinearLayout.LayoutParams(dp(64), dp(44)));

        TextView title = AppUi.compactTitleText(this, "DLsite 登录");
        title.setTextSize(AppUi.TEXT_HEADLINE);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        TextView done = AppUi.compactTitleText(this, "完成");
        done.setTextColor(AppUi.ACCENT);
        done.setTextSize(AppUi.TEXT_BODY);
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(view -> finishWithResult());
        header.addView(done, new LinearLayout.LayoutParams(dp(64), dp(44)));
        content.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)));

        progressView = new View(this);
        progressView.setBackgroundColor(AppUi.ACCENT);
        content.addView(progressView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(2))));

        webView = new WebView(this);
        content.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return root;
    }

    private void configureWebView() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSafeBrowsingEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progressView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                progressView.setVisibility(View.GONE);
            }
        });
    }

    private void finishWithResult() {
        CookieManager.getInstance().flush();
        setResult(Activity.RESULT_OK);
        finish();
    }

    private int dp(int value) {
        return AppUi.dp(this, value);
    }
}
