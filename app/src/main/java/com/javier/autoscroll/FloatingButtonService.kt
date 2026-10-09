package com.javier.autoscroll

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingButtonService : Service() {
    private var windowManager: WindowManager? = null
    private var panel: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var activeButton: TextView? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            showPanel()
        } catch (e: Exception) {
            Toast.makeText(this, "AutoScroll: no se pudieron mostrar los controles.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (panel == null) showPanel()
        return START_STICKY
    }

    private fun showPanel() {
        if (panel != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.floating_buttons, null, false)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 240
        }
        layoutParams = lp
        panel = view

        val up = view.findViewById<TextView>(R.id.btnUp)
        val pause = view.findViewById<TextView>(R.id.btnPause)
        val down = view.findViewById<TextView>(R.id.btnDown)
        val close = view.findViewById<TextView>(R.id.btnClose)

        up.setOnClickListener {
            if (ScrollAccessibilityService.startScroll(true)) highlight(up)
            else Toast.makeText(this, "Activa AutoScroll v12 en Accesibilidad.", Toast.LENGTH_LONG).show()
        }
        down.setOnClickListener {
            if (ScrollAccessibilityService.startScroll(false)) highlight(down)
            else Toast.makeText(this, "El servicio de Accesibilidad no está conectado. Abre AutoScroll otra vez.", Toast.LENGTH_LONG).show()
        }
        pause.setOnClickListener {
            ScrollAccessibilityService.stopScrolling()
            highlight(null)
        }
        close.setOnClickListener {
            ScrollAccessibilityService.stopScrolling()
            stopSelf()
        }

        // Cada botón permite tocar para activar y arrastrar el panel desde ese mismo botón.
        listOf(up, pause, down, close).forEach { button ->
            var downRawX = 0f
            var downRawY = 0f
            var originX = 0
            var originY = 0
            var moved = false
            button.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        originX = lp.x
                        originY = lp.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downRawX).toInt()
                        val dy = (event.rawY - downRawY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        if (moved) {
                            lp.x = (originX + dx).coerceAtLeast(0)
                            lp.y = (originY + dy).coerceAtLeast(0)
                            try { windowManager?.updateViewLayout(view, lp) } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) v.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
        }

        try {
            windowManager?.addView(view, lp)
        } catch (_: Exception) {
            panel = null
            layoutParams = null
            stopSelf()
        }
    }

    private fun highlight(button: TextView?) {
        activeButton?.background = rippleBackground()
        activeButton = button
        if (button != null) {
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(0x553797EF)
            }
        }
    }

    private fun rippleBackground() = android.graphics.drawable.ColorDrawable(0x0018243A)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Controles AutoScroll", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("AutoScroll v12 activo")
            .setContentText("Los controles flotantes están disponibles.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        ScrollAccessibilityService.stopScrolling()
        panel?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        panel = null
        layoutParams = null
        windowManager = null
        activeButton = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "autoscroll_controls"
        private const val NOTIFICATION_ID = 731
    }
}