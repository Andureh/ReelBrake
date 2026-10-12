# Changelog

## 1.1
- Fixed: after closing a reel, opening another one (even from a different chat) showed the pause right away as "reel #1". Closing the viewer made Instagram fire a scroll event that was counted as a swipe.
- The pause now only appears if you are still in the Reels viewer a moment after the swipe.
- Leaving while the pause is on screen no longer re-arms it for the next reel you open.

## 1.0
First release.
- Detects Instagram Reels opened from messages, the feed, the Reels tab and Explore/profiles.
- Per-source mode: Free, Pause or Block. Master on/off switch.
- Full-screen pause with "Leave Reels" and a "One more" button behind a growing countdown (10 s → 60 s).
- Free reels before the pause, base countdown and a time-based safety net are configurable.
- Today's counters: reels, pauses, exits and "one more".
- Diagnostics report (view IDs only) to fix detection after Instagram updates.
- Six languages: English, Portuguese, Spanish, French, German and Italian.
- No internet permission.
