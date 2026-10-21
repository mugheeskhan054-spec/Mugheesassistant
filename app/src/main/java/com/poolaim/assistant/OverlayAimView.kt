package com.poolaim.assistant

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class OverlayAimView(context: Context) : View(context) {

    private val aimWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }

    private val targetRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        strokeWidth = 7f
        style = Paint.Style.STROKE
    }

    private val cueCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#06B6D4")
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val bounceYellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FACC15")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    private val ballIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 34, 197, 94)
        style = Paint.Style.FILL
    }

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 56, 189, 248)
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        textSize = 34f
        style = Paint.Style.FILL
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(6f, 3f, 3f, Color.BLACK)
    }

    var activeTrajectory: ShotTrajectory? = null
    var detectedBalls: List<Ball> = emptyList()
    var targetRadius: Float = 24f
    var statusText: String = "Initializing..."

    var currentCuePos: Vec2? = null
    var aimAngle: Float = 0f

    // Callback to trigger recalculation when touch angle changes
    var onAimAdjusted: ((Float) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cue = currentCuePos ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cue.x
                val dy = event.y - cue.y
                aimAngle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                onAimAdjusted?.invoke(aimAngle)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Status Banner
        canvas.drawText("🎱 $statusText", 60f, 90f, textPaint)

        // Highlight detected ball locations
        for (b in detectedBalls) {
            canvas.drawCircle(b.pos.x, b.pos.y, b.radius, ballIndicatorPaint)
        }

        // Draw Interactive Aim Dial around cue ball
        currentCuePos?.let { cue ->
            canvas.drawCircle(cue.x, cue.y, targetRadius * 2.8f, dialPaint)
            val handleX = cue.x + cos(aimAngle) * targetRadius * 2.8f
            val handleY = cue.y + sin(aimAngle) * targetRadius * 2.8f
            canvas.drawCircle(handleX, handleY, 14f, circlePaint)
        }

        val traj = activeTrajectory ?: return

        // 1. Cue to Impact Line
        canvas.drawLine(
            traj.cueBallStart.x, traj.cueBallStart.y,
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            aimWhitePaint
        )

        // 2. Ghost Impact Ring
        canvas.drawCircle(traj.cueBallImpact.x, traj.cueBallImpact.y, targetRadius, circlePaint)

        // 3. Object Ball Target Line (Red)
        val objEnd = traj.cueBallImpact + (traj.objectBallPath * 500f)
        canvas.drawLine(
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            objEnd.x, objEnd.y,
            targetRedPaint
        )

        // 4. Cue Ball Deflection Line (Cyan)
        val cueDeflectEnd = traj.cueBallImpact + (traj.cueBallDeflection * 250f)
        canvas.drawLine(
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            cueDeflectEnd.x, cueDeflectEnd.y,
            cueCyanPaint
        )

        // 5. Cushion Rebound Bank (Yellow)
        traj.cushionBounce?.let { bounce ->
            canvas.drawLine(objEnd.x, objEnd.y, bounce.x, bounce.y, bounceYellowPaint)
        }
    }
}
