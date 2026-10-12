package com.andureh.reelbrake

import android.content.Context
import java.time.LocalDate

object Prefs {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(context: Context): Config {
        val p = prefs(context)
        val d = Config()
        return Config(
            enabled = p.getBoolean("enabled", d.enabled),
            modes = Source.entries.associateWith { s ->
                p.getString("mode_${s.name}", null)?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: d.mode(s)
            },
            freeReels = p.getInt("free_reels", d.freeReels),
            baseWaitSec = p.getInt("base_wait", d.baseWaitSec),
            maxReelSec = p.getInt("max_reel", d.maxReelSec),
        )
    }

    fun save(context: Context, c: Config) {
        prefs(context).edit().apply {
            putBoolean("enabled", c.enabled)
            Source.entries.forEach { putString("mode_${it.name}", c.mode(it).name) }
            putInt("free_reels", c.freeReels)
            putInt("base_wait", c.baseWaitSec)
            putInt("max_reel", c.maxReelSec)
        }.apply()
    }
}

/** Today's counters, kept per day so the screen can show a running tally. */
data class DayStats(val reels: Int = 0, val brakes: Int = 0, val exits: Int = 0, val continues: Int = 0)

object Stats {
    private fun prefs(context: Context) = context.getSharedPreferences("stats", Context.MODE_PRIVATE)
    private fun key(stat: Stat) = "${LocalDate.now()}_${stat.name}"

    fun record(context: Context, stat: Stat) {
        val p = prefs(context)
        val today = LocalDate.now().toString()
        val edit = p.edit()
        // Keep only today's keys around
        p.all.keys.filterNot { it.startsWith(today) }.forEach(edit::remove)
        edit.putInt(key(stat), p.getInt(key(stat), 0) + 1).apply()
    }

    fun today(context: Context): DayStats {
        val p = prefs(context)
        return DayStats(
            reels = p.getInt(key(Stat.REEL), 0),
            brakes = p.getInt(key(Stat.BRAKE), 0),
            exits = p.getInt(key(Stat.EXIT), 0),
            continues = p.getInt(key(Stat.CONTINUE), 0),
        )
    }
}
