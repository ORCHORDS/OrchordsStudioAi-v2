package com.orchords.orchordsai.ui.activity

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.orchords.orchordsai.OrchordsAiActivity
import com.orchords.orchordsai.data.files.StagedShareRoot
import com.orchords.orchordsai.data.files.createStagedShareUri
import java.io.File

class ShortcutHandlerActivity : ComponentActivity() {
    private var photoURI: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            finish()
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoURI?.let {
                val intent = Intent(this, OrchordsAiActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, it.toString())
                }
                startActivity(intent)
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTrustedCameraShortcutInvocation(intent?.action, intent?.dataString)) {
            finish()
            return
        }
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val cameraDir = File(cacheDir, "camera").apply { mkdirs() }
        val imageFile = File(cameraDir, "shortcut_camera_image.jpg")
        imageFile.parentFile?.mkdirs()
        if (!imageFile.exists()) imageFile.createNewFile()
        photoURI = createStagedShareUri(this, imageFile, StagedShareRoot.CAMERA)
        photoURI?.let {
            takePictureLauncher.launch(it)
        } ?: finish()
    }
}
