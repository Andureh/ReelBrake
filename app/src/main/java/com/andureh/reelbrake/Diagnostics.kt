package com.andureh.reelbrake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * In-memory log of what the service saw, so detection can be checked (and fixed after an
 * Instagram update) without adb. Holds resource IDs only, never screen text.
 */
object Diagnostics {
    private const val MAX_LINES = 40
    private val lines = ArrayDeque<String>()
    private var lastScreenLine = ""

    /** Latest screen summary, or null before Instagram has been seen. */
    private val _live = MutableStateFlow<String?>(null)
    val live: StateFlow<String?> = _live

    @Synchronized
    fun screen(screen: Screen, ids: List<String>, policy: ReelPolicy) {
        val summary = "viewer=${screen.viewerVisible} reelsTab=${screen.reelsTabSelected} " +
            "screen=${screen.context ?: "?"} | session: source=${policy.source} reels=${policy.reelsSeen} braking=${policy.braking}"
        _live.value = summary
        val line = "$summary\n    ids: ${ids.joinToString(" ")}"
        if (line != lastScreenLine) {
            lastScreenLine = line
            add(line)
        }
    }

    @Synchronized
    fun event(text: String) = add(text)

    @Synchronized
    fun report(): String = buildString {
        appendLine("ReelBrake ${BuildConfig.VERSION_NAME} diagnostics (view IDs only, no screen text)")
        lines.forEach(::appendLine)
    }

    private fun add(text: String) {
        lines.addLast("${LocalTime.now().truncatedTo(ChronoUnit.SECONDS)} $text")
        while (lines.size > MAX_LINES) lines.removeFirst()
    }
}
