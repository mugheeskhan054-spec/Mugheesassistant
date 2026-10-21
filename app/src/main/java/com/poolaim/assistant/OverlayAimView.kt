package com.poolaim.assistant

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class OverlayAimView(context: Context) : View(context) {

    private val aimWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
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
        color = Color.argb(120, 34, 197, 94)
        style = Paint.Style.FILL
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
    var statusText: String = "Initializing Engine..."

    // Manual override vector (allows fine-tuning aim manually)
    var manualAimAngle: Float = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Status Header
        canvas.drawText("🎱 $statusText", 60f, 90f, textPaint)

        // Draw green rings around all computer-vision-detected balls to confirm tracking
        for (b in detectedBalls) {
            canvas.drawCircle(b.pos.x, b.pos.y, b.radius, ballIndicatorPaint)
        }

        val traj = activeTrajectory ?: return

        // 1. Cue Ball to Impact Line
        canvas.drawLine(
            traj.cueBallStart.x, traj.cueBallStart.y,
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            aimWhitePaint
        )

        // 2. Ghost Impact Ring
        canvas.drawCircle(traj.cueBallImpact.x, traj.cueBallImpact.y, targetRadius, circlePaint)

        // 3. Object Ball Target Line
        val objEnd = traj.cueBallImpact + (traj.objectBallPath * 500f)
        canvas.drawLine(
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            objEnd.x, objEnd.y,
            targetRedPaint
        )

        // 4. Cue Ball Tangent Deflection Line
        val cueDeflectEnd = traj.cueBallImpact + (traj.cueBallDeflection * 250f)
        canvas.drawLine(
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            cueDeflectEnd.x, cueDeflectEnd.y,
            cueCyanPaint
        )

        // 5. Cushion Rebound Bank
        traj.cushionBounce?.let { bounce ->
            canvas.drawLine(objEnd.x, objEnd.y, bounce.x, bounce.y, bounceYellowPaint)
        }
    }
}
