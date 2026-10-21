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
import kotlin.math.cos
import kotlin.math.sin

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

    private var tableBounds: Pair<Vec2, Vec2> = Pair(Vec2(0f, 0f), Vec2(1000f, 1000f))
    private var cachedBalls: List<Ball> = emptyList()
    private var cachedCue: Ball? = null

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

        // Calibrated inner felt dimensions derived from 8 Ball Pool layout
        val tableTL = Vec2(width * 0.175f, height * 0.170f)
        val tableBR = Vec2(width * 0.825f, height * 0.830f)
        tableBounds = Pair(tableTL, tableBR)

        val dynamicBallRadius = height * 0.024f
        overlayView.targetRadius = dynamicBallRadius

        physicsEngine = PhysicsEngine(ballRadius = dynamicBallRadius)
        visionProcessor = VisionProcessor(tableTL, tableBR, dynamicBallRadius)

        // Aim angle callback
        overlayView.onAimAdjusted = { angle: Float ->
            val cue = cachedCue ?: cachedBalls.firstOrNull()
            if (cue != null) {
                val aimDir = Vec2(cos(angle), sin(angle))
                val traj = physicsEngine.computeShot(cue.pos, aimDir, cachedBalls, tableBounds)
                overlayView.activeTrajectory = traj
            }
        }

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
                reader.acquireLatestImage()?.close()
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
                        cachedBalls = balls
                        cachedCue = cueBall ?: balls.first()
                        overlayView.detectedBalls = balls
                        overlayView.currentCuePos = cachedCue?.pos

                        overlayView.statusText = "Locked: ${balls.size} Balls Detected"

                        val activeCue = cachedCue!!
                        val candidates = balls.filter { it != activeCue }

                        if (candidates.isNotEmpty()) {
                            val target = candidates.minByOrNull { (it.pos - activeCue.pos).length() }
                            if (target != null) {
                                val aimDir = (target.pos - activeCue.pos).normalize()
                                overlayView.activeTrajectory = physicsEngine.computeShot(
                                    activeCue.pos,
                                    aimDir,
                                    balls,
                                    tableBounds
                                )
                            }
                        }
                    } else {
                        overlayView.statusText = "Scanning table... Aligning felt bounds"
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
