package com.andureh.reelbrake

/** Where a Reels viewer was opened from. */
enum class Source { REELS_TAB, FEED, DIRECT, OTHER }

/** What to do with Reels opened from a given source. */
enum class Mode { OFF, BRAKE, BLOCK }

data class Config(
    val enabled: Boolean = true,
    val modes: Map<Source, Mode> = DEFAULT_MODES,
    /** Reels allowed per viewer session before the brake shows up. */
    val freeReels: Int = 1,
    /** First countdown on "one more"; grows with every extra reel. */
    val baseWaitSec: Int = 10,
    /** Backstop: after this long on the same reel, assume a swipe went undetected. */
    val maxReelSec: Int = 120,
) {
    fun mode(source: Source): Mode = if (!enabled) Mode.OFF else modes[source] ?: Mode.BRAKE

    companion object {
        val DEFAULT_MODES = mapOf(
            Source.REELS_TAB to Mode.BLOCK,
            Source.FEED to Mode.BRAKE,
            Source.DIRECT to Mode.BRAKE,
            Source.OTHER to Mode.BRAKE,
        )
    }
}

/** What the service read from Instagram's active window. */
data class Screen(
    val viewerVisible: Boolean,
    val reelsTabSelected: Boolean,
    /** Screen the user is on, or null when nothing recognisable is visible. */
    val context: Source?,
)

sealed interface Action {
    data object None : Action
    data class ShowBrake(val reelNumber: Int, val source: Source, val waitSec: Int, val secondsInReels: Int) : Action
    data object HideBrake : Action
    /** Leave the viewer with a global Back (the user returns to where they came from). */
    data object Back : Action
    /** Leave the Reels tab by selecting the Home tab. */
    data object GoHome : Action
}

enum class Stat { REEL, BRAKE, EXIT, CONTINUE }

/**
 * Decides when to interrupt Reels. Pure Kotlin so it can be unit tested; the accessibility
 * service feeds it observations and executes the returned [Action]s.
 */
class ReelPolicy(
    private val config: () -> Config,
    private val onStat: (Stat, Source) -> Unit = { _, _ -> },
) {
    private var lastContext = Source.OTHER

    var inViewer = false
        private set
    var source = Source.OTHER
        private set
    var reelsSeen = 0
        private set
    var braking = false
        private set

    private var allowed = 0
    private var enteredAt = 0L
    private var reelStartedAt = 0L
    private var lastScrollAt = 0L
    private var lastIndex = -1

    private var exiting = false
    private var exitAttempts = 0
    private var lastExitAt = 0L

    private var level = 0
    private var lastContinueAt = Long.MIN_VALUE / 2
    /** Set when the user escaped a brake by leaving the viewer some other way. */
    private var pendingBrakeAt = Long.MIN_VALUE / 2

    fun onScreen(now: Long, screen: Screen): Action {
        if (!screen.viewerVisible && !screen.reelsTabSelected) {
            screen.context?.let { lastContext = it }
            return leaveViewer(now)
        }

        val enteringTab = screen.reelsTabSelected && source != Source.REELS_TAB
        if (!inViewer || enteringTab) return enterViewer(now, if (screen.reelsTabSelected) Source.REELS_TAB else lastContext)

        if (exiting) return retryExit(now)
        return Action.None
    }

    /** A scroll inside the Reels pager. [index] is the event's fromIndex, or -1 if unknown. */
    fun onViewerScroll(now: Long, index: Int): Action {
        if (!inViewer || braking || exiting) return Action.None
        val pastGrace = now - enteredAt > ENTRY_GRACE_MS
        val newReel = if (index >= 0) {
            val changed = if (lastIndex >= 0) index != lastIndex else pastGrace
            lastIndex = index
            changed
        } else {
            val fresh = pastGrace && now - lastScrollAt > SWIPE_GAP_MS
            lastScrollAt = now
            fresh
        }
        if (!newReel) return Action.None
        return nextReel(now)
    }

    /** Called periodically while in a viewer; catches swipes the scroll events missed. */
    fun onTick(now: Long): Action {
        if (!inViewer || braking || exiting) return Action.None
        if (config().mode(source) != Mode.BRAKE) return Action.None
        if (now - reelStartedAt < config().maxReelSec * 1000L) return Action.None
        return nextReel(now)
    }

    fun onContinue(now: Long): Action {
        if (!braking) return Action.None
        braking = false
        level++
        lastContinueAt = now
        allowed = reelsSeen
        reelStartedAt = now
        onStat(Stat.CONTINUE, source)
        return Action.HideBrake
    }

    /** The user chose to leave. The caller hides the brake before running the returned action. */
    fun onExit(now: Long): Action {
        braking = false
        onStat(Stat.EXIT, source)
        return startExit(now)
    }

    /** Another app took the foreground. */
    fun onLeftInstagram(now: Long): Action = leaveViewer(now)

    private fun enterViewer(now: Long, from: Source): Action {
        inViewer = true
        source = from
        reelsSeen = 1
        allowed = config().freeReels
        enteredAt = now
        reelStartedAt = now
        lastScrollAt = now
        lastIndex = -1
        exiting = false
        exitAttempts = 0
        return when (config().mode(source)) {
            Mode.OFF -> Action.None
            Mode.BLOCK -> {
                onStat(Stat.EXIT, source)
                startExit(now)
            }
            Mode.BRAKE -> {
                onStat(Stat.REEL, source)
                if (reelsSeen > allowed || now - pendingBrakeAt < RESUME_BRAKE_MS) brake(now) else Action.None
            }
        }
    }

    private fun leaveViewer(now: Long): Action {
        val wasBraking = braking
        // Escaping a brake (gesture Back, Home...) re-arms it if the user comes straight back.
        if (wasBraking) {
            pendingBrakeAt = now
            onStat(Stat.EXIT, source)
        }
        inViewer = false
        braking = false
        exiting = false
        exitAttempts = 0
        return if (wasBraking) Action.HideBrake else Action.None
    }

    private fun nextReel(now: Long): Action {
        reelsSeen++
        reelStartedAt = now
        return when (config().mode(source)) {
            Mode.OFF -> Action.None
            Mode.BLOCK -> startExit(now)
            Mode.BRAKE -> {
                onStat(Stat.REEL, source)
                if (reelsSeen > allowed) brake(now) else Action.None
            }
        }
    }

    private fun brake(now: Long): Action {
        braking = true
        pendingBrakeAt = Long.MIN_VALUE / 2
        if (now - lastContinueAt > LEVEL_DECAY_MS) level = 0
        val wait = (config().baseWaitSec * (level + 1)).coerceAtMost(MAX_WAIT_SEC)
        onStat(Stat.BRAKE, source)
        return Action.ShowBrake(reelsSeen, source, wait, ((now - enteredAt) / 1000).toInt())
    }

    private fun startExit(now: Long): Action {
        exiting = true
        exitAttempts = 1
        lastExitAt = now
        return if (source == Source.REELS_TAB) Action.GoHome else Action.Back
    }

    private fun retryExit(now: Long): Action {
        if (now - lastExitAt < EXIT_RETRY_MS) return Action.None
        if (exitAttempts >= MAX_EXIT_ATTEMPTS) {
            exiting = false
            return Action.None
        }
        exitAttempts++
        lastExitAt = now
        return if (source == Source.REELS_TAB) Action.GoHome else Action.Back
    }

    companion object {
        const val ENTRY_GRACE_MS = 1_000L
        const val SWIPE_GAP_MS = 700L
        const val EXIT_RETRY_MS = 800L
        const val MAX_EXIT_ATTEMPTS = 3
        const val LEVEL_DECAY_MS = 30 * 60_000L
        const val RESUME_BRAKE_MS = 2 * 60_000L
        const val MAX_WAIT_SEC = 60
    }
}
