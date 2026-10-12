package com.andureh.reelbrake

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class ReelBrakeService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var policy: ReelPolicy
    private lateinit var overlay: BrakeOverlay

    private var lastInspectAt = 0L
    private var inspectScheduled = false
    private var lastScrollLine = ""
    private var pendingBrake: Action.ShowBrake? = null

    private val delayedInspect = Runnable {
        inspectScheduled = false
        inspect()
    }
    /**
     * Shows a brake only if the viewer is still there a moment later: closing a reel makes
     * Instagram fire pager scroll events that would otherwise count as a swipe.
     */
    private val showBrake = Runnable {
        val brake = pendingBrake ?: return@Runnable
        pendingBrake = null
        inspect()
        if (policy.braking) {
            Diagnostics.event("brake shown")
            overlay.show(brake)
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!policy.inViewer) return
            perform(policy.onTick(now()))
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        policy = ReelPolicy(
            config = { Prefs.load(this) },
            onStat = { stat, _ -> Stats.record(this, stat) },
        )
        overlay = BrakeOverlay(
            context = this,
            onExit = { perform(policy.onExit(now())) },
            onContinue = { perform(policy.onContinue(now())) },
        )
        Diagnostics.event("service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::policy.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != InstagramReader.PACKAGE) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != packageName) checkLeftInstagram()
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> inspect()
            else -> requestInspect()
        }
    }

    private fun checkLeftInstagram() {
        val root = rootInActiveWindow ?: return
        val active = root.packageName?.toString()
        @Suppress("DEPRECATION") root.recycle()
        // The notification shade and our own overlay don't count as leaving
        if (active == null || active == InstagramReader.PACKAGE || active == packageName || active == SYSTEM_UI) return
        perform(policy.onLeftInstagram(now()))
    }

    private fun onScroll(event: AccessibilityEvent) {
        val source = event.source ?: return
        val id = InstagramReader.shortId(source.viewIdResourceName)
        val fromViewer = try {
            InstagramReader.isViewerScroll(source)
        } finally {
            @Suppress("DEPRECATION") source.recycle()
        }
        if (policy.inViewer) {
            val line = "scroll id=${id ?: "(none)"} viewer=$fromViewer"
            if (line != lastScrollLine) {
                lastScrollLine = line
                Diagnostics.event(line)
            }
        }
        if (fromViewer) perform(policy.onViewerScroll(now(), event.fromIndex))
    }

    /** Content changes fire constantly while a video plays, so inspections are throttled. */
    private fun requestInspect() {
        val elapsed = now() - lastInspectAt
        if (elapsed >= INSPECT_INTERVAL_MS) {
            inspect()
        } else if (!inspectScheduled) {
            inspectScheduled = true
            handler.postDelayed(delayedInspect, INSPECT_INTERVAL_MS - elapsed)
        }
    }

    private fun inspect() {
        lastInspectAt = now()
        val root = rootInActiveWindow ?: return
        try {
            // While the overlay has been touched it is the active window; Instagram is behind it.
            if (root.packageName?.toString() != InstagramReader.PACKAGE) return
            val result = InstagramReader.read(root)
            val action = policy.onScreen(now(), result.screen)
            Diagnostics.screen(result.screen, result.ids, policy)
            perform(action)
        } finally {
            @Suppress("DEPRECATION") root.recycle()
        }
        if (policy.inViewer) {
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, TICK_MS)
        }
    }

    private fun perform(action: Action) {
        when (action) {
            Action.None -> Unit
            is Action.ShowBrake -> {
                Diagnostics.event("brake: reel ${action.reelNumber}, source ${action.source}, wait ${action.waitSec}s")
                pendingBrake = action
                handler.removeCallbacks(showBrake)
                handler.postDelayed(showBrake, BRAKE_CONFIRM_MS)
            }
            Action.HideBrake -> hideBrake()
            Action.Back -> {
                hideBrake()
                Diagnostics.event("leaving with Back")
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, ACTION_DELAY_MS)
            }
            Action.GoHome -> {
                hideBrake()
                Diagnostics.event("leaving to Home")
                handler.postDelayed({
                    val root = rootInActiveWindow
                    val clicked = root != null && root.packageName == InstagramReader.PACKAGE && InstagramReader.clickHomeTab(root)
                    if (!clicked) performGlobalAction(GLOBAL_ACTION_BACK)
                }, ACTION_DELAY_MS)
            }
        }
    }

    private fun hideBrake() {
        handler.removeCallbacks(showBrake)
        pendingBrake = null
        overlay.hide()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    private fun now() = SystemClock.uptimeMillis()

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"
        private const val INSPECT_INTERVAL_MS = 400L
        private const val TICK_MS = 5_000L
        private const val ACTION_DELAY_MS = 120L
        private const val BRAKE_CONFIRM_MS = 350L
    }
}
