package com.javier.autoscroll

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.content.Context
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 21)
            } else Toast.makeText(this, "Permiso de superposición ya activo", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.startButton).setOnClickListener { startOverlay() }
    }
    override fun onResume() {
        super.onResume()
        findViewById<Button>(R.id.startButton).isEnabled = overlayAllowed()
    }
    private fun overlayAllowed() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
    private fun startOverlay() {
        if (!overlayAllowed()) {
            Toast.makeText(this, "Primero permite mostrar sobre otras apps", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!ScrollAccessibilityService.isConnected()) {
            Toast.makeText(this, "Activa AutoScroll en Accesibilidad y vuelve a intentarlo", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        startService(Intent(this, FloatingButtonService::class.java))
        moveTaskToBack(true)
    }
}