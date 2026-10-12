package com.andureh.reelbrake

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen pause drawn on top of Instagram. Uses an accessibility overlay window, so no
 * "draw over other apps" permission is needed.
 */
class BrakeOverlay(
    private val context: Context,
    private val onExit: () -> Unit,
    private val onContinue: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var focusRequest: AudioFocusRequest? = null

    val isShowing get() = view != null

    fun show(brake: Action.ShowBrake) {
        hide()
        val root = build(brake)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(root, params)
        view = root
        pauseVideo()
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        focusRequest?.let(audio::abandonAudioFocusRequest)
        focusRequest = null
    }

    /** Taking audio focus makes Instagram pause or mute the reel behind the overlay. */
    private fun pauseVideo() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        audio.requestAudioFocus(request)
        focusRequest = request
    }

    private fun build(brake: Action.ShowBrake): View {
        val accent = Color.parseColor("#FF7A59")
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F5101216"))
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true // swallow touches so nothing reaches Instagram
        }
        root.addView(text(context.getString(R.string.brake_title), 40f, Color.WHITE, bold = true))
        root.addView(space(12))
        root.addView(text(headline(brake), 18f, Color.parseColor("#E6E6EA")))
        if (brake.secondsInReels >= 60) {
            root.addView(text(context.getString(R.string.brake_time, brake.secondsInReels / 60), 15f, Color.parseColor("#E6E6EA")))
        }
        root.addView(space(8))
        root.addView(text(context.getString(R.string.brake_question), 15f, Color.parseColor("#9A9AA5")))
        root.addView(space(40))

        root.addView(button(context.getString(R.string.brake_exit), accent, Color.WHITE).apply { setOnClickListener { onExit() } })
        root.addView(space(12))

        val more = button("", Color.parseColor("#2A2C33"), Color.parseColor("#77777F")).apply {
            isEnabled = false
            setOnClickListener { onContinue() }
        }
        root.addView(more)

        var remaining = brake.waitSec
        val tick = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    more.text = context.getString(R.string.brake_more_countdown, remaining)
                    remaining--
                    handler.postDelayed(this, 1_000)
                } else {
                    more.text = context.getString(R.string.brake_more)
                    more.isEnabled = true
                    more.setTextColor(Color.parseColor("#C8C8D0"))
                }
            }
        }
        tick.run()
        return root
    }

    private fun headline(b: Action.ShowBrake): String {
        val res = when (b.source) {
            Source.REELS_TAB -> R.string.brake_headline_reels_tab
            Source.FEED -> R.string.brake_headline_feed
            Source.DIRECT -> R.string.brake_headline_direct
            Source.OTHER -> R.string.brake_headline_other
        }
        return context.getString(res, b.reelNumber)
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, bg: Int, fg: Int) = Button(context).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setTextColor(fg)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(bg)
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
    }

    private fun space(heightDp: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}
