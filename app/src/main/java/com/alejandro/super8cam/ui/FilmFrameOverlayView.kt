package com.alejandro.super8cam.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Decorative chrome drawn over the live preview: a dark gate border and a
 * column of sprocket holes down the left edge, like looking through an
 * 8mm viewfinder. Purely cosmetic -- the actual film-look processing
 * happens in the GL shader (see gl/FilmShader.kt) and is baked into the
 * recorded video; this view is UI-only and is never part of the recording.
 */
class FilmFrameOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val sprocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1510")
        style = Paint.Style.FILL
    }
    private val sprocketHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A0806")
        style = Paint.Style.FILL
    }

    private val borderThicknessDp = 18f
    private val sprocketLaneWidthDp = 26f
    private val sprocketHoleHeightDp = 14f
    private val sprocketGapDp = 10f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val border = borderThicknessDp * density
        val laneWidth = sprocketLaneWidthDp * density

        // Top / bottom gate bars.
        canvas.drawRect(0f, 0f, width.toFloat(), border, borderPaint)
        canvas.drawRect(0f, height - border, width.toFloat(), height.toFloat(), borderPaint)
        // Right-side thin border for symmetry.
        canvas.drawRect(width - border * 0.4f, 0f, width.toFloat(), height.toFloat(), borderPaint)

        // Left sprocket-hole lane.
        canvas.drawRect(0f, 0f, laneWidth, height.toFloat(), sprocketPaint)
        val holeHeight = sprocketHoleHeightDp * density
        val gap = sprocketGapDp * density
        val holeWidth = laneWidth * 0.45f
        val holeLeft = (laneWidth - holeWidth) / 2f
        var y = gap
        while (y < height - holeHeight) {
            canvas.drawRoundRect(
                holeLeft, y, holeLeft + holeWidth, y + holeHeight,
                4f * density, 4f * density, sprocketHolePaint
            )
            y += holeHeight + gap
        }
    }
}
