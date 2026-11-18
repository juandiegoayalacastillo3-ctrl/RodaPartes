package com.rodapartes.app.ui.admin

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.AppUser
import com.rodapartes.app.data.AuditRepository
import com.rodapartes.app.data.AuthRepository
import com.rodapartes.app.data.Role
import com.rodapartes.app.data.UserRepository
import com.rodapartes.app.data.UserStatus
import com.rodapartes.app.databinding.ActivityUserFormBinding
import com.rodapartes.app.ui.auth.LoginActivity
import com.rodapartes.app.util.BiometricSetup
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.initials
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.visible
import kotlinx.coroutines.launch

/**
 * CRUD de usuarios del administrador (RF-ADU-02 a RF-ADU-05), con persistencia en Firestore:
 * crear (con contraseña inicial), editar datos, rol y estado, aprobar vendedores, suspender
 * y eliminar (borrado lógico). Cada acción queda en la bitácora de auditoría.
 */
class UserFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserFormBinding
    private var user: AppUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.cardRut.visible(false)

        val uid = intent.getStringExtra(EXTRA_ID)
        if (uid == null) {
            // Modo creación
            binding.toolbar.setTitle(R.string.user_new_title)
            binding.groupHeader.visible(false)
            binding.groupApproval.visible(false)
            binding.btnDelete.visible(false)
            binding.actRole.setText(Role.BUYER.label, false)
            binding.actStatus.setText(UserStatus.ACTIVO.label, false)
            binding.btnSave.setText(R.string.user_create)
        } else {
            binding.tilPassword.visible(false)
            binding.tilEmail.isEnabled = false // el correo de acceso no se cambia desde aquí
            binding.btnSave.isEnabled = false
            lifecycleScope.launch {
                runCatching { UserRepository.get(uid) }.getOrNull()?.let {
                    user = it
                    fill(it)
                    binding.btnSave.isEnabled = true
                }
            }
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnApprove.setOnClickListener { setStatus(UserStatus.ACTIVO, R.string.user_approved) }
        binding.btnSuspend.setOnClickListener { confirmSuspend() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    /** Llena el formulario con el usuario leído de Firestore. */
    private fun fill(u: AppUser) {
        binding.tvInitials.text = initials(u.displayName)
        binding.tvHeaderName.text = u.displayName
        binding.tvHeaderStatus.applyStatus(u.statusEnum)
        binding.etName.setText(u.name)
        binding.etEmail.setText(u.email)
        binding.etPhone.setText(u.phone)
        binding.etDocument.setText(u.document)
        binding.actRole.setText(u.roleEnum.label, false)
        binding.actStatus.setText(u.statusEnum.label, false)

        binding.btnApprove.visible(u.statusEnum != UserStatus.ACTIVO)
        binding.btnApprove.setText(if (u.statusEnum == UserStatus.PENDIENTE) R.string.action_approve else R.string.action_reactivate)
        binding.btnSuspend.visible(u.statusEnum != UserStatus.SUSPENDIDO)
        // El administrador no puede suspenderse ni eliminarse a sí mismo
        val isMe = u.uid == Session.user?.uid
        binding.groupApproval.visible(!isMe)
        binding.btnDelete.visible(!isMe)
    }

    /** Valida y guarda. Crear o ascender a un administrador pide la contraseña del admin actual. */
    private fun save() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        binding.tilName.error = if (name.length < 3) getString(R.string.error_required) else null
        binding.tilEmail.error = if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) getString(R.string.error_email) else null
        binding.tilPassword.error = if (user == null && !LoginActivity.isValidPassword(password)) getString(R.string.password_rule) else null
        if (listOf(binding.tilName, binding.tilEmail, binding.tilPassword).any { it.error != null }) return

        val role = Role.fromLabel(binding.actRole.text.toString())
        val status = UserStatus.fromLabel(binding.actStatus.text.toString())

        val becomesAdmin = role == Role.ADMIN && user?.roleEnum != Role.ADMIN
        if (becomesAdmin) {
            BiometricSetup.askPassword(this, R.string.reauth_title) { adminPassword ->
                lifecycleScope.safeLaunch(binding.root) {
                    AuthRepository.reauthenticate(adminPassword)
                    persist(name, email, password, role, status)
                }
            }
        } else {
            lifecycleScope.safeLaunch(binding.root) { persist(name, email, password, role, status) }
        }
    }

    /** Crea la cuenta (Auth + Firestore) o actualiza el documento existente. */
    private suspend fun persist(name: String, email: String, password: String, role: Role, status: UserStatus) {
        binding.btnSave.isEnabled = false
        try {
            val u = user
            if (u == null) {
                val newUser = AppUser(
                    name = name,
                    email = email,
                    phone = binding.etPhone.text.toString().trim(),
                    document = binding.etDocument.text.toString().trim(),
                    role = role.name,
                    status = status.name,
                    storeName = if (role == Role.SELLER) name else ""
                )
                UserRepository.adminCreate(this, newUser, password)
                AuditRepository.log("Creó a $name ($email) como ${role.label}")
                Toast.makeText(this, R.string.user_created, Toast.LENGTH_SHORT).show()
            } else {
                UserRepository.update(u.uid, mapOf(
                    "name" to name,
                    "phone" to binding.etPhone.text.toString().trim(),
                    "document" to binding.etDocument.text.toString().trim(),
                    "role" to role.name,
                    "status" to status.name
                ))
                if (u.roleEnum != role) AuditRepository.log("Cambió el rol de ${u.displayName} a ${role.label}")
                if (u.statusEnum != status) AuditRepository.log("Cambió el estado de ${u.displayName} a ${status.label}")
                Toast.makeText(this, R.string.saved_ok, Toast.LENGTH_SHORT).show()
            }
            finish()
        } finally {
            binding.btnSave.isEnabled = true
        }
    }

    /** Aprobar, reactivar o suspender en un toque. */
    private fun setStatus(status: UserStatus, message: Int) {
        val u = user ?: return
        lifecycleScope.safeLaunch(binding.root) {
            UserRepository.update(u.uid, mapOf("status" to status.name))
            AuditRepository.log("${status.label}: ${u.displayName}")
            u.status = status.name
            fill(u)
            Toast.makeText(this@UserFormActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmSuspend() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.user_suspend_title)
            .setMessage(R.string.user_suspend_msg)
            .setPositiveButton(R.string.action_suspend) { _, _ -> setStatus(UserStatus.SUSPENDIDO, R.string.user_suspended) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Borrado lógico: se anonimizan los datos y la cuenta ya no puede iniciar sesión. */
    private fun confirmDelete() {
        val u = user ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.user_delete_title)
            .setMessage(getString(R.string.user_delete_msg, u.displayName))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.safeLaunch(binding.root) {
                    UserRepository.adminDelete(u.uid)
                    AuditRepository.log("Eliminó a ${u.displayName} (${u.email})")
                    Toast.makeText(this@UserFormActivity, getString(R.string.deleted_ok, u.displayName), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "user_id"
    }
}
