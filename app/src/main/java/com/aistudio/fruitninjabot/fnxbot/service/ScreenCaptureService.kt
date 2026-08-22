package com.aistudio.fruitninjabot.fnxbot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.aistudio.fruitninjabot.fnxbot.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val CHANNEL_ID = "screen_capture_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "ACTION_START_CAPTURE"
        const val ACTION_STOP = "ACTION_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"

        var isRunning: Boolean = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDensity = 320

    private val isProcessingFrame = AtomicBoolean(false)
    private var lastProcessTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

                if (resultCode != -1 && resultData != null) {
                    startForegroundWithNotification()
                    initScreenDimensions()
                    startBackgroundThread()
                    initMediaProjection(resultCode, resultData)
                    isRunning = true
                    BotStateController.setScreenCaptureActive(true)
                    BotStateController.addLog("Screen Capture & Vision Pipeline started.")
                } else {
                    Log.e(TAG, "Invalid MediaProjection intent extras")
                    stopSelf()
                }
            }

            ACTION_STOP -> {
                stopCapture()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fruit Ninja Bot Active")
            .setContentText("Vision engine scanning game arena...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Bot", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initScreenDimensions() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = max(metrics.widthPixels, 320)
        screenHeight = max(metrics.heightPixels, 480)
        screenDensity = metrics.densityDpi
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("VisionCaptureThread", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun initMediaProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.w(TAG, "MediaProjection stopped by system")
                stopCapture()
            }
        }, backgroundHandler)

        // Downscale capture surface to 480px width for low latency & memory safety
        val captureWidth = 480
        val captureHeight = (screenHeight.toFloat() / screenWidth.toFloat() * captureWidth).toInt().coerceAtLeast(320)

        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            3
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            handleImageAvailable(reader, captureWidth, captureHeight)
        }, backgroundHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "FruitNinjaVisionDisplay",
            captureWidth,
            captureHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )
    }

    private fun handleImageAvailable(reader: ImageReader, captureWidth: Int, captureHeight: Int) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image == null) return

            val runState = BotStateController.botRunState.value
            if (runState != BotRunState.RUNNING) {
                image.close()
                return
            }

            val now = System.currentTimeMillis()
            val config = BotStateController.config.value
            val targetInterval = 1000L / max(1, config.targetFps)
            if (now - lastProcessTime < targetInterval) {
                image.close()
                return
            }

            if (!isProcessingFrame.compareAndSet(false, true)) {
                image.close()
                return
            }

            val startTime = System.currentTimeMillis()

            // 1. Extract planes and calculate row stride / padding
            val planes = image.planes
            val plane = planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = max(0, rowStride - pixelStride * captureWidth)

            // 2. Stride-aware Bitmap extraction
            val strideWidth = if (pixelStride > 0) captureWidth + (rowPadding / pixelStride) else captureWidth
            val rawBitmap = Bitmap.createBitmap(
                strideWidth,
                captureHeight,
                Bitmap.Config.ARGB_8888
            )
            rawBitmap.copyPixelsFromBuffer(buffer)
            
            // Release hardware Image immediately to keep buffer pool free
            image.close()
            image = null

            // 3. Crop out GPU stride padding if present
            val cleanBitmap = if (rowPadding > 0 && strideWidth > captureWidth) {
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, captureWidth, captureHeight)
                rawBitmap.recycle()
                cropped
            } else {
                rawBitmap
            }

            // 4. Execute vision detection algorithm
            val result = ColorDetector.analyzeFrame(
                bitmap = cleanBitmap,
                config = config,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )

            // 5. Update latency and metrics immediately for HUD overlay
            val latency = max(1L, System.currentTimeMillis() - startTime)
            lastProcessTime = System.currentTimeMillis()
            BotStateController.setVisionLatency(latency)

            if (result.fruits.isNotEmpty()) {
                BotStateController.incrementDetectedCount(result.fruits.size)
            }

            // 6. Dispatch gestures if AccessibilityService is active
            val touchService = AutoTouchService.instance
            if (touchService != null) {
                // Handle ad skipping
                if (result.adSkips.isNotEmpty() && config.autoSkipAds) {
                    val adPoint = result.adSkips.first()
                    touchService.performClick(adPoint.x, adPoint.y) {
                        BotStateController.incrementAdSkipsCount(1)
                        BotStateController.addLog("Auto-closed ad overlay.")
                    }
                }

                // Handle auto start menu buttons
                if (result.fruits.isEmpty() && result.menuActions.isNotEmpty() && config.autoStartGame) {
                    val menuPoint = result.menuActions.first()
                    touchService.performSwipe(
                        menuPoint.x - 120f,
                        menuPoint.y + 40f,
                        menuPoint.x + 120f,
                        menuPoint.y - 40f,
                        durationMs = 50L
                    ) {
                        BotStateController.incrementMenuClicksCount(1)
                        BotStateController.addLog("Sliced lobby start watermelon!")
                    }
                }

                // Execute slice trajectories
                for (traj in result.trajectories) {
                    touchService.performSwipe(
                        startX = traj.startX,
                        startY = traj.startY,
                        endX = traj.endX,
                        endY = traj.endY,
                        durationMs = traj.durationMs
                    )
                }
            }

            cleanBitmap.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Error in vision processing loop: ${e.message}", e)
        } finally {
            image?.close()
            isProcessingFrame.set(false)
        }
    }

    private fun stopCapture() {
        isRunning = false
        BotStateController.setScreenCaptureActive(false)
        BotStateController.setVisionLatency(0L)
        BotStateController.addLog("Screen Capture stopped.")

        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing virtual display", e)
        }

        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing image reader", e)
        }

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media projection", e)
        }

        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}
