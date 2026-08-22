package com.example.service

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
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.aistudio.fruitninjabot.fnxbot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ScreenCaptureService - High-FPS Screen Frame Grabber & Vision Processing Pipeline.
 *
 * Uses MediaProjection and ImageReader to acquire screen frames asynchronously,
 * passes frame data to ColorDetector for pixel color classification,
 * and commands AutoTouchService to execute swipes, combos, and click gestures.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA_INTENT = "extra_data_intent"
        const val ACTION_STOP = "action_stop_capture"

        private const val CHANNEL_ID = "channel_screen_capture"
        private const val NOTIF_ID = 2002

        private var _instance: ScreenCaptureService? = null
        val instance: ScreenCaptureService?
            get() = _instance

        fun isCapturing(): Boolean = _instance != null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var processingJob: Job? = null

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420

    // Downscaled processing resolution for ultra-fast zero-latency color parsing
    private var processWidth = 270
    private var processHeight = 600

    // Reusable Bitmap to avoid high-frequency GC allocations
    private var reusableBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        _instance = this
        createNotificationChannel()
        startForegroundWithNotification()
        measureScreen()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA_INTENT)
        }

        if (resultCode == -1 || data == null) {
            Log.e(TAG, "Missing MediaProjection resultCode or data intent")
            stopSelf()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d(TAG, "MediaProjection stopped by system")
                cleanupCapture()
                stopSelf()
            }
        }, null)

        setupVirtualDisplay()
        startProcessingLoop()

        BotStateController.setScreenCaptureActive(true)
        BotStateController.addLog("ScreenCaptureService started with ColorDetector pipeline.")

        return START_STICKY
    }

    private fun measureScreen() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.currentWindowMetrics
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = resources.displayMetrics.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }

        // 1/4 resolution sampling keeps memory footprint miniscule & ensures 60 FPS analysis
        processWidth = (screenWidth / 4).coerceAtLeast(160)
        processHeight = (screenHeight / 4).coerceAtLeast(320)
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(
            processWidth,
            processHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "FruitNinjaVisionDisplay",
            processWidth,
            processHeight,
            screenDensity / 4,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun startProcessingLoop() {
        processingJob?.cancel()
        processingJob = serviceScope.launch {
            var lastSliceTime = 0L
            var lastClickTime = 0L

            while (isActive) {
                val config = BotStateController.config.value
                val isBotRunning = BotStateController.botRunState.value == BotRunState.RUNNING

                var image: Image? = null
                try {
                    image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * processWidth

                        val allocWidth = processWidth + rowPadding / pixelStride

                        // Reallocate or reuse bitmap efficiently
                        val bmp = if (reusableBitmap != null &&
                            reusableBitmap?.width == allocWidth &&
                            reusableBitmap?.height == processHeight
                        ) {
                            reusableBitmap!!
                        } else {
                            reusableBitmap?.recycle()
                            Bitmap.createBitmap(allocWidth, processHeight, Bitmap.Config.ARGB_8888).also {
                                reusableBitmap = it
                            }
                        }

                        bmp.copyPixelsFromBuffer(buffer)

                        val croppedBitmap = if (rowPadding > 0) {
                            Bitmap.createBitmap(bmp, 0, 0, processWidth, processHeight)
                        } else {
                            bmp
                        }

                        // Feed frame into ColorDetector vision utility
                        val visionResult = ColorDetector.analyzeFrame(
                            croppedBitmap,
                            screenWidth,
                            screenHeight,
                            config
                        )

                        BotStateController.setVisionLatency(visionResult.processDurationMs)

                        // Update detection counters
                        if (visionResult.fruits.isNotEmpty()) {
                            BotStateController.incrementDetectedCount(visionResult.fruits.size)
                        }

                        val now = System.currentTimeMillis()
                        val autoTouch = AutoTouchService.instance

                        if (isBotRunning && autoTouch != null) {
                            // 1. Prioritize fruit slicing gestures
                            val sliceCooldown = (1000L / config.targetFps).coerceIn(20L, 120L)
                            if (visionResult.suggestedSlices.isNotEmpty() && (now - lastSliceTime) >= sliceCooldown) {
                                val topSlice = visionResult.suggestedSlices.first()
                                if (topSlice.points.size >= 2) {
                                    autoTouch.performMultiSwipe(
                                        topSlice.points,
                                        config.sliceDurationMs
                                    )
                                } else {
                                    autoTouch.performSwipe(
                                        topSlice.startX,
                                        topSlice.startY,
                                        topSlice.endX,
                                        topSlice.endY,
                                        config.sliceDurationMs
                                    )
                                }
                                lastSliceTime = now

                                if (topSlice.isMenuTrigger) {
                                    BotStateController.incrementMenuClicksCount(1)
                                    BotStateController.addLog("Auto-sliced lobby play fruit!")
                                }
                            }

                            // 2. Handle Ad/Skip Dialog Close button clicks (rate-limited)
                            if (config.autoSkipAds && visionResult.suggestedClicks.isNotEmpty() && (now - lastClickTime) >= 1500L) {
                                val (clickX, clickY) = visionResult.suggestedClicks.first()
                                autoTouch.performClick(clickX, clickY)
                                lastClickTime = now
                                BotStateController.incrementAdSkipsCount(1)
                                BotStateController.addLog("Auto-clicked ad skip at (${clickX.toInt()}, ${clickY.toInt()})")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Frame vision error: ${e.message}")
                } finally {
                    try {
                        image?.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                val frameDelay = (1000L / config.targetFps.coerceIn(15, 60)).coerceAtLeast(16L)
                delay(frameDelay)
            }
        }
    }

    private fun startForegroundWithNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fruit Ninja Vision Bot Active")
            .setContentText("Continuous color frame analyzer & swipe dispatcher running")
            .setSmallIcon(R.drawable.fruit_ninja_bot_icon_1787345790782)
            .setContentIntent(pendingOpen)
            .addAction(R.drawable.fruit_ninja_bot_icon_1787345790782, "Stop", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen analysis engine for Fruit Ninja slicing automation"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun cleanupCapture() {
        processingJob?.cancel()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        reusableBitmap?.recycle()
        reusableBitmap = null
        BotStateController.setScreenCaptureActive(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupCapture()
        serviceScope.cancel()
        if (_instance == this) {
            _instance = null
        }
        BotStateController.setScreenCaptureActive(false)
        BotStateController.addLog("ScreenCaptureService stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
