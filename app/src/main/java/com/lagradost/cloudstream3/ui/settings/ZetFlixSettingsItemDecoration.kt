package com.lagradost.cloudstream3.ui.settings

import android.graphics.*
import android.view.View
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.utils.UIHelper.toPx

class ZetFlixSettingsItemDecoration(
    private val cornerRadius: Float = 12.toPx.toFloat(),
    private val padding: Int = 16.toPx,
    private val margin: Int = 16.toPx,
    private val backgroundColor: Int = Color.parseColor("#1a1a24"),
    private val borderColor: Int = Color.parseColor("#2c2c3a")
) : RecyclerView.ItemDecoration() {

    private val bgPaint = Paint().apply {
        color = backgroundColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = 1.toPx.toFloat()
        isAntiAlias = true
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val adapter = parent.adapter ?: return
        val itemType = adapter.getItemViewType(position)
        
        // We add extra margin above categories (except the first one or if it's title)
        // Actually, we'll handle margins in onDraw
        
        outRect.left = margin
        outRect.right = margin
        
        // Check if it's a category
        // In PreferenceFragment, categories usually have a specific layout id or type
        // For simplicity, let's assume we want some spacing between items
        outRect.bottom = 0
        outRect.top = 0
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter ?: return
        
        var currentCardTop = -1
        var currentCardBottom = -1

        parent.children.forEach { child ->
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) return@forEach

            // Logic to determine card boundaries:
            // A new card starts at a PreferenceCategory.
            // But how do we know if it's a category? 
            // We can check if the view has @android:id/title and no widget_frame?
            // Or better, check the view's padding/tag if we can set it.
            
            // For now, let's try a simpler logic:
            // Every item that is NOT the title header and NOT a category header gets grouped.
            // Actually, let's use the view type or layout id if possible.
            
            // Since we can't easily access Preference objects here without a lot of overhead,
            // we'll use a hack: check if the view is a TextView with a large size (Category).
        }
        
        // Actually, drawing card backgrounds behind groups in RecyclerView is better done by
        // checking the adapter items.
    }
}
