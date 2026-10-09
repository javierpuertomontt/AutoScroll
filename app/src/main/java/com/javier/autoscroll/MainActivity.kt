package com.javier.autoscroll

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)

        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            if (overlayAllowed()) {
                Toast.makeText(this, "El permiso para mostrar sobre otras apps ya está activo", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_OVERLAY)
            }
        }

        startButton.setOnClickListener { openControls() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun overlayAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun refreshStatus() {
        val overlay = overlayAllowed()
        val accessibility = ScrollAccessibilityService.isEnabled(this)
        statusText.text = when {
            overlay && accessibility -> "Todo listo. Puedes abrir los controles flotantes."
            !overlay && !accessibility -> "Falta activar los dos permisos."
            !overlay -> "Falta el permiso para mostrar sobre otras aplicaciones."
            else -> "Falta activar el servicio AutoScroll en Accesibilidad."
        }
        startButton.isEnabled = overlay && accessibility
        startButton.alpha = if (startButton.isEnabled) 1f else 0.55f
    }

    private fun openControls() {
        if (!overlayAllowed()) {
            Toast.makeText(this, "Activa primero «Mostrar sobre otras apps».", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!ScrollAccessibilityService.isConnected()) {
            Toast.makeText(this, "Activa AutoScroll v12 en Accesibilidad y vuelve aquí.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        try {
            startService(Intent(this, FloatingButtonService::class.java))
            Toast.makeText(this, "Controles abiertos. Toca ▲ o ▼ para comenzar.", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudieron abrir los controles: ${e.localizedMessage ?: "error"}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQUEST_OVERLAY = 21
    }
}