package com.lagradost.cloudstream3.ui.music

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SyncedLyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val lyricsAdapter = LyricsLineAdapter()
    var autoScrollEnabled = true
        private set

    init {
        layoutManager = LinearLayoutManager(context)
        adapter = lyricsAdapter
        
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == SCROLL_STATE_DRAGGING) {
                    autoScrollEnabled = false
                }
            }
        })
    }

    fun setLyrics(lines: List<LyricLine>) {
        lyricsAdapter.submitList(lines)
        autoScrollEnabled = true
    }

    fun setPalette(palette: LyricsPalette) {
        lyricsAdapter.setPalette(palette)
    }

    fun resumeAutoScroll() {
        autoScrollEnabled = true
        val index = lyricsAdapter.currentLineIndex
        if (index != -1) {
            scrollToPositionCentered(index)
        }
    }

    fun updateProgress(currentMs: Long) {
        val lines = lyricsAdapter.currentList
        if (lines.isEmpty()) return

        val index = lines.indexOfLast { it.timestampMs <= currentMs }
        if (index != -1 && index != lyricsAdapter.currentLineIndex) {
            lyricsAdapter.currentLineIndex = index
            if (autoScrollEnabled) {
                scrollToPositionCentered(index)
            }
        }
    }

    fun scrollToPositionCentered(index: Int) {
        val layoutManager = layoutManager as? LinearLayoutManager ?: return
        
        post {
            if (index < 0 || index >= lyricsAdapter.itemCount) return@post
            
            val smoothScroller = object : androidx.recyclerview.widget.LinearSmoothScroller(context) {
                override fun getVerticalSnapPreference(): Int = SNAP_TO_ANY
                
                override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                    // Target approximately 1/3 down from the top for a more premium reading focus
                    val targetY = boxStart + (boxEnd - boxStart) / 3
                    return targetY - viewStart
                }

                override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                    // Slightly slower, smoother scroll for a premium feel
                    return 80f / displayMetrics.densityDpi
                }
            }
            smoothScroller.targetPosition = index
            layoutManager.startSmoothScroll(smoothScroller)
        }
    }
}
