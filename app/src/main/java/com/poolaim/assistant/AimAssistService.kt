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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.opencv.android.OpenCVLoader

class AimAssistService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayAimView
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var physicsEngine: PhysicsEngine
    private lateinit var visionProcessor: VisionProcessor

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isProcessing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        OpenCVLoader.initDebug()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = OverlayAimView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayView, params)

        startForeground(101, buildNotification())
    }

    @SuppressLint("WrongConstant")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("DATA") ?: return START_NOT_STICKY

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi

        val tableTL = Vec2(width * 0.08f, height * 0.15f)
        val tableBR = Vec2(width * 0.92f, height * 0.85f)

        val dynamicBallRadius = height * 0.028f
        overlayView.targetRadius = dynamicBallRadius

        physicsEngine = PhysicsEngine(ballRadius = dynamicBallRadius)
        visionProcessor = VisionProcessor(tableTL, tableBR, dynamicBallRadius)

        overlayView.activeTrajectory = ShotTrajectory(
            cueBallStart = Vec2(width * 0.35f, height * 0.5f),
            cueBallImpact = Vec2(width * 0.65f, height * 0.5f),
            objectBallPath = Vec2(1f, -0.4f).normalize(),
            cueBallDeflection = Vec2(0.4f, 1f).normalize(),
            cushionBounce = Vec2(width * 0.92f, height * 0.38f)
        )
        overlayView.statusText = "Vision Engine Active - Waiting for Game Table..."
        overlayView.invalidate()

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
            if (isProcessing) {
                val img = reader.acquireLatestImage()
                img?.close()
                return@setOnImageAvailableListener
            }

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            isProcessing = true

            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val cleanWidth = image.width + (rowStride - pixelStride * image.width) / pixelStride

                val bitmap = Bitmap.createBitmap(cleanWidth, image.height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val (balls, cueBall) = visionProcessor.processFrame(bitmap)

                mainHandler.post {
                    if (balls.isNotEmpty()) {
                        overlayView.statusText = "Detected ${balls.size} Balls"
                        
                        val activeCue = cueBall ?: balls.first()
                        val target = balls.firstOrNull { it != activeCue }

                        if (target != null) {
                            val aimDir = (target.pos - activeCue.pos).normalize()
                            val trajectory = physicsEngine.computeShot(
                                activeCue.pos,
                                aimDir,
                                balls,
                                Pair(tableTL, tableBR)
                            )
                            overlayView.activeTrajectory = trajectory
                        }
                    } else {
                        overlayView.statusText = "Searching for Table Balls..."
                    }
                    overlayView.invalidate()
                    isProcessing = false
                }
            } catch (e: Exception) {
                isProcessing = false
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
