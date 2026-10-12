package com.andureh.reelbrake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelPolicyTest {
    private var config = Config()
    private val stats = mutableListOf<Stat>()
    private val policy = ReelPolicy({ config }, { stat, _ -> stats += stat })

    private val dmThread = Screen(viewerVisible = false, reelsTabSelected = false, context = Source.DIRECT)
    private val feed = Screen(viewerVisible = false, reelsTabSelected = false, context = Source.FEED)
    private val viewer = Screen(viewerVisible = true, reelsTabSelected = false, context = null)
    private val reelsTab = Screen(viewerVisible = true, reelsTabSelected = true, context = Source.REELS_TAB)

    private fun openFrom(screen: Screen, at: Long = 0): Action {
        policy.onScreen(at, screen)
        return policy.onScreen(at + 100, viewer)
    }

    @Test
    fun `first reel from DMs is free, the swipe to the second brakes`() {
        assertEquals(Action.None, openFrom(dmThread))
        val brake = policy.onViewerScroll(5_000, index = 1)
        assertTrue(brake is Action.ShowBrake)
        brake as Action.ShowBrake
        assertEquals(2, brake.reelNumber)
        assertEquals(Source.DIRECT, brake.source)
        assertEquals(10, brake.waitSec)
    }

    @Test
    fun `source is remembered from the feed`() {
        openFrom(feed)
        val brake = policy.onViewerScroll(5_000, index = 1) as Action.ShowBrake
        assertEquals(Source.FEED, brake.source)
    }

    @Test
    fun `scrolls without an index count once per swipe`() {
        config = config.copy(freeReels = 2)
        openFrom(dmThread)
        // Layout scrolls right after opening are ignored
        assertEquals(Action.None, policy.onViewerScroll(400, index = -1))
        assertEquals(Action.None, policy.onViewerScroll(3_000, -1))
        assertEquals(Action.None, policy.onViewerScroll(3_100, -1))
        assertEquals(Action.None, policy.onViewerScroll(3_300, -1))
        assertEquals(2, policy.reelsSeen)
        assertTrue(policy.onViewerScroll(6_000, -1) is Action.ShowBrake)
    }

    @Test
    fun `index changes count, repeated index does not`() {
        config = config.copy(freeReels = 3)
        openFrom(dmThread)
        policy.onViewerScroll(300, index = 0) // baseline inside the grace period
        policy.onViewerScroll(3_000, index = 0)
        assertEquals(1, policy.reelsSeen)
        policy.onViewerScroll(3_100, index = 1)
        policy.onViewerScroll(3_200, index = 1)
        assertEquals(2, policy.reelsSeen)
    }

    @Test
    fun `each continue allows one reel and makes the next wait longer`() {
        openFrom(dmThread)
        policy.onViewerScroll(5_000, 1)
        assertEquals(Action.HideBrake, policy.onContinue(15_000))
        assertEquals(Action.None, policy.onScreen(15_100, viewer))
        val second = policy.onViewerScroll(20_000, 2) as Action.ShowBrake
        assertEquals(20, second.waitSec)
        policy.onContinue(40_000)
        val third = policy.onViewerScroll(45_000, 3) as Action.ShowBrake
        assertEquals(30, third.waitSec)
    }

    @Test
    fun `wait goes back to base after a long break`() {
        openFrom(dmThread)
        policy.onViewerScroll(5_000, 1)
        policy.onContinue(15_000)
        policy.onScreen(16_000, dmThread)
        val later = 15_000 + ReelPolicy.LEVEL_DECAY_MS + 1
        openFrom(dmThread, later)
        val brake = policy.onViewerScroll(later + 5_000, 1) as Action.ShowBrake
        assertEquals(10, brake.waitSec)
    }

    @Test
    fun `exit goes back and retries while the viewer is still there`() {
        openFrom(dmThread)
        policy.onViewerScroll(5_000, 1)
        assertEquals(Action.Back, policy.onExit(8_000))
        assertEquals(Action.None, policy.onScreen(8_300, viewer))
        assertEquals(Action.Back, policy.onScreen(9_000, viewer))
        assertEquals(Action.None, policy.onScreen(9_200, dmThread))
        // Opening another reel afterwards starts a fresh session
        assertEquals(Action.None, policy.onScreen(30_000, viewer))
        assertEquals(1, policy.reelsSeen)
    }

    @Test
    fun `exit gives up after a few attempts`() {
        openFrom(dmThread)
        policy.onViewerScroll(5_000, 1)
        policy.onExit(8_000)
        assertEquals(Action.Back, policy.onScreen(9_000, viewer))
        assertEquals(Action.Back, policy.onScreen(10_000, viewer))
        assertEquals(Action.None, policy.onScreen(11_000, viewer))
        assertEquals(Action.None, policy.onScreen(12_000, viewer))
    }

    @Test
    fun `reels tab is blocked by going home`() {
        policy.onScreen(0, feed)
        assertEquals(Action.GoHome, policy.onScreen(100, reelsTab))
        assertEquals(Action.None, policy.onScreen(200, reelsTab))
        assertEquals(Action.GoHome, policy.onScreen(1_000, reelsTab))
        assertEquals(Action.None, policy.onScreen(1_200, feed))
    }

    @Test
    fun `reels tab on brake mode behaves like the other sources`() {
        config = config.copy(modes = config.modes + (Source.REELS_TAB to Mode.BRAKE))
        assertEquals(Action.None, policy.onScreen(0, reelsTab))
        val brake = policy.onViewerScroll(5_000, 1) as Action.ShowBrake
        assertEquals(Source.REELS_TAB, brake.source)
        assertEquals(Action.GoHome, policy.onExit(6_000))
    }

    @Test
    fun `zero free reels brakes right away`() {
        config = config.copy(freeReels = 0)
        assertTrue(openFrom(feed) is Action.ShowBrake)
    }

    @Test
    fun `off mode never interferes`() {
        config = config.copy(modes = config.modes + (Source.DIRECT to Mode.OFF))
        openFrom(dmThread)
        repeat(5) { assertEquals(Action.None, policy.onViewerScroll(5_000L * (it + 1), it + 1)) }
        assertEquals(Action.None, policy.onTick(1_000_000))
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `master switch disables everything`() {
        config = config.copy(enabled = false)
        assertEquals(Action.None, policy.onScreen(0, reelsTab))
        openFrom(dmThread, 1_000)
        assertEquals(Action.None, policy.onViewerScroll(9_000, 1))
    }

    @Test
    fun `backstop brakes when no swipe is detected for too long`() {
        openFrom(dmThread)
        assertEquals(Action.None, policy.onTick(60_000))
        assertTrue(policy.onTick(121_000) is Action.ShowBrake)
    }

    @Test
    fun `escaping the brake re-arms it on a quick return`() {
        openFrom(dmThread)
        policy.onViewerScroll(5_000, 1)
        assertEquals(Action.HideBrake, policy.onLeftInstagram(6_000))
        assertTrue(openFrom(dmThread, 20_000) is Action.ShowBrake)
    }

    @Test
    fun `escape is forgotten after a while`() {
        openFrom(dmThread)
        policy.onViewerScroll(5_000, 1)
        policy.onLeftInstagram(6_000)
        assertEquals(Action.None, openFrom(dmThread, 6_000 + ReelPolicy.RESUME_BRAKE_MS + 1))
    }

    @Test
    fun `stats count reels, brakes and choices`() {
        openFrom(dmThread)
        policy.onViewerScroll(5_000, 1)
        policy.onContinue(15_000)
        policy.onViewerScroll(20_000, 2)
        policy.onExit(30_000)
        assertEquals(
            listOf(Stat.REEL, Stat.REEL, Stat.BRAKE, Stat.CONTINUE, Stat.REEL, Stat.BRAKE, Stat.EXIT),
            stats,
        )
    }
}
