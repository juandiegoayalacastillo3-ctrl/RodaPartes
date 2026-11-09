package com.rodapartes.app.ui.seller

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rodapartes.app.R
import com.rodapartes.app.databinding.ActivityCameraBinding
import com.rodapartes.app.util.color
import com.rodapartes.app.util.visible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Interfaz de cámara con dos modos: foto del producto y escáner de referencia.
 * Devuelve el resultado a quien la abrió (formulario de producto o buscador).
 * La captura real con CameraX y la lectura con ML Kit se conectan en la siguiente fase.
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var mode = MODE_SCAN
    private var scanAnimator: ObjectAnimator? = null
    private var flashOn = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_PHOTO, uri.toString()))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }
        binding.btnFlash.setOnClickListener {
            flashOn = !flashOn
            binding.btnFlash.alpha = if (flashOn) 1f else 0.5f
        }
        binding.btnFlash.alpha = 0.5f

        binding.tabPhoto.setOnClickListener { setMode(MODE_PHOTO) }
        binding.tabScan.setOnClickListener { setMode(MODE_SCAN) }
        binding.tabGallery.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnShutter.setOnClickListener { capture() }

        setMode(intent.getIntExtra(EXTRA_MODE, MODE_SCAN))
    }

    private fun setMode(newMode: Int) {
        mode = newMode
        val scan = mode == MODE_SCAN
        highlight(binding.tabScan, scan)
        highlight(binding.tabPhoto, !scan)
        binding.scanLine.visible(scan)
        binding.ivBarcode.visible(scan)
        binding.tvHint.setText(if (scan) R.string.camera_hint_scan else R.string.camera_hint_photo)
        if (scan) startScanAnimation() else scanAnimator?.cancel()
    }

    private fun highlight(tab: TextView, on: Boolean) {
        tab.setTextColor(color(if (on) R.color.scan_linea else R.color.texto_3))
    }

    /** La línea del escáner sube y baja dentro del marco. */
    private fun startScanAnimation() {
        binding.frame.post {
            val travel = (binding.frame.height - binding.scanLine.height - 40).toFloat()
            scanAnimator?.cancel()
            scanAnimator = ObjectAnimator.ofFloat(binding.scanLine, "translationY", 20f, travel).apply {
                duration = 1600
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun capture() {
        binding.btnShutter.isEnabled = false
        // Destello blanco breve como retroalimentación de la captura
        binding.root.animate().alpha(0.3f).setDuration(80).withEndAction {
            binding.root.animate().alpha(1f).setDuration(120).start()
        }.start()
        lifecycleScope.launch {
            delay(450)
            val data = Intent()
            if (mode == MODE_SCAN) data.putExtra(EXTRA_CODE, DEMO_CODE)
            setResult(RESULT_OK, data)
            finish()
        }
    }

    override fun onDestroy() {
        scanAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_CODE = "code"
        const val EXTRA_PHOTO = "photo"
        const val MODE_PHOTO = 1
        const val MODE_SCAN = 2

        /** Referencia de prueba: coincide con las pastillas de freno del catálogo. */
        private const val DEMO_CODE = "PF-AKT125-D"
    }
}
