package com.lagradost.cloudstream3.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.absoluteValue

object AvatarDrawableGenerator {
    private val backgrounds = listOf(
        0xFF2196F3.toInt(), // blue
        0xFF3F51B5.toInt(), // dark blue
        0xFFFF9800.toInt(), // orange
        0xFFE91E63.toInt(), // pink
        0xFF9C27B0.toInt(), // purple
        0xFFE50914.toInt(), // red
        0xFF009688.toInt()  // teal
    )

    fun generateMonogramDrawable(context: Context, email: String): Drawable {
        val username = email.substringBefore("@")
        val letter = if (username.isNotEmpty()) username.take(1).uppercase() else "?"
        val bgIndex = if (username.isNotEmpty()) username.hashCode().absoluteValue % backgrounds.size else 0
        val color = backgrounds[bgIndex]

        val size = 24 // Standard bottom nav icon size
        val pxSize = (size * context.resources.displayMetrics.density).toInt()
        
        val bitmap = Bitmap.createBitmap(pxSize, pxSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawCircle(pxSize / 2f, pxSize / 2f, pxSize / 2f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = pxSize * 0.5f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        
        val xPos = pxSize / 2f
        val yPos = (pxSize / 2f - (paint.descent() + paint.ascent()) / 2f)
        
        canvas.drawText(letter, xPos, yPos, paint)
        
        return BitmapDrawable(context.resources, bitmap)
    }
}
