package com.poolaim.assistant

import kotlin.math.hypot
import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(v: Vec2) = Vec2(x + v.x, y + v.y)
    operator fun minus(v: Vec2) = Vec2(x - v.x, y - v.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    operator fun div(scalar: Float) = Vec2(x / scalar, y / scalar)

    fun dot(v: Vec2): Float = x * v.x + y * v.y
    fun length(): Float = hypot(x.toDouble(), y.toDouble()).toFloat()

    fun normalize(): Vec2 {
        val len = length()
        return if (len > 0.0001f) this / len else Vec2(0f, 0f)
    }
}

data class Ball(
    val pos: Vec2,
    val radius: Float,
    val isCueBall: Boolean = false
)

data class ShotTrajectory(
    val cueBallStart: Vec2,
    val cueBallImpact: Vec2,
    val objectBallPath: Vec2,
    val cueBallDeflection: Vec2,
    val cushionBounce: Vec2?
)

class PhysicsEngine(private val ballRadius: Float) {

    fun computeShot(
        cuePos: Vec2,
        aimDirection: Vec2,
        balls: List<Ball>,
        tableBounds: Pair<Vec2, Vec2>
    ): ShotTrajectory? {
        val dir = aimDirection.normalize()
        var closestDist = Float.MAX_VALUE
        var targetBall: Ball? = null
        var impactPoint: Vec2? = null

        val collisionDist = ballRadius * 2f

        for (ball in balls) {
            if (ball.isCueBall) continue

            val d = ball.pos - cuePos
            val proj = d.dot(dir)
            if (proj <= 0f) continue

            val perpDist = (d - (dir * proj)).length()
            if (perpDist < collisionDist) {
                val dImpact = proj - sqrt((collisionDist * collisionDist) - (perpDist * perpDist))
                if (dImpact in 0.0f..<closestDist) {
                    closestDist = dImpact
                    impactPoint = cuePos + (dir * dImpact)
                    targetBall = ball
                }
            }
        }

        if (targetBall == null || impactPoint == null) {
            val impact = projectToCushion(cuePos, dir, tableBounds)
            return ShotTrajectory(cuePos, impact, Vec2(0f, 0f), Vec2(0f, 0f), null)
        }

        val normal = (targetBall.pos - impactPoint).normalize()
        val tangent = Vec2(-normal.y, normal.x)
        val cueDeflect = if (tangent.dot(dir) >= 0) tangent else tangent * -1f
        val cushionHit = projectToCushion(targetBall.pos, normal, tableBounds)

        return ShotTrajectory(
            cueBallStart = cuePos,
            cueBallImpact = impactPoint,
            objectBallPath = normal,
            cueBallDeflection = cueDeflect,
            cushionBounce = cushionHit
        )
    }

    private fun projectToCushion(start: Vec2, dir: Vec2, bounds: Pair<Vec2, Vec2>): Vec2 {
        val (topLeft, bottomRight) = bounds
        var tMin = Float.MAX_VALUE

        if (dir.x > 0) tMin = minOf(tMin, (bottomRight.x - start.x) / dir.x)
        if (dir.x < 0) tMin = minOf(tMin, (topLeft.x - start.x) / dir.x)
        if (dir.y > 0) tMin = minOf(tMin, (bottomRight.y - start.y) / dir.y)
        if (dir.y < 0) tMin = minOf(tMin, (topLeft.y - start.y) / dir.y)

        return start + (dir * tMin)
    }
}
