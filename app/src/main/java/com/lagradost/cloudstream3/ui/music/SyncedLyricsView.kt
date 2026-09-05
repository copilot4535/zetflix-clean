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
    private var isUserScrolling = false

    init {
        layoutManager = LinearLayoutManager(context)
        adapter = lyricsAdapter
        
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isUserScrolling = newState != SCROLL_STATE_IDLE
            }
        })
    }

    fun setLyrics(lines: List<LyricLine>) {
        lyricsAdapter.submitList(lines)
    }

    fun updateProgress(currentMs: Long) {
        if (isUserScrolling) return

        val lines = lyricsAdapter.currentList
        if (lines.isEmpty()) return

        val index = lines.indexOfLast { it.timestampMs <= currentMs }
        if (index != -1 && index != lyricsAdapter.currentLineIndex) {
            lyricsAdapter.currentLineIndex = index
            scrollToPositionCentered(index)
        }
    }

    fun scrollToPositionCentered(index: Int) {
        val layoutManager = layoutManager as? LinearLayoutManager ?: return
        
        post {
            if (isUserScrolling || index < 0 || index >= lyricsAdapter.itemCount) return@post
            
            val smoothScroller = object : androidx.recyclerview.widget.LinearSmoothScroller(context) {
                override fun getVerticalSnapPreference(): Int = SNAP_TO_ANY
                
                override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                    return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
                }
            }
            smoothScroller.targetPosition = index
            layoutManager.startSmoothScroll(smoothScroller)
        }
    }
}
