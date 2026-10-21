package com.poolaim.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class OverlayAimView(context: Context) : View(context) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    var activeTrajectory: ShotTrajectory? = null
    var targetRadius: Float = 18f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val traj = activeTrajectory ?: return

        linePaint.color = Color.WHITE
        canvas.drawLine(
            traj.cueBallStart.x, traj.cueBallStart.y,
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            linePaint
        )

        canvas.drawCircle(traj.cueBallImpact.x, traj.cueBallImpact.y, targetRadius, circlePaint)

        linePaint.color = Color.RED
        val objEnd = traj.cueBallImpact + (traj.objectBallPath * 350f)
        canvas.drawLine(
            traj.cueBallImpact.x, traj.cueBallImpact.y,
            objEnd.x, objEnd.y,
            linePaint
        )

        linePaint.color = Color.CYAN
        val cueDeflectEnd = traj.cueBallImpact + (traj.cueBallDeflection * 160f)
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
