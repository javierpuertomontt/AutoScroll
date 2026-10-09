package com.javier.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

class ScrollAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scrolling = AtomicBoolean(false)
    @Volatile private var fingerMovesDown = true
    @Volatile private var gesturePending = false
    @Volatile private var sawScrollEvent = false
    private var screenWidth = 1080
    private var screenHeight = 2200
    private var swipesInBurst = 0
    private var stalledBursts = 0

    private val nextSwipe = object : Runnable {
        override fun run() { performSwipe() }
    }

    private val continueAfterLoad = object : Runnable {
        override fun run() {
            if (scrolling.get()) performSwipe()
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
        } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            sawScrollEvent = true
        }
    }

    override fun onInterrupt() { stopScrolling() }

    override fun onDestroy() {
        stopScrolling()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun performSwipe() {
        if (!scrolling.get()) return
        if (gesturePending) {
            mainHandler.postDelayed(nextSwipe, 80L)
            return
        }

        // Faster, longer swipe to cover more of the conversation with each gesture.
        val x = screenWidth * 0.5f
        val top = screenHeight * 0.16f
        val bottom = screenHeight * 0.88f
        val path = Path().apply {
            moveTo(x, if (fingerMovesDown) top else bottom)
            lineTo(x, if (fingerMovesDown) bottom else top)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()

        sawScrollEvent = false
        gesturePending = true
        val accepted = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gesturePending = false
                    if (!scrolling.get()) return
                    swipesInBurst++
                    if (swipesInBurst >= SWIPES_PER_BURST) {
                        // Instagram may need a moment to fetch the next batch of old messages.
                        mainHandler.postDelayed({
                            if (!scrolling.get()) return@postDelayed
                            if (!sawScrollEvent) stalledBursts++ else stalledBursts = 0
                            if (stalledBursts >= MAX_STALLED_BURSTS) {
                                stopScrolling()
                                return@postDelayed
                            }
                            swipesInBurst = 0
                            sawScrollEvent = false
                            performSwipe()
                        }, LOAD_WAIT_MS)
                    } else {
                        mainHandler.postDelayed(nextSwipe, SWIPE_GAP_MS)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gesturePending = false
                    if (scrolling.get()) mainHandler.postDelayed(nextSwipe, SWIPE_GAP_MS)
                }
            }, mainHandler)
        } catch (_: Exception) { false }

        if (!accepted) {
            gesturePending = false
            mainHandler.postDelayed(nextSwipe, 120L)
        }
    }

    companion object {
        private const val SWIPE_DURATION_MS = 70L
        private const val SWIPE_GAP_MS = 35L
        private const val SWIPES_PER_BURST = 4
        private const val LOAD_WAIT_MS = 550L
        private const val MAX_STALLED_BURSTS = 5

        @Volatile private var instance: ScrollAccessibilityService? = null
        fun isConnected(): Boolean = instance != null

        fun isEnabled(context: Context): Boolean {
            val expected = context.packageName + "/" + ScrollAccessibilityService::class.java.name
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
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
                service.swipesInBurst = 0
                service.stalledBursts = 0
                service.sawScrollEvent = true
                service.mainHandler.removeCallbacks(service.nextSwipe)
                service.mainHandler.removeCallbacks(service.continueAfterLoad)
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
            service.mainHandler.removeCallbacks(service.continueAfterLoad)
        }
    }
}
