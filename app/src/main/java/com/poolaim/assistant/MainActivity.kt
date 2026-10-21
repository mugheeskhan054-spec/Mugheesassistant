package com.poolaim.assistant

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, AimAssistService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            startForegroundService(serviceIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val button = Button(this).apply {
            text = "Start Aim Assistant"
            setOnClickListener { checkPermissionsAndStart() }
        }
        setContentView(button)
    }

    private fun checkPermissionsAndStart() {
      if (!Settings.canDrawOverlays(this)) {
          val intent = Intent(
              Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
              Uri.parse("package:$packageName")
          )
          startActivity(intent)
          return
      }

      val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      captureLauncher.launch(manager.createScreenCaptureIntent())
    }
}
