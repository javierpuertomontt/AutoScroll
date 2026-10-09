package com.javier.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import android.util.DisplayMetrics
import android.content.Context
import android.graphics.Point
import android.view.Display
import android.view.WindowManager as WM
import java.util.concurrent.atomic.AtomicBoolean

class ScrollAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var swipeUp = true
    private var gesturePending = false
    private var lastPackage: String? = null
    private var screenW = 1080
    private var screenH = 2200

    override fun onServiceConnected() {
        instance = this
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenW=metrics.widthPixels; screenH=metrics.heightPixels
        } catch (_: Exception) {}
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != packageName) lastPackage = pkg
        }
    }
    override fun onInterrupt() { stopScrolling() }
    override fun onDestroy() { stopScrolling(); if (instance === this) instance=null; super.onDestroy() }

    private fun tick() {
        if (!running.get() || gesturePending) return
        val svc = instance ?: run { stopScrolling(); return }
        val p = Path()
        val x = screenW * 0.5f
        val top = screenH * 0.30f
        val bottom = screenH * 0.85f
        p.moveTo(x, if (swipeUp) top else bottom)
        p.lineTo(x, if (swipeUp) bottom else top)
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 80)).build()
        gesturePending = true
        val accepted = svc.dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { gesturePending=false }
            override fun onCancelled(gestureDescription: GestureDescription?) { gesturePending=false }
        }, handler)
        if (!accepted) gesturePending=false
        if (running.get()) handler.postDelayed({ tick() }, 150)
    }

    companion object {
        @Volatile private var instance: ScrollAccessibilityService? = null
        @Volatile private var active = false
        fun isConnected() = instance != null
        fun startScroll(up: Boolean) {
            val s=instance ?: return
            s.swipeUp=up
            active=true
            if (s.running.compareAndSet(false,true)) s.handler.post { s.tick() }
            else if (!s.gesturePending) s.handler.post { s.tick() }
        }
        fun stopScrolling() {
            active=false
            instance?.let { s -> s.running.set(false); s.handler.removeCallbacksAndMessages(null); s.gesturePending=false }
        }
    }
}