package com.poolaim.assistant

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class AimAssistService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayAimView
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var physicsEngine: PhysicsEngine
    private lateinit var visionProcessor: VisionProcessor

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = OverlayAimView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayView, params)

        startForeground(101, buildNotification())
    }

    @SuppressLint("WrongConstant")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("DATA") ?: return START_NOT_STICKY

        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val dpi = resources.displayMetrics.densityDpi

        val tableTL = Vec2(width * 0.12f, height * 0.22f)
        val tableBR = Vec2(width * 0.88f, height * 0.78f)

        physicsEngine = PhysicsEngine(ballRadius = 18f)
        visionProcessor = VisionProcessor(tableTL, tableBR)

        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PoolAssistCapture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val cleanWidth = image.width + (rowStride - pixelStride * image.width) / pixelStride

            val bitmap = Bitmap.createBitmap(cleanWidth, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val (balls, cueBall) = visionProcessor.processFrame(bitmap)

            if (cueBall != null && balls.isNotEmpty()) {
                val target = balls.firstOrNull { !it.isCueBall }
                if (target != null) {
                    val aimDir = (target.pos - cueBall.pos).normalize()
                    val trajectory = physicsEngine.computeShot(
                        cueBall.pos,
                        aimDir,
                        balls,
                        Pair(tableTL, tableBR)
                    )

                    overlayView.post {
                        overlayView.activeTrajectory = trajectory
                        overlayView.invalidate()
                    }
                }
            }
        }, null)

        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "aim_assist_channel"
        val channel = NotificationChannel(channelId, "Aim Assist Running", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pool Aim Assistant")
            .setContentText("Screen analysis active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
    }
}
