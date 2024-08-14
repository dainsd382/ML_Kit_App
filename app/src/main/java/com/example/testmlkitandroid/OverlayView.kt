package com.example.testmlkitandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val boundingBoxes = mutableListOf<RectF>()
    private val paint = Paint().apply {
        color = android.graphics.Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // Preallocate a fixed number of RectF objects for reuse
    private val reusableRects = List(10) { RectF() }

    fun updateBoxes(boxes: List<RectF>) {
        boundingBoxes.clear()
        boundingBoxes.addAll(boxes)
        invalidate()
    }

    fun setBoxColor(color: Int) {
        paint.color = color
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (box in boundingBoxes) {
            canvas.drawRect(box, paint)
        }
    }
}

