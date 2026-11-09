package com.rodapartes.app.ui.buyer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.databinding.ActivityEditProfileBinding
import com.rodapartes.app.util.Session

/** Editar nombre, celular, foto y "Mi moto" (RF-PER-01, RF-PER-02, RF-INI-04). */
class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var session: Session

    // Selector de fotos del sistema: no necesita permiso de almacenamiento
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            binding.ivAvatar.imageTintList = null
            binding.ivAvatar.setPadding(0, 0, 0, 0)
            binding.ivAvatar.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = Session(this)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.etName.setText(session.name)
        binding.etPhone.setText(session.phone.replace(" ", ""))
        binding.etEmail.setText(session.email)
        binding.actMoto.setText(MockData.myMoto, false)

        binding.fabPhoto.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        binding.tilName.error = if (name.length < 3) getString(R.string.error_required) else null
        binding.tilPhone.error = if (phone.length != 10) getString(R.string.error_phone) else null
        if (binding.tilName.error != null || binding.tilPhone.error != null) return

        session.name = name
        session.phone = phone
        MockData.myMoto = binding.actMoto.text.toString()
        Toast.makeText(this, R.string.saved_ok, Toast.LENGTH_SHORT).show()
        finish()
    }
}
