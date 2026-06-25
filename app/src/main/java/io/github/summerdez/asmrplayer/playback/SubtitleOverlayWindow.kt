package io.github.summerdez.asmrplayer.playback

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.summerdez.asmrplayer.R

class SubtitleOverlayWindow(context: Context?, private val listener: Listener?) {
    interface Listener {
        fun onPrevious()

        fun onPlayPause()

        fun onNext()

        fun onLock()
    }

    enum class ShowResult {
        SHOWN,
        MISSING_PERMISSION,
        FAILED,
    }

    private val context: Context = context!!
    private val windowManager: WindowManager? = this.context.getSystemService(WindowManager::class.java)
    private var rootView: LinearLayout? = null
    private var topRow: LinearLayout? = null
    private var controlsRow: LinearLayout? = null
    private var textView: TextView? = null
    private var playPauseButton: ImageButton? = null
    private var params: WindowManager.LayoutParams? = null
    private var playing = false
    private var locked = false

    fun hasPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun show(text: String?, playing: Boolean, locked: Boolean): ShowResult {
        this.playing = playing
        this.locked = locked
        if (!hasPermission()) {
            return ShowResult.MISSING_PERMISSION
        }
        val manager = windowManager ?: return ShowResult.FAILED
        if (rootView != null) {
            updateText(text)
            applyPlayingState()
            applyLockedState()
            updateLayoutFlags()
            return ShowResult.SHOWN
        }

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setOnTouchListener(DragTouchListener())
        rootView = root

        val top = LinearLayout(context)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        topRow = top
        val mark = iconView(R.drawable.ic_music_note, 0xFFE6E3F2.toInt(), 22)
        top.addView(mark, LinearLayout.LayoutParams(dp(32), dp(30)))
        val spacer = View(context)
        top.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        val lockButton = overlayButton(R.drawable.ic_lock, 32, false)
        lockButton.setOnClickListener { listener!!.onLock() }
        top.addView(lockButton, LinearLayout.LayoutParams(dp(34), dp(32)))
        root.addView(
            top,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val subtitleText = TextView(context)
        subtitleText.setTextColor(Color.WHITE)
        subtitleText.textSize = 18f
        subtitleText.typeface = Typeface.DEFAULT_BOLD
        subtitleText.gravity = Gravity.CENTER
        subtitleText.maxLines = 4
        subtitleText.ellipsize = TextUtils.TruncateAt.END
        subtitleText.includeFontPadding = false
        subtitleText.setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0xDD000000.toInt())
        textView = subtitleText
        val textParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        textParams.topMargin = dp(6)
        textParams.bottomMargin = dp(10)
        root.addView(subtitleText, textParams)

        val controls = LinearLayout(context)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        controlsRow = controls
        val previousButton = overlayButton(R.drawable.ic_skip_previous, 36, true)
        previousButton.setOnClickListener { listener!!.onPrevious() }
        controls.addView(previousButton, LinearLayout.LayoutParams(dp(38), dp(38)))

        playPauseButton = overlayButton(R.drawable.ic_pause, 44, true)
        playPauseButton!!.setOnClickListener { listener!!.onPlayPause() }
        val playParams = LinearLayout.LayoutParams(dp(46), dp(46))
        playParams.leftMargin = dp(16)
        playParams.rightMargin = dp(16)
        controls.addView(playPauseButton, playParams)

        val nextButton = overlayButton(R.drawable.ic_skip_next, 36, true)
        nextButton.setOnClickListener { listener!!.onNext() }
        controls.addView(nextButton, LinearLayout.LayoutParams(dp(38), dp(38)))
        root.addView(
            controls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        updateText(text)
        applyPlayingState()
        applyLockedState()

        val layoutParams = WindowManager.LayoutParams(
            panelWidth(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags(),
            PixelFormat.TRANSLUCENT,
        )
        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutParams.y = dp(96)
        params = layoutParams

        return try {
            manager.addView(root, layoutParams)
            ShowResult.SHOWN
        } catch (error: RuntimeException) {
            clearViews()
            ShowResult.FAILED
        }
    }

    fun updateText(text: String?) {
        val subtitleView = textView ?: return
        val subtitleText = text ?: ""
        subtitleView.text = subtitleText
        subtitleView.visibility = if (TextUtils.isEmpty(subtitleText)) View.GONE else View.VISIBLE
    }

    fun setPlaying(playing: Boolean) {
        this.playing = playing
        applyPlayingState()
    }

    fun setLocked(locked: Boolean) {
        this.locked = locked
        applyLockedState()
        updateLayoutFlags()
    }

    fun remove() {
        val root = rootView
        val manager = windowManager
        if (root == null || manager == null) {
            clearViews()
            return
        }
        try {
            manager.removeView(root)
        } catch (ignored: RuntimeException) {
        }
        clearViews()
    }

    private fun applyPlayingState() {
        playPauseButton?.setImageResource(
            if (playing) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            },
        )
    }

    private fun applyLockedState() {
        val root = rootView ?: return
        root.minimumWidth = panelWidth()
        root.setPadding(dp(12), dp(10), dp(12), dp(12))
        root.background = if (locked) transparentBackground() else panelBackground()
        topRow?.visibility = if (locked) View.INVISIBLE else View.VISIBLE
        controlsRow?.visibility = if (locked) View.INVISIBLE else View.VISIBLE
    }

    private fun updateLayoutFlags() {
        val root = rootView ?: return
        val layoutParams = params ?: return
        val manager = windowManager ?: return
        layoutParams.flags = windowFlags()
        layoutParams.width = panelWidth()
        try {
            manager.updateViewLayout(root, layoutParams)
        } catch (ignored: RuntimeException) {
        }
    }

    private fun windowFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (locked) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    private fun iconView(drawableRes: Int, tintColor: Int, iconDp: Int): ImageView {
        val view = ImageView(context)
        view.setImageResource(drawableRes)
        view.imageTintList = ColorStateList.valueOf(tintColor)
        view.scaleType = ImageView.ScaleType.CENTER
        view.setPadding(dp(8), dp(8), dp(8), dp(8))
        view.maxWidth = dp(iconDp)
        view.maxHeight = dp(iconDp)
        return view
    }

    private fun overlayButton(drawableRes: Int, sizeDp: Int, outlined: Boolean): ImageButton {
        val button = ImageButton(context)
        button.setImageResource(drawableRes)
        button.imageTintList = ColorStateList.valueOf(0xFFEDEAF6.toInt())
        button.scaleType = ImageView.ScaleType.CENTER
        button.setPadding(dp(8), dp(8), dp(8), dp(8))
        button.background = if (outlined) circleBackground() else null
        button.minimumWidth = 0
        button.minimumHeight = 0
        button.maxWidth = dp(sizeDp)
        button.maxHeight = dp(sizeDp)
        return button
    }

    private fun panelWidth(): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        return Math.max(dp(300), Math.min(screenWidth - dp(80), dp(420)))
    }

    private fun panelBackground(): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(0xAA101017.toInt())
        drawable.cornerRadius = dp(8).toFloat()
        return drawable
    }

    private fun transparentBackground(): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(Color.TRANSPARENT)
        drawable.cornerRadius = dp(8).toFloat()
        return drawable
    }

    private fun circleBackground(): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(0x22101017)
        drawable.setStroke(dp(1), 0xAAEDEAF6.toInt())
        return drawable
    }

    private fun clearViews() {
        rootView = null
        topRow = null
        controlsRow = null
        textView = null
        playPauseButton = null
        params = null
    }

    private fun dp(value: Int): Int {
        return Math.round(value * context.resources.displayMetrics.density)
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var moved = false

        override fun onTouch(view: View?, event: MotionEvent?): Boolean {
            if (locked || params == null || rootView == null || windowManager == null) {
                return false
            }
            val layoutParams = params ?: return false
            val root = rootView ?: return false
            val manager = windowManager
            val touchEvent = event!!
            when (touchEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = touchEvent.rawX
                    initialTouchY = touchEvent.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.round(touchEvent.rawX - initialTouchX)
                    val dy = Math.round(touchEvent.rawY - initialTouchY)
                    if (Math.abs(dx) > dp(DRAG_SLOP_DP) || Math.abs(dy) > dp(DRAG_SLOP_DP)) {
                        moved = true
                    }
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    manager.updateViewLayout(root, layoutParams)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        view!!.performClick()
                    }
                    return true
                }
                else -> return false
            }
        }
    }

    private companion object {
        private const val DRAG_SLOP_DP = 4
    }
}
