package com.example.notes.ui

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import com.example.notes.domain.PageBackgroundStyle
import com.example.notes.domain.CanvasObject
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.runBlocking

object PageRenderer {
    private val strokeRenderer = CanvasStrokeRenderer.create()

    fun renderToBitmap(
        context: Context,
        elements: List<CanvasObject>,
        width: Int,
        height: Int,
        backgroundStyle: PageBackgroundStyle = PageBackgroundStyle.LINED,
        backgroundColorInt: Int = Color.WHITE,
    ): Bitmap {
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.applyCanvas {
            drawColor(backgroundColorInt)
            PageBackgroundRenderer.draw(this, width.toFloat(), height.toFloat(), backgroundStyle)
            render(context, elements, this)
        }
        return bitmap
    }

    fun render(context: Context, elements: List<CanvasObject>, canvas: Canvas) {
        val sortedElements = elements.sortedBy { it.zIndex }
        sortedElements.forEach { element ->
            when (element) {
                is CanvasObject.StrokeObject -> renderStroke(element, canvas)
                is CanvasObject.RichTextObject -> renderText(context, element, canvas)
                is CanvasObject.ImageObject -> renderImage(context, element, canvas)
                is CanvasObject.IllustrationObject -> {} // Handle illustration
            }
        }
    }

    private fun renderImage(context: Context, imageObj: CanvasObject.ImageObject, canvas: Canvas) {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageObj.uri)
            .allowHardware(false)
            .build()
        
        val result = runBlocking { loader.execute(request) }
        val drawable = result.drawable
        if (drawable != null) {
            val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val rect = RectF(imageObj.x, imageObj.y, imageObj.x + imageObj.width, imageObj.y + imageObj.height)
                canvas.drawBitmap(bitmap, null, rect, null)
            }
        }
    }

    private fun renderText(context: Context, textObj: CanvasObject.RichTextObject, canvas: Canvas) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 16f * context.resources.displayMetrics.density
        }

        val width = textObj.width * context.resources.displayMetrics.density
        val builder = StaticLayout.Builder.obtain(textObj.text, 0, textObj.text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
        
        val staticLayout = builder.build()

        canvas.withTranslation(textObj.x, textObj.y) {
            staticLayout.draw(this)
        }
    }

    private fun renderStroke(strokeObj: CanvasObject.StrokeObject, canvas: Canvas) {
        val brush = Brush.createWithColorIntArgb(
            family = StockBrushes.marker(StockBrushes.MarkerVersion.V1),
            colorIntArgb = strokeObj.colorHex.toColorInt(),
            size = strokeObj.brushWidth,
            epsilon = 0.1f
        )
        
        val builder = MutableStrokeInputBatch()
        strokeObj.points.forEach { p ->
            builder.add(InputToolType.STYLUS, p.x, p.y, p.timestampMs, p.pressure, p.tiltX, p.tiltY)
        }
        
        val stroke = Stroke(brush, builder)
        strokeRenderer.draw(canvas, stroke, Matrix())
    }
}
