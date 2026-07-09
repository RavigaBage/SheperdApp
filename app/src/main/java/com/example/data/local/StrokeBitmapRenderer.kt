package com.example.data.local

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.example.presentation.viewmodel.DrawingStroke

object StrokeBitmapRenderer {

    fun render(strokes: List<DrawingStroke>, backgroundColor: Int = android.graphics.Color.WHITE): Bitmap {
        if (strokes.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (stroke in strokes) {
            for (point in stroke.points) {
                if (point.x < minX) minX = point.x
                if (point.y < minY) minY = point.y
                if (point.x > maxX) maxX = point.x
                if (point.y > maxY) maxY = point.y
            }
        }

        val padding = 50f
        val width = (maxX - minX + padding * 2).toInt().coerceAtLeast(100)
        val height = (maxY - minY + padding * 2).toInt().coerceAtLeast(100)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (stroke in strokes) {
            paint.color = if (stroke.isEraser) backgroundColor else stroke.color
            paint.strokeWidth = stroke.strokeWidth
            if (stroke.points.isNotEmpty()) {
                val path = Path().apply {
                    moveTo(stroke.points[0].x - minX + padding, stroke.points[0].y - minY + padding)
                    for (i in 1 until stroke.points.size) {
                        lineTo(stroke.points[i].x - minX + padding, stroke.points[i].y - minY + padding)
                    }
                }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }
}