package com.andureh.reelbrake

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads Instagram's view hierarchy. Only resource IDs and the selected state are used;
 * no text from the screen (messages, captions...) is read or stored.
 *
 * IDs verified against Instagram in October 2026. Instagram renames them now and then:
 * when detection breaks, use the diagnostics report in the app to find the new names.
 */
object InstagramReader {
    const val PACKAGE = "com.instagram.android"

    private const val VIEWER_ID = "clips_viewer_view_pager"
    private const val REELS_TAB_ID = "clips_tab"
    const val HOME_TAB_ID = "feed_tab"
    private val DIRECT_TAB_IDS = setOf("direct_tab", "inbox_tab")
    private val DIRECT_MARKERS = listOf(
        "direct_thread",
        "thread_message",
        "message_composer",
        "direct_inbox",
        "inbox_refreshable_thread_list_recyclerview",
    )
    private const val MAX_NODES = 1_500
    private const val MAX_DIAG_IDS = 60

    class Result(val screen: Screen, val ids: List<String>)

    fun read(root: AccessibilityNodeInfo): Result {
        var visited = 0
        var viewer = false
        var reelsTab = false
        var homeTab = false
        var directTab = false
        var otherTab = false
        var directMarker = false
        val ids = LinkedHashSet<String>()

        fun visit(node: AccessibilityNodeInfo, selectedAncestor: Boolean) {
            if (visited++ >= MAX_NODES) return
            val selected = selectedAncestor || node.isSelected
            val id = shortId(node.viewIdResourceName)
            if (id != null && node.isVisibleToUser) {
                if (ids.size < MAX_DIAG_IDS) ids += if (node.isSelected) "$id*" else id
                when {
                    id == VIEWER_ID -> viewer = true
                    id == REELS_TAB_ID -> if (selected) reelsTab = true
                    id == HOME_TAB_ID -> if (selected) homeTab = true
                    id in DIRECT_TAB_IDS -> if (selected) directTab = true
                    id.endsWith("_tab") -> if (selected) otherTab = true
                }
                if (DIRECT_MARKERS.any { id.contains(it) }) directMarker = true
            }
            for (i in 0 until node.childCount) {
                if (visited >= MAX_NODES) break
                val child = node.getChild(i) ?: continue
                try {
                    visit(child, selected)
                } finally {
                    @Suppress("DEPRECATION") child.recycle()
                }
            }
        }
        visit(root, false)

        val context = when {
            reelsTab -> Source.REELS_TAB
            directMarker || directTab -> Source.DIRECT
            homeTab -> Source.FEED
            otherTab -> Source.OTHER
            else -> null
        }
        return Result(Screen(viewerVisible = viewer, reelsTabSelected = reelsTab, context = context), ids.toList())
    }

    /** True when a scroll event came from the Reels pager (or its unnamed inner list). */
    fun isViewerScroll(source: AccessibilityNodeInfo): Boolean {
        val id = shortId(source.viewIdResourceName)
        if (id != null) return id.startsWith("clips_viewer")
        val parent = source.parent ?: return false
        return try {
            shortId(parent.viewIdResourceName)?.startsWith("clips_viewer") == true
        } finally {
            @Suppress("DEPRECATION") parent.recycle()
        }
    }

    /** Clicks the Home tab (or its nearest clickable ancestor). */
    fun clickHomeTab(root: AccessibilityNodeInfo): Boolean {
        val node = root.findAccessibilityNodeInfosByViewId("$PACKAGE:id/$HOME_TAB_ID").firstOrNull() ?: return false
        var current: AccessibilityNodeInfo? = node
        repeat(8) {
            val n = current ?: return false
            if (n.isClickable && n.isVisibleToUser) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current = n.parent
        }
        return false
    }

    fun shortId(full: String?): String? = full?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
}
