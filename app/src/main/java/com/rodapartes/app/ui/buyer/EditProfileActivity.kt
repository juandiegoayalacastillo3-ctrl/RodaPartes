package com.rodapartes.app.ui.buyer

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.UserRepository
import com.rodapartes.app.databinding.ActivityEditProfileBinding
import com.rodapartes.app.util.PhotoFiles
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.safeLaunch

/**
 * Editar perfil (RF-PER-01, RF-PER-02, RF-INI-04): nombre, celular, "Mi moto" y foto.
 * La foto se puede tomar con la cámara o elegir de la galería y se sube a Firebase Storage.
 */
class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private var newPhoto: Uri? = null
    private var cameraUri: Uri? = null

    /** Galería del sistema: no necesita permiso de almacenamiento. */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) showNewPhoto(uri)
    }

    /** App de cámara del sistema: guarda la foto en la URI que le pasamos. */
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) showNewPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val user = Session.user ?: return finish()
        binding.etName.setText(user.name)
        binding.etPhone.setText(user.phone.replace(" ", ""))
        binding.etEmail.setText(user.email)
        binding.actMoto.setText(user.moto, false)
        if (user.photoUrl.isNotBlank()) {
            binding.ivAvatar.imageTintList = null
            binding.ivAvatar.setPadding(0, 0, 0, 0)
            binding.ivAvatar.load(user.photoUrl)
        }

        binding.fabPhoto.setOnClickListener { choosePhotoSource() }
        binding.btnSave.setOnClickListener { save() }
    }

    /** Pregunta si la foto viene de la cámara o de la galería. */
    private fun choosePhotoSource() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_change_photo)
            .setItems(resources.getStringArray(R.array.photo_sources)) { _, which ->
                if (which == 0) {
                    cameraUri = PhotoFiles.newCameraUri(this)
                    takePhoto.launch(cameraUri!!)
                } else {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            .show()
    }

    private fun showNewPhoto(uri: Uri) {
        newPhoto = uri
        binding.ivAvatar.imageTintList = null
        binding.ivAvatar.setPadding(0, 0, 0, 0)
        binding.ivAvatar.setImageURI(uri)
    }

    /** Guarda los cambios en users/{uid} y sube la foto nueva si la hay. */
    private fun save() {
        val user = Session.user ?: return
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        binding.tilName.error = if (name.length < 3) getString(R.string.error_required) else null
        binding.tilPhone.error = if (phone.length != 10) getString(R.string.error_phone) else null
        if (binding.tilName.error != null || binding.tilPhone.error != null) return

        val moto = binding.actMoto.text.toString()
        binding.btnSave.isEnabled = false
        binding.btnSave.setText(R.string.loading)
        lifecycleScope.safeLaunch(binding.root, onDone = {
            binding.btnSave.isEnabled = true
            binding.btnSave.setText(R.string.action_save_changes)
        }) {
            UserRepository.update(user.uid, mapOf("name" to name, "phone" to phone, "moto" to moto))
            newPhoto?.let { user.photoUrl = UserRepository.uploadAvatar(user.uid, it) }
            user.name = name
            user.phone = phone
            user.moto = moto
            Toast.makeText(this, R.string.saved_ok, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
