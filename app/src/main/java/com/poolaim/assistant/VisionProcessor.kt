package com.poolaim.assistant

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

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

        // Mask specifically tuned for the Cyan/Blue 8 Ball Pool felt:
        // Hue: 85 to 130 covers blue/cyan cloth across brightness gradients
        val feltMask = Mat()
        Core.inRange(hsv, Scalar(85.0, 70.0, 50.0), Scalar(135.0, 255.0, 255.0), feltMask)

        // Invert: Non-felt elements (balls) become white
        val nonFelt = Mat()
        Core.bitwise_not(feltMask, nonFelt)

        // Morphological opening to eliminate line artifacts and table chalk marks
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(nonFelt, nonFelt, Imgproc.MORPH_OPEN, kernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(nonFelt, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val balls = mutableListOf<Ball>()
        var cueBall: Ball? = null

        val expectedArea = Math.PI * ballRadiusPixels * ballRadiusPixels
        val minArea = expectedArea * 0.4
        val maxArea = expectedArea * 1.8

        // Define pocket deadzones inside the ROI to ignore pocket shadows
        val pocketRadiusTolerance = ballRadiusPixels * 2.2f

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area in minArea..maxArea) {
                val moments = Imgproc.moments(contour)
                if (moments.m00 != 0.0) {
                    val localX = (moments.m10 / moments.m00).toFloat()
                    val localY = (moments.m01 / moments.m00).toFloat()

                    // Ignore detections directly hugging the inner cushion boundary / pocket corners
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

                    // Cue ball has minimal color saturation and high brightness
                    val isWhite = (pixel != null && pixel[1] < 40.0 && pixel[2] > 195.0)
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
