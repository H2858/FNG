package com.ninjabot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.ninjabot.vision.SlicePath

/**
 * خدمة إمكانية الوصول: المسؤولة الوحيدة عن لمس الشاشة فعليا.
 * تستقبل مسارات التقطيع الآمنة (بعيدة عن القنابل) من ScreenCaptureService
 * وتحوّلها إلى حركة سحب (swipe gesture) حقيقية عبر dispatchGesture.
 */
class NinjaAccessibilityService : AccessibilityService() {

    companion object {
        // مرجع ثابت بسيط يسمح لخدمة التقاط الشاشة بإرسال أوامر اللمس مباشرة.
        // (بديل خفيف عن Binder/AIDL لأن كلتا الخدمتين تعملان داخل نفس التطبيق)
        @Volatile
        var instance: NinjaAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لسنا بحاجة لقراءة محتوى الشاشة هنا، الكشف يتم عبر الصورة (MediaProjection)
    }

    override fun onInterrupt() {}

    /** ينفّذ مسار تقطيع واحد كحركة سحب سريعة (لتقليد إصبع اللاعب الحقيقي) */
    fun performSlice(slice: SlicePath, durationMs: Long = 60) {
        val path = Path().apply {
            moveTo(slice.startX.toFloat(), slice.startY.toFloat())
            lineTo(slice.endX.toFloat(), slice.endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun performSlices(slices: List<SlicePath>) {
        // تنفيذ كل المسارات المكتشفة في نفس الإطار كضربات سحب متتالية سريعة
        slices.forEach { performSlice(it) }
    }
}
