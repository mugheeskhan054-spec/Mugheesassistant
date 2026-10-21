package com.poolaim.assistant

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

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
        val hsv = Mat()
        Imgproc.cvtColor(tableMat, hsv, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)

        // 1. AUTO-SAMPLE TABLE CLOTH COLOR
        // Sample a 20x20 patch near the center of the table felt
        val sampleX = (roiWidth * 0.45f).toInt().coerceIn(0, roiWidth - 25)
        val sampleY = (roiHeight * 0.45f).toInt().coerceIn(0, roiHeight - 25)
        val sampleRoi = Mat(hsv, Rect(sampleX, sampleY, 20, 20))
        val meanColor = Core.mean(sampleRoi)
        sampleRoi.release()

        val feltH = meanColor.`val`[0]
        val feltS = meanColor.`val`[1]
        val feltV = meanColor.`val`[2]

        // 2. DYNAMIC TOLERANCE MASK BASED ON SAMPLED COLOR
        val lowerBound = Scalar(
            (feltH - 18.0).coerceAtLeast(0.0),
            (feltS - 50.0).coerceAtLeast(30.0),
            (feltV - 60.0).coerceAtLeast(30.0)
        )
        val upperBound = Scalar(
            (feltH + 18.0).coerceAtMost(180.0),
            (feltS + 50.0).coerceAtMost(255.0),
            (feltV + 60.0).coerceAtMost(255.0)
        )

        val feltMask = Mat()
        Core.inRange(hsv, lowerBound, upperBound, feltMask)

        // Invert: non-cloth objects (balls, chalk, pocket outlines) become white
        val nonFelt = Mat()
        Core.bitwise_not(feltMask, nonFelt)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(nonFelt, nonFelt, Imgproc.MORPH_OPEN, kernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(nonFelt, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val balls = mutableListOf<Ball>()
        var cueBall: Ball? = null

        val expectedArea = Math.PI * ballRadiusPixels * ballRadiusPixels
        val minArea = expectedArea * 0.35
        val maxArea = expectedArea * 2.0
        val pocketRadiusTolerance = ballRadiusPixels * 2.2f

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area in minArea..maxArea) {
                val moments = Imgproc.moments(contour)
                if (moments.m00 != 0.0) {
                    val localX = (moments.m10 / moments.m00).toFloat()
                    val localY = (moments.m01 / moments.m00).toFloat()

                    // Exclude pocket zones
                    val isNearCorner = (localX < pocketRadiusTolerance || localX > roiWidth - pocketRadiusTolerance) &&
                            (localY < pocketRadiusTolerance || localY > roiHeight - pocketRadiusTolerance)
                    val isNearSidePocket = (Math.abs(localX - (roiWidth / 2f)) < pocketRadiusTolerance) &&
                            (localY < pocketRadiusTolerance || localY > roiHeight - pocketRadiusTolerance)

                    if (isNearCorner || isNearSidePocket) {
                        contour.release()
                        continue
                    }

                    val screenX = localX + roiX
                    val screenY = localY + roiY

                    val pX = localX.toInt().coerceIn(0, tableMat.cols() - 1)
                    val pY = localY.toInt().coerceIn(0, tableMat.rows() - 1)
                    val pixel = hsv.get(pY, pX)

                    // White cue ball detection remains universal (always white regardless of table color)
                    val isWhite = (pixel != null && pixel[1] < 45.0 && pixel[2] > 190.0)
                    val detectedBall = Ball(Vec2(screenX, screenY), ballRadiusPixels, isWhite)

                    if (isWhite) {
                        cueBall = detectedBall
                    }
                    balls.add(detectedBall)
                }
            }
            contour.release()
        }

        mat.release()
        tableMat.release()
        hsv.release()
        feltMask.release()
        nonFelt.release()
        hierarchy.release()
        kernel.release()

        return Pair(balls, cueBall)
    }
}
