package com.poolaim.assistant

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class VisionProcessor(
    private val tableTopLeft: Vec2,
    private val tableBottomRight: Vec2
) {
    private val ballRadiusPixels = 18f

    fun processFrame(bitmap: Bitmap): Pair<List<Ball>, Ball?> {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val roiWidth = (tableBottomRight.x - tableTopLeft.x).toInt().coerceAtLeast(1)
        val roiHeight = (tableBottomRight.y - tableTopLeft.y).toInt().coerceAtLeast(1)
        val roi = Rect(tableTopLeft.x.toInt(), tableTopLeft.y.toInt(), roiWidth, roiHeight)

        val tableMat = Mat(mat, roi)
        val gray = Mat()
        Imgproc.cvtColor(tableMat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(9.0, 9.0), 2.0)

        val circles = Mat()
        Imgproc.HoughCircles(
            gray,
            circles,
            Imgproc.HOUGH_GRADIENT,
            1.2,
            ballRadiusPixels.toDouble() * 1.8,
            50.0,
            24.0,
            (ballRadiusPixels * 0.8).toInt(),
            (ballRadiusPixels * 1.2).toInt()
        )

        val balls = mutableListOf<Ball>()
        var cueBall: Ball? = null

        val hsv = Mat()
        Imgproc.cvtColor(tableMat, hsv, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)

        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i) ?: continue
            val localX = data[0].toFloat()
            val localY = data[1].toFloat()
            val r = data[2].toFloat()

            val screenX = localX + tableTopLeft.x
            val screenY = localY + tableTopLeft.y

            val pX = localX.toInt().coerceIn(0, tableMat.cols() - 1)
            val pY = localY.toInt().coerceIn(0, tableMat.rows() - 1)
            val pixel = hsv.get(pY, pX)

            val isWhite = (pixel != null && pixel[1] < 45.0 && pixel[2] > 200.0)
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
