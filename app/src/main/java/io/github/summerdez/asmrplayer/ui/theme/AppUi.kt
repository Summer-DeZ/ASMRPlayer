package io.github.summerdez.asmrplayer.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import io.github.summerdez.asmrplayer.R
import io.github.summerdez.asmrplayer.domain.model.AppThemeMode
import kotlin.math.max

class AppUi private constructor() {
    companion object {
        var ACCENT: Int = 0
        var SWITCH_ON: Int = 0
        var SWITCH_OFF: Int = 0
        var BG: Int = 0
        var GRAY6: Int = 0
        var GRAY5: Int = 0
        var GRAY4: Int = 0
        var GRAY3: Int = 0
        var LABEL: Int = 0
        var LABEL2: Int = 0
        var LABEL3: Int = 0
        var SEPARATOR: Int = 0
        var BAR_MATERIAL: Int = 0
        var SOLID_BAR: Int = 0
        var SHEET_MATERIAL: Int = 0

        const val TEXT_LARGE_TITLE: Int = 34
        const val TEXT_TITLE2: Int = 22
        const val TEXT_HEADLINE: Int = 17
        const val TEXT_BODY: Int = 17
        const val TEXT_SUBHEAD: Int = 15
        const val TEXT_FOOTNOTE: Int = 13
        const val TEXT_CAPTION: Int = 12

        const val R_THUMB: Int = 10
        const val R_CARD: Int = 16
        const val R_BAR: Int = 18

        private var currentThemeMode: AppThemeMode = AppThemeMode.DARK
        private var palette: AppThemePalette = AppThemePalette.dark()

        init {
            applyPalette(palette)
        }

        fun setThemeMode(context: Context?, mode: AppThemeMode?) {
            currentThemeMode = mode ?: AppThemeMode.DARK
            applyPalette(resolvePalette(context, currentThemeMode))
        }

        fun themeMode(): AppThemeMode {
            return currentThemeMode
        }

        fun isLightTheme(): Boolean {
            return palette.light
        }

        fun refreshTheme(context: Context?) {
            applyPalette(resolvePalette(context, currentThemeMode))
        }

        fun applySystemBars(activity: Activity?) {
            if (activity == null) {
                return
            }
            activity.window.statusBarColor = BG
            activity.window.navigationBarColor = SOLID_BAR
            val controller = WindowCompat.getInsetsController(
                activity.window,
                activity.window.decorView,
            )
            controller.isAppearanceLightStatusBars = isLightTheme()
            controller.isAppearanceLightNavigationBars = isLightTheme()
        }

        private fun resolvePalette(context: Context?, mode: AppThemeMode): AppThemePalette {
            if (mode == AppThemeMode.LIGHT) {
                return AppThemePalette.light()
            }
            if (mode == AppThemeMode.SYSTEM && context != null && isSystemLight(context)) {
                return AppThemePalette.light()
            }
            return AppThemePalette.dark()
        }

        private fun isSystemLight(context: Context): Boolean {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return nightMode == Configuration.UI_MODE_NIGHT_NO
        }

        private fun applyPalette(nextPalette: AppThemePalette) {
            palette = nextPalette
            ACCENT = nextPalette.accent
            SWITCH_ON = nextPalette.switchOn
            SWITCH_OFF = nextPalette.switchOff
            BG = nextPalette.bg
            GRAY6 = nextPalette.gray6
            GRAY5 = nextPalette.gray5
            GRAY4 = nextPalette.gray4
            GRAY3 = nextPalette.gray3
            LABEL = nextPalette.label
            LABEL2 = nextPalette.label2
            LABEL3 = nextPalette.label3
            SEPARATOR = nextPalette.separator
            BAR_MATERIAL = nextPalette.barMaterial
            SOLID_BAR = nextPalette.solidBar
            SHEET_MATERIAL = nextPalette.sheetMaterial
        }

        fun horizontalRow(context: Context): LinearLayout {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER
            return row
        }

        fun matchWrap(): LinearLayout.LayoutParams {
            return LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        fun matchWrapWithTop(context: Context, topDp: Int): LinearLayout.LayoutParams {
            val params = matchWrap()
            params.topMargin = dp(context, topDp)
            return params
        }

        fun compactTitleText(context: Context, text: String?): TextView {
            val view = TextView(context)
            view.text = text
            view.setTextColor(LABEL)
            view.textSize = TEXT_HEADLINE.toFloat()
            view.maxLines = 1
            view.ellipsize = TextUtils.TruncateAt.END
            return view
        }

        fun pageTitle(context: Context, text: String?): TextView {
            val view = TextView(context)
            view.text = text
            view.setTextColor(LABEL)
            view.textSize = TEXT_LARGE_TITLE.toFloat()
            view.setTypeface(view.typeface, Typeface.BOLD)
            view.maxLines = 1
            view.ellipsize = TextUtils.TruncateAt.END
            return view
        }

        fun compactSubtitleText(context: Context, text: String?): TextView {
            val view = TextView(context)
            view.text = text
            view.setTextColor(LABEL2)
            view.textSize = TEXT_FOOTNOTE.toFloat()
            view.maxLines = 1
            view.ellipsize = TextUtils.TruncateAt.END
            return view
        }

        fun iconView(context: Context, drawableRes: Int, tintColor: Int, iconDp: Int): ImageView {
            val view = ImageView(context)
            view.setImageResource(drawableRes)
            view.imageTintList = ColorStateList.valueOf(tintColor)
            view.scaleType = ImageView.ScaleType.CENTER
            view.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            view.maxWidth = dp(context, iconDp)
            view.maxHeight = dp(context, iconDp)
            return view
        }

        fun iconButton(context: Context, drawableRes: Int, tintColor: Int, iconDp: Int): ImageButton {
            val button = ImageButton(context)
            button.setImageResource(drawableRes)
            button.imageTintList = ColorStateList.valueOf(tintColor)
            button.scaleType = ImageView.ScaleType.CENTER
            button.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            button.minimumWidth = 0
            button.minimumHeight = 0
            button.setBackgroundColor(Color.TRANSPARENT)
            button.adjustViewBounds = false
            button.maxWidth = dp(context, iconDp)
            button.maxHeight = dp(context, iconDp)
            return button
        }

        fun miniCoverView(context: Context, index: Int): TextView {
            val view = TextView(context)
            view.text = index.toString()
            view.setTextColor(LABEL3)
            view.textSize = 24f
            view.gravity = Gravity.CENTER
            view.setTypeface(view.typeface, Typeface.BOLD)
            view.background = coverBackground(context, false)
            return view
        }

        fun coverView(context: Context, coverUri: String?, sizeDp: Int, selected: Boolean): FrameLayout {
            val cover = FrameLayout(context)
            cover.background = coverBackground(context, selected)
            cover.clipToOutline = true

            if (!TextUtils.isEmpty(coverUri)) {
                val image = ImageView(context)
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                try {
                    image.setImageURI(Uri.parse(coverUri.orEmpty()))
                } catch (_: SecurityException) {
                } catch (_: IllegalArgumentException) {
                }
                cover.addView(
                    image,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            } else {
                val placeholder = iconView(
                    context,
                    R.drawable.ic_music_note,
                    LABEL3,
                    max(22, sizeDp / 2),
                )
                cover.addView(
                    placeholder,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            return cover
        }

        fun subtitleLineText(context: Context, color: Int, textSize: Float): TextView {
            val view = TextView(context)
            view.setTextColor(color)
            view.textSize = textSize
            view.gravity = Gravity.LEFT
            view.maxLines = 4
            view.ellipsize = TextUtils.TruncateAt.END
            view.includeFontPadding = false
            return view
        }

        fun transparentTextButton(context: Context, text: String?): Button {
            val button = Button(context)
            button.text = text
            button.isAllCaps = false
            button.textSize = 22f
            button.setTypeface(button.typeface, Typeface.BOLD)
            button.setTextColor(LABEL)
            button.setPadding(0, 0, 0, 0)
            button.setBackgroundColor(Color.TRANSPARENT)
            return button
        }

        fun compactButton(context: Context, text: String?): Button {
            val button = Button(context)
            button.text = text
            button.isAllCaps = false
            button.textSize = 15f
            button.setTextColor(LABEL)
            button.minHeight = dp(context, 42)
            button.background = cardBackground(context, GRAY6, SEPARATOR, 1, R_CARD)
            return button
        }

        fun rowWithDivider(context: Context, row: View?, leftInsetDp: Int): View {
            val container = LinearLayout(context)
            container.orientation = LinearLayout.VERTICAL
            container.setBackgroundColor(Color.TRANSPARENT)
            container.addView(row, matchWrap())
            container.addView(listDivider(context, leftInsetDp))
            return container
        }

        fun listDivider(context: Context, leftInsetDp: Int): View {
            val divider = View(context)
            divider.setBackgroundColor(SEPARATOR)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                max(1, dp(context, 1)),
            )
            params.leftMargin = dp(context, leftInsetDp)
            params.rightMargin = dp(context, 10)
            divider.layoutParams = params
            return divider
        }

        fun materialBackground(context: Context, fillColor: Int, radiusDp: Int): GradientDrawable {
            return cardBackground(context, fillColor, SEPARATOR, 1, radiusDp)
        }

        fun topRoundedBackground(context: Context, fillColor: Int, radiusDp: Int): GradientDrawable {
            val drawable = GradientDrawable()
            drawable.setColor(fillColor)
            val radius = dp(context, radiusDp).toFloat()
            drawable.cornerRadii = floatArrayOf(
                radius,
                radius,
                radius,
                radius,
                0f,
                0f,
                0f,
                0f,
            )
            return drawable
        }

        fun cardBackground(
            context: Context,
            fillColor: Int,
            strokeColor: Int,
            strokeDp: Int,
            radiusDp: Int,
        ): GradientDrawable {
            val drawable = GradientDrawable()
            drawable.setColor(fillColor)
            drawable.cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeDp > 0) {
                drawable.setStroke(dp(context, strokeDp), strokeColor)
            }
            return drawable
        }

        private fun coverBackground(context: Context, selected: Boolean): GradientDrawable {
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(GRAY5, GRAY4),
            )
            drawable.cornerRadius = dp(context, R_THUMB).toFloat()
            drawable.setStroke(dp(context, 1), if (selected) ACCENT else SEPARATOR)
            return drawable
        }

        fun dp(context: Context, value: Int): Int {
            return Math.round(value * context.resources.displayMetrics.density)
        }
    }
}
