package com.javier.autoscroll

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class FloatingButtonService : Service() {
    private var wm: WindowManager? = null
    private var panel: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    override fun onCreate() {
        super.onCreate()
        if (panel != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = LayoutInflater.from(this).inflate(R.layout.floating_buttons, null, false)
        panel = v
        val type = if (android.os.Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 24; lp.y = 240
        params = lp
        v.findViewById<TextView>(R.id.btnUp).setOnClickListener { ScrollAccessibilityService.startScroll(true) }
        v.findViewById<TextView>(R.id.btnDown).setOnClickListener { ScrollAccessibilityService.startScroll(false) }
        v.findViewById<TextView>(R.id.btnPause).setOnClickListener { ScrollAccessibilityService.stopScrolling() }
        v.findViewById<TextView>(R.id.btnClose).setOnClickListener {
            ScrollAccessibilityService.stopScrolling()
            stopSelf()
        }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX=e.rawX; downY=e.rawY; startX=lp.x; startY=lp.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=(e.rawX-downX).toInt(); val dy=(e.rawY-downY).toInt()
                    if (!moved && (abs(dx)>8 || abs(dy)>8)) moved=true
                    if (moved) { lp.x=startX+dx; lp.y=startY+dy; try { wm?.updateViewLayout(v,lp) } catch (_: Exception) {} }
                    true
                }
                MotionEvent.ACTION_UP -> moved
                else -> false
            }
        }
        try { wm?.addView(v, lp) } catch (_: Exception) { panel=null; stopSelf() }
    }
    override fun onDestroy() {
        ScrollAccessibilityService.stopScrolling()
        panel?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        panel=null; params=null; wm=null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}