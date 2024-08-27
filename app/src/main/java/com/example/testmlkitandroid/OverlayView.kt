package com.example.testmlkitandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom view that overlays bounding boxes on the camera preview for detected faces.
 * This view is responsible for drawing the bounding boxes on the screen.
 */
class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // List to hold the bounding boxes that need to be drawn on the screen.
    private val boundingBoxes = mutableListOf<RectF>()

    // Paint object used to define the style and color of the bounding boxes.
    private val paint = Paint().apply {
        color = android.graphics.Color.RED   // Set the initial color of the bounding box.
        style = Paint.Style.STROKE           // Set the paint style to stroke, meaning only the outline of the box will be drawn.
        strokeWidth = 8f                     // Set the width of the bounding box's outline.
    }

    // Preallocate a fixed number of RectF objects for reuse to avoid creating new objects frequently.
    // This helps in optimizing memory usage and reduces the overhead of garbage collection.
    private val reusableRects = List(10) { RectF() }

    /**
     * Updates the bounding boxes to be drawn on the screen.
     *
     * @param boxes List of RectF objects representing the new bounding boxes.
     */
    fun updateBoxes(boxes: List<RectF>) {
        boundingBoxes.clear()                // Clear the current list of bounding boxes.
        boundingBoxes.addAll(boxes)          // Add all new bounding boxes to the list.
        invalidate()                         // Request to redraw the view with the new bounding boxes.
    }

    /**
     * Changes the color of the bounding boxes.
     *
     * @param color The new color for the bounding boxes.
     */
    fun setBoxColor(color: Int) {
        paint.color = color                  // Update the paint color with the new color.
    }

    /**
     * Override the onDraw method to draw the bounding boxes on the canvas.
     *
     * @param canvas The Canvas object on which the bounding boxes will be drawn.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)                 // Call the superclass's onDraw method to ensure any background is drawn.

        // Loop through all bounding boxes and draw each one on the canvas.
        for (box in boundingBoxes) {
            canvas.drawRect(box, paint)      // Draw the bounding box using the current paint settings.
        }
    }
}
