package com.javier.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.provider.Settings
import android.text.TextUtils
import java.util.concurrent.atomic.AtomicBoolean

class ScrollAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scrolling = AtomicBoolean(false)
    @Volatile private var fingerMovesDown = true
    @Volatile private var gesturePending = false
    private var screenWidth = 1080
    private var screenHeight = 2200

    private val nextSwipe = object : Runnable {
        override fun run() {
            performSwipe()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateScreenSize()
    }

    private fun updateScreenSize() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
            }
        } catch (_: Exception) {
            // Conserva dimensiones de respaldo si el fabricante bloquea la consulta.
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No dependemos de eventos de cambio de ventana para ejecutar los gestos:
        // algunos firmwares MIUI/HyperOS no los notifican de manera consistente.
    }

    override fun onInterrupt() {
        stopScrolling()
    }

    override fun onDestroy() {
        stopScrolling()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun performSwipe() {
        if (!scrolling.get()) return
        if (gesturePending) {
            mainHandler.postDelayed(nextSwipe, RETRY_DELAY_MS)
            return
        }

        val x = screenWidth * 0.5f
        val top = screenHeight * TOP_LIMIT
        val bottom = screenHeight * BOTTOM_LIMIT
        val path = Path().apply {
            moveTo(x, if (fingerMovesDown) top else bottom)
            lineTo(x, if (fingerMovesDown) bottom else top)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()

        gesturePending = true
        val accepted = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gesturePending = false
                    scheduleNext()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gesturePending = false
                    scheduleNext()
                }
            }, mainHandler)
        } catch (_: Exception) {
            false
        }

        if (!accepted) {
            gesturePending = false
            mainHandler.postDelayed(nextSwipe, RETRY_DELAY_MS)
        }
    }

    private fun scheduleNext() {
        if (scrolling.get()) mainHandler.postDelayed(nextSwipe, SWIPE_INTERVAL_MS)
    }

    companion object {
        private const val TOP_LIMIT = 0.28f
        private const val BOTTOM_LIMIT = 0.82f
        private const val SWIPE_DURATION_MS = 110L
        private const val SWIPE_INTERVAL_MS = 140L
        private const val RETRY_DELAY_MS = 250L

        @Volatile private var instance: ScrollAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun isEnabled(context: Context): Boolean {
            val expected = context.packageName + "/" + ScrollAccessibilityService::class.java.name
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expected, ignoreCase = true)) return true
            }
            return false
        }

        fun startScroll(fingerDown: Boolean): Boolean {
            val service = instance ?: return false
            service.fingerMovesDown = fingerDown
            if (service.scrolling.compareAndSet(false, true)) {
                service.mainHandler.removeCallbacks(service.nextSwipe)
                service.mainHandler.post(service.nextSwipe)
            } else if (!service.gesturePending) {
                service.mainHandler.removeCallbacks(service.nextSwipe)
                service.mainHandler.post(service.nextSwipe)
            }
            return true
        }

        fun stopScrolling() {
            val service = instance ?: return
            service.scrolling.set(false)
            service.mainHandler.removeCallbacks(service.nextSwipe)
            // No se fuerza gesturePending=false: el sistema puede seguir ejecutando
            // el gesto ya despachado y limpiará el indicador en su callback.
        }
    }
}