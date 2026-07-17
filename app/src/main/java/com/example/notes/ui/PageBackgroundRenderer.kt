package com.example.notes.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.notes.domain.PageBackgroundStyle

object PageBackgroundRenderer {
    fun draw(canvas: Canvas, width: Float, height: Float, backgroundStyle: PageBackgroundStyle, density: Float = 1f) {
        val paint = Paint().apply {
            color = Color.parseColor("#E0E0E0") // Light gray
            strokeWidth = 1f * density
            style = Paint.Style.STROKE
        }

        when (backgroundStyle) {
            PageBackgroundStyle.LINED -> {
                val spacing = 32f * density
                var y = spacing
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, paint)
                    y += spacing
                }
            }
            PageBackgroundStyle.GRID -> {
                val spacing = 32f * density
                // Horizontal lines
                var y = spacing
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, paint)
                    y += spacing
                }
                // Vertical lines
                var x = spacing
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height, paint)
                    x += spacing
                }
            }
            PageBackgroundStyle.PLAIN -> {
                // Just keep it plain (white)
            }
        }
    }
}
