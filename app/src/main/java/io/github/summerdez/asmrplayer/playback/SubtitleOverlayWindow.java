package io.github.summerdez.asmrplayer.playback;

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
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class SubtitleOverlayWindow {
    interface Listener {
        void onPrevious();

        void onPlayPause();

        void onNext();

        void onLock();
    }

    enum ShowResult {
        SHOWN,
        MISSING_PERMISSION,
        FAILED
    }

    private final Context context;
    private final WindowManager windowManager;
    private final Listener listener;
    private LinearLayout rootView;
    private LinearLayout topRow;
    private LinearLayout controlsRow;
    private TextView textView;
    private ImageButton playPauseButton;
    private WindowManager.LayoutParams params;
    private boolean playing;
    private boolean locked;

    SubtitleOverlayWindow(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        windowManager = context.getSystemService(WindowManager.class);
    }

    boolean hasPermission() {
        return Settings.canDrawOverlays(context);
    }

    ShowResult show(String text, boolean playing, boolean locked) {
        this.playing = playing;
        this.locked = locked;
        if (!hasPermission()) {
            return ShowResult.MISSING_PERMISSION;
        }
        if (windowManager == null) {
            return ShowResult.FAILED;
        }
        if (rootView != null) {
            updateText(text);
            applyPlayingState();
            applyLockedState();
            updateLayoutFlags();
            return ShowResult.SHOWN;
        }

        rootView = new LinearLayout(context);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setGravity(Gravity.CENTER);
        rootView.setOnTouchListener(new DragTouchListener());

        topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = iconView(R.drawable.ic_music_note, 0xFFE6E3F2, 22);
        topRow.addView(mark, new LinearLayout.LayoutParams(dp(32), dp(30)));
        View spacer = new View(context);
        topRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        ImageButton lockButton = overlayButton(R.drawable.ic_lock, 32, false);
        lockButton.setOnClickListener(view -> listener.onLock());
        topRow.addView(lockButton, new LinearLayout.LayoutParams(dp(34), dp(32)));
        rootView.addView(topRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        textView = new TextView(context);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(18f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setGravity(Gravity.CENTER);
        textView.setMaxLines(4);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setIncludeFontPadding(false);
        textView.setShadowLayer(dp(3), 0f, dp(1), 0xDD000000);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(6);
        textParams.bottomMargin = dp(10);
        rootView.addView(textView, textParams);

        controlsRow = new LinearLayout(context);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER);
        ImageButton previousButton = overlayButton(R.drawable.ic_skip_previous, 36, true);
        previousButton.setOnClickListener(view -> listener.onPrevious());
        controlsRow.addView(previousButton, new LinearLayout.LayoutParams(dp(38), dp(38)));

        playPauseButton = overlayButton(R.drawable.ic_pause, 44, true);
        playPauseButton.setOnClickListener(view -> listener.onPlayPause());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        playParams.leftMargin = dp(16);
        playParams.rightMargin = dp(16);
        controlsRow.addView(playPauseButton, playParams);

        ImageButton nextButton = overlayButton(R.drawable.ic_skip_next, 36, true);
        nextButton.setOnClickListener(view -> listener.onNext());
        controlsRow.addView(nextButton, new LinearLayout.LayoutParams(dp(38), dp(38)));
        rootView.addView(controlsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        updateText(text);
        applyPlayingState();
        applyLockedState();

        params = new WindowManager.LayoutParams(
                panelWidth(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                windowFlags(),
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(96);

        try {
            windowManager.addView(rootView, params);
            return ShowResult.SHOWN;
        } catch (RuntimeException error) {
            rootView = null;
            topRow = null;
            controlsRow = null;
            textView = null;
            playPauseButton = null;
            params = null;
            return ShowResult.FAILED;
        }
    }

    void updateText(String text) {
        if (textView == null) {
            return;
        }
        String subtitleText = text == null ? "" : text;
        textView.setText(subtitleText);
        textView.setVisibility(TextUtils.isEmpty(subtitleText) ? View.GONE : View.VISIBLE);
    }

    void setPlaying(boolean playing) {
        this.playing = playing;
        applyPlayingState();
    }

    void setLocked(boolean locked) {
        this.locked = locked;
        applyLockedState();
        updateLayoutFlags();
    }

    void remove() {
        if (rootView == null || windowManager == null) {
            clearViews();
            return;
        }
        try {
            windowManager.removeView(rootView);
        } catch (RuntimeException ignored) {
        }
        clearViews();
    }

    private void applyPlayingState() {
        if (playPauseButton != null) {
            playPauseButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    private void applyLockedState() {
        if (rootView == null) {
            return;
        }
        rootView.setMinimumWidth(panelWidth());
        rootView.setPadding(dp(12), dp(10), dp(12), dp(12));
        rootView.setBackground(locked ? transparentBackground() : panelBackground());
        if (topRow != null) {
            topRow.setVisibility(locked ? View.INVISIBLE : View.VISIBLE);
        }
        if (controlsRow != null) {
            controlsRow.setVisibility(locked ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void updateLayoutFlags() {
        if (rootView == null || params == null || windowManager == null) {
            return;
        }
        params.flags = windowFlags();
        params.width = panelWidth();
        try {
            windowManager.updateViewLayout(rootView, params);
        } catch (RuntimeException ignored) {
        }
    }

    private int windowFlags() {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (locked) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        return flags;
    }

    private ImageView iconView(int drawableRes, int tintColor, int iconDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(drawableRes);
        view.setImageTintList(ColorStateList.valueOf(tintColor));
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setMaxWidth(dp(iconDp));
        view.setMaxHeight(dp(iconDp));
        return view;
    }

    private ImageButton overlayButton(int drawableRes, int sizeDp, boolean outlined) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawableRes);
        button.setImageTintList(ColorStateList.valueOf(0xFFEDEAF6));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setBackground(outlined ? circleBackground() : null);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setMaxWidth(dp(sizeDp));
        button.setMaxHeight(dp(sizeDp));
        return button;
    }

    private int panelWidth() {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(300), Math.min(screenWidth - dp(80), dp(420)));
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xAA101017);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable transparentBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable circleBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0x22101017);
        drawable.setStroke(dp(1), 0xAAEDEAF6);
        return drawable;
    }

    private void clearViews() {
        rootView = null;
        topRow = null;
        controlsRow = null;
        textView = null;
        playPauseButton = null;
        params = null;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private static final int DRAG_SLOP_DP = 4;

        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (locked || params == null || rootView == null || windowManager == null) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - initialTouchX);
                    int dy = Math.round(event.getRawY() - initialTouchY);
                    if (Math.abs(dx) > dp(DRAG_SLOP_DP) || Math.abs(dy) > dp(DRAG_SLOP_DP)) {
                        moved = true;
                    }
                    params.x = initialX + dx;
                    params.y = initialY + dy;
                    windowManager.updateViewLayout(rootView, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        view.performClick();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
