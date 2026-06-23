package io.github.summerdez.asmrplayer.ui.theme;

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
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public final class AppUi {
    public static int ACCENT;
    public static int SWITCH_ON;
    public static int SWITCH_OFF;
    public static int BG;
    public static int GRAY6;
    public static int GRAY5;
    public static int GRAY4;
    public static int GRAY3;
    public static int LABEL;
    public static int LABEL2;
    public static int LABEL3;
    public static int SEPARATOR;
    public static int BAR_MATERIAL;
    public static int SOLID_BAR;
    public static int SHEET_MATERIAL;

    public static final int TEXT_LARGE_TITLE = 34;
    public static final int TEXT_TITLE2 = 22;
    public static final int TEXT_HEADLINE = 17;
    public static final int TEXT_BODY = 17;
    public static final int TEXT_SUBHEAD = 15;
    public static final int TEXT_FOOTNOTE = 13;
    public static final int TEXT_CAPTION = 12;

    public static final int R_THUMB = 10;
    public static final int R_CARD = 16;
    public static final int R_BAR = 18;

    private static AppThemeMode themeMode = AppThemeMode.DARK;
    private static AppThemePalette palette = AppThemePalette.dark();

    static {
        applyPalette(palette);
    }

    private AppUi() {
    }

    public static void setThemeMode(Context context, AppThemeMode mode) {
        themeMode = mode == null ? AppThemeMode.DARK : mode;
        applyPalette(resolvePalette(context, themeMode));
    }

    public static AppThemeMode themeMode() {
        return themeMode;
    }

    public static boolean isLightTheme() {
        return palette.light;
    }

    public static void refreshTheme(Context context) {
        applyPalette(resolvePalette(context, themeMode));
    }

    public static void applySystemBars(Activity activity) {
        if (activity == null) {
            return;
        }
        activity.getWindow().setStatusBarColor(BG);
        activity.getWindow().setNavigationBarColor(SOLID_BAR);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(),
                activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(isLightTheme());
        controller.setAppearanceLightNavigationBars(isLightTheme());
    }

    private static AppThemePalette resolvePalette(Context context, AppThemeMode mode) {
        if (mode == AppThemeMode.LIGHT) {
            return AppThemePalette.light();
        }
        if (mode == AppThemeMode.SYSTEM && context != null && isSystemLight(context)) {
            return AppThemePalette.light();
        }
        return AppThemePalette.dark();
    }

    private static boolean isSystemLight(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_NO;
    }

    private static void applyPalette(AppThemePalette nextPalette) {
        palette = nextPalette;
        ACCENT = nextPalette.accent;
        SWITCH_ON = nextPalette.switchOn;
        SWITCH_OFF = nextPalette.switchOff;
        BG = nextPalette.bg;
        GRAY6 = nextPalette.gray6;
        GRAY5 = nextPalette.gray5;
        GRAY4 = nextPalette.gray4;
        GRAY3 = nextPalette.gray3;
        LABEL = nextPalette.label;
        LABEL2 = nextPalette.label2;
        LABEL3 = nextPalette.label3;
        SEPARATOR = nextPalette.separator;
        BAR_MATERIAL = nextPalette.barMaterial;
        SOLID_BAR = nextPalette.solidBar;
        SHEET_MATERIAL = nextPalette.sheetMaterial;
    }

    public static LinearLayout horizontalRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams matchWrapWithTop(Context context, int topDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(context, topDp);
        return params;
    }

    public static TextView compactTitleText(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(LABEL);
        view.setTextSize(TEXT_HEADLINE);
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    public static TextView pageTitle(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(LABEL);
        view.setTextSize(TEXT_LARGE_TITLE);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    public static TextView compactSubtitleText(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(LABEL2);
        view.setTextSize(TEXT_FOOTNOTE);
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    public static ImageView iconView(Context context, int drawableRes, int tintColor, int iconDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(drawableRes);
        view.setImageTintList(ColorStateList.valueOf(tintColor));
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        view.setMaxWidth(dp(context, iconDp));
        view.setMaxHeight(dp(context, iconDp));
        return view;
    }

    public static ImageButton iconButton(Context context, int drawableRes, int tintColor, int iconDp) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawableRes);
        button.setImageTintList(ColorStateList.valueOf(tintColor));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setAdjustViewBounds(false);
        button.setMaxWidth(dp(context, iconDp));
        button.setMaxHeight(dp(context, iconDp));
        return button;
    }

    public static TextView miniCoverView(Context context, int index) {
        TextView view = new TextView(context);
        view.setText(String.valueOf(index));
        view.setTextColor(LABEL3);
        view.setTextSize(24f);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setBackground(coverBackground(context, false));
        return view;
    }

    public static FrameLayout coverView(Context context, String coverUri, int sizeDp, boolean selected) {
        FrameLayout cover = new FrameLayout(context);
        cover.setBackground(coverBackground(context, selected));
        cover.setClipToOutline(true);

        if (!TextUtils.isEmpty(coverUri)) {
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                image.setImageURI(Uri.parse(coverUri));
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
            cover.addView(image, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            ImageView placeholder = iconView(context, R.drawable.ic_music_note, LABEL3, Math.max(22, sizeDp / 2));
            cover.addView(placeholder, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        return cover;
    }

    public static TextView subtitleLineText(Context context, int color, float textSize) {
        TextView view = new TextView(context);
        view.setTextColor(color);
        view.setTextSize(textSize);
        view.setGravity(Gravity.LEFT);
        view.setMaxLines(4);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static Button transparentTextButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(22f);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        button.setTextColor(LABEL);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    public static Button compactButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15f);
        button.setTextColor(LABEL);
        button.setMinHeight(dp(context, 42));
        button.setBackground(cardBackground(context, GRAY6, SEPARATOR, 1, R_CARD));
        return button;
    }

    public static View rowWithDivider(Context context, View row, int leftInsetDp) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.TRANSPARENT);
        container.addView(row, matchWrap());
        container.addView(listDivider(context, leftInsetDp));
        return container;
    }

    public static View listDivider(Context context, int leftInsetDp) {
        View divider = new View(context);
        divider.setBackgroundColor(SEPARATOR);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(context, 1)));
        params.leftMargin = dp(context, leftInsetDp);
        params.rightMargin = dp(context, 10);
        divider.setLayoutParams(params);
        return divider;
    }

    public static GradientDrawable materialBackground(Context context, int fillColor, int radiusDp) {
        return cardBackground(context, fillColor, SEPARATOR, 1, radiusDp);
    }

    public static GradientDrawable topRoundedBackground(Context context, int fillColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        float radius = dp(context, radiusDp);
        drawable.setCornerRadii(new float[]{
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f});
        return drawable;
    }

    public static GradientDrawable cardBackground(
            Context context,
            int fillColor,
            int strokeColor,
            int strokeDp,
            int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(context, strokeDp), strokeColor);
        }
        return drawable;
    }

    private static GradientDrawable coverBackground(Context context, boolean selected) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{GRAY5, GRAY4});
        drawable.setCornerRadius(dp(context, R_THUMB));
        drawable.setStroke(dp(context, 1), selected ? ACCENT : SEPARATOR);
        return drawable;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
