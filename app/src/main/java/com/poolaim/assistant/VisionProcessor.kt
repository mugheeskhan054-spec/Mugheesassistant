package com.poolaim.assistant

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

class VisionProcessor(
    private val tableTopLeft: Vec2,
    private val tableBottomRight: Vec2,
    private val ballRadiusPixels: Float
) {
    fun processFrame(bitmap: Bitmap): Pair<List<Ball>, Ball?> {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val roiWidth = (tableBottomRight.x - tableTopLeft.x).toInt().coerceIn(1, mat.cols())
        val roiHeight = (tableBottomRight.y - tableTopLeft.y).toInt().coerceIn(1, mat.rows())
        val roiX = tableTopLeft.x.toInt().coerceIn(0, mat.cols() - roiWidth)
        val roiY = tableTopLeft.y.toInt().coerceIn(0, mat.rows() - roiHeight)

        val tableMat = Mat(mat, Rect(roiX, roiY, roiWidth, roiHeight))
        val gray = Mat()
        Imgproc.cvtColor(tableMat, gray, Imgproc.COLOR_RGBA2GRAY)

        // Enhance contrast to separate spheres from the cloth gradient
        Imgproc.equalizeHist(gray, gray)
        Imgproc.GaussianBlur(gray, gray, Size(7.0, 7.0), 1.5)

        // Hough circles tuned to the exact scaled ball diameter
        val circles = Mat()
        val minR = (ballRadiusPixels * 0.75f).toInt().coerceAtLeast(10)
        val maxR = (ballRadiusPixels * 1.25f).toInt().coerceAtLeast(minR + 4)

        Imgproc.HoughCircles(
            gray,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.2,
            ballRadiusPixels.toDouble() * 1.6, // Minimum distance between two balls
            60.0, // Upper edge threshold
            18.0, // Accumulator threshold (lower = more sensitive detection)
            minR,
            maxR
        )

        val balls = mutableListOf<Ball>()
        var cueBall: Ball? = null

        val hsv = Mat()
        Imgproc.cvtColor(tableMat, hsv, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)

        val pocketRadiusTolerance = ballRadiusPixels * 2.5f

        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i) ?: continue
            val localX = data[0].toFloat()
            val localY = data[1].toFloat()
            val r = data[2].toFloat()

            // Filter out edge pockets
            val isNearCorner = (localX < pocketRadiusTolerance || localX > roiWidth - pocketRadiusTolerance) &&
                    (localY < pocketRadiusTolerance || localY > roiHeight - pocketRadiusTolerance)
            val isNearSidePocket = (abs(localX - (roiWidth / 2f)) < pocketRadiusTolerance) &&
                    (localY < pocketRadiusTolerance || localY > roiHeight - pocketRadiusTolerance)

            if (isNearCorner || isNearSidePocket) continue

            val screenX = localX + roiX
            val screenY = localY + roiY

            // Sample central pixels to locate the white cue ball
            val pX = localX.toInt().coerceIn(0, tableMat.cols() - 1)
            val pY = localY.toInt().coerceIn(0, tableMat.rows() - 1)
            val pixel = hsv.get(pY, pX)

            val isWhite = (pixel != null && pixel[1] < 45.0 && pixel[2] > 185.0)
            val detected = Ball(Vec2(screenX, screenY), r, isWhite)

            if (isWhite) {
                cueBall = detected
            }
            balls.add(detected)
        }

        mat.release()
        tableMat.release()
        gray.release()
        circles.release()
        hsv.release()

        return Pair(balls, cueBall)
    }
}
