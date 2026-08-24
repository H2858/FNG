package com.ninjabot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import com.ninjabot.MainActivity
import com.ninjabot.vision.FrameAnalyzer
import com.ninjabot.vision.SlicePlanner

/**
 * خدمة أمامية (Foreground Service) تلتقط الشاشة عبر MediaProjection،
 * تحلّل كل إطار جديد للكشف عن الفواكه والقنابل، ثم تطلب من
 * NinjaAccessibilityService تنفيذ حركات التقطيع الآمنة فقط.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "ninja_bot_channel"
        const val NOTIF_ID = 1
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        // حد أدنى بين الإطارات المُحلَّلة لتفادي إثقال المعالج (بالميلي ثانية)
        const val ANALYSIS_INTERVAL_MS = 80L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private val frameAnalyzer = FrameAnalyzer()
    private val slicePlanner = SlicePlanner()

    private var lastAnalysisTime = 0L
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        backgroundThread = HandlerThread("NinjaBotCaptureThread").apply { start() }
        backgroundHandler = Handler(backgroundThread.looper)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        startForeground(NOTIF_ID, buildNotification())

        if (resultCode != -1 && resultData != null) {
            startCapture(resultCode, resultData)
        }
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "NinjaBotCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, backgroundHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            if (now - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
                // تجاهل الإطار للحفاظ على معدل تحليل ثابت وغير مكلف للبطارية
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            lastAnalysisTime = now

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = imageToBitmap(image)
                processFrame(bitmap)
                bitmap.recycle()
            } finally {
                image.close()
            }
        }, backgroundHandler)
    }

    private fun processFrame(bitmap: Bitmap) {
        val detections = frameAnalyzer.analyze(bitmap)
        if (detections.isEmpty()) return

        val safeSlices = slicePlanner.plan(detections)
        if (safeSlices.isEmpty()) return

        NinjaAccessibilityService.instance?.performSlices(safeSlices)
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Ninja Bot", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ninja Bot يعمل")
            .setContentText("يراقب الشاشة ويقطّع الفواكه تلقائيا مع تجنّب القنابل")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        backgroundThread.quitSafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
