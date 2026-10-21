package com.poolaim.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class OverlayAimView(context: Context) : View(context) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        textSize = 36f
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    var activeTrajectory: ShotTrajectory? = null
    var targetRadius: Float = 25f
    var statusText: String = "Initializing Engine..."

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawText("🎱 $statusText", 50f, 100f, textPaint)

        val traj = activeTrajectory ?: return

        linePaint.color = Color.WHITE
        canvas.drawLine(
            traj.cueBallStart.x, traj.cueBallStart.y,
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            linePaint
        )

        canvas.drawCircle(traj.cueBallImpact.x, traj.cueBallImpact.y, targetRadius, circlePaint)

        linePaint.color = Color.RED
        val objEnd = traj.cueBallImpact + (traj.objectBallPath * 450f)
        canvas.drawLine(
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            objEnd.x, objEnd.y,
            linePaint
        )

        linePaint.color = Color.CYAN
        val cueDeflectEnd = traj.cueBallImpact + (traj.cueBallDeflection * 220f)
        canvas.drawLine(
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            cueDeflectEnd.x, cueDeflectEnd.y,
            linePaint
        )

        traj.cushionBounce?.let { bounce ->
            linePaint.color = Color.YELLOW
            canvas.drawLine(objEnd.x, objEnd.y, bounce.x, bounce.y, linePaint)
        }
    }
}
