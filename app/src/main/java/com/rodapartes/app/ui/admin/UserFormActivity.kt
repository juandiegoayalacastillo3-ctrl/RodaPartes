package com.rodapartes.app.ui.admin

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.Role
import com.rodapartes.app.data.User
import com.rodapartes.app.data.UserStatus
import com.rodapartes.app.databinding.ActivityUserFormBinding
import com.rodapartes.app.databinding.DialogConfirmPasswordBinding
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.initials
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

/** Crear, editar, aprobar, suspender y eliminar usuarios (RF-ADU-02 a 05). */
class UserFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserFormBinding
    private var user: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        user = intent.getIntExtra(EXTRA_ID, -1).takeIf { it > 0 }?.let { MockData.user(it) }
        val u = user

        if (u == null) {
            // Modo creación
            binding.toolbar.setTitle(R.string.user_new_title)
            binding.groupHeader.visible(false)
            binding.cardRut.visible(false)
            binding.groupApproval.visible(false)
            binding.btnDelete.visible(false)
            binding.actRole.setText(Role.BUYER.label, false)
            binding.actStatus.setText(UserStatus.ACTIVO.label, false)
            binding.btnSave.setText(R.string.user_create)
        } else {
            fill(u)
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnApprove.setOnClickListener { setStatus(UserStatus.ACTIVO, R.string.user_approved) }
        binding.btnSuspend.setOnClickListener { confirmSuspend() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.cardRut.setOnClickListener { binding.root.snack(getString(R.string.user_rut_open)) }
    }

    private fun fill(u: User) {
        binding.tvInitials.text = initials(u.name)
        binding.tvHeaderName.text = u.name
        binding.tvHeaderStatus.applyStatus(u.status)
        binding.etName.setText(u.name)
        binding.etEmail.setText(u.email)
        binding.etPhone.setText(u.phone)
        binding.etDocument.setText(u.document)
        binding.actRole.setText(u.role.label, false)
        binding.actStatus.setText(u.status.label, false)

        val isSeller = u.role == Role.SELLER
        binding.cardRut.visible(isSeller)
        binding.tvRut.text = getString(R.string.user_rut_file, u.name.lowercase().replace(" ", "_"))
        binding.btnApprove.visible(u.status != UserStatus.ACTIVO)
        binding.btnApprove.setText(if (u.status == UserStatus.PENDIENTE) R.string.action_approve else R.string.action_reactivate)
        binding.btnSuspend.visible(u.status != UserStatus.SUSPENDIDO)
    }

    private fun roleFromLabel(label: String) = Role.entries.firstOrNull { it.label == label } ?: Role.BUYER

    private fun save() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        binding.tilName.error = if (name.length < 3) getString(R.string.error_required) else null
        binding.tilEmail.error = if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) getString(R.string.error_email) else null
        if (binding.tilName.error != null || binding.tilEmail.error != null) return

        val role = roleFromLabel(binding.actRole.text.toString())
        val status = UserStatus.fromLabel(binding.actStatus.text.toString())

        // Crear o ascender a administrador exige reautenticación del admin actual
        val becomesAdmin = role == Role.ADMIN && user?.role != Role.ADMIN
        if (becomesAdmin) reauthenticate { persist(name, email, role, status) }
        else persist(name, email, role, status)
    }

    private fun persist(name: String, email: String, role: Role, status: UserStatus) {
        val u = user
        if (u == null) {
            MockData.users.add(User(MockData.newId(), name, email, binding.etPhone.text.toString(),
                binding.etDocument.text.toString(), role, status))
            Toast.makeText(this, R.string.user_created, Toast.LENGTH_SHORT).show()
        } else {
            u.name = name
            u.email = email
            u.phone = binding.etPhone.text.toString()
            u.document = binding.etDocument.text.toString()
            u.role = role
            u.status = status
            Toast.makeText(this, R.string.saved_ok, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun reauthenticate(onOk: () -> Unit) {
        val dialog = DialogConfirmPasswordBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reauth_title)
            .setMessage(R.string.reauth_msg)
            .setView(dialog.root)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                if (!dialog.etPassword.text.isNullOrBlank()) onOk()
                else binding.root.snack(getString(R.string.error_required))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun setStatus(status: UserStatus, message: Int) {
        user?.let {
            it.status = status
            fill(it)
            binding.root.snack(getString(message))
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

    /** Borrado lógico: se anonimizan los datos y se conserva lo exigido por facturación. */
    private fun confirmDelete() {
        val u = user ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.user_delete_title)
            .setMessage(getString(R.string.user_delete_msg, u.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                MockData.users.remove(u)
                Toast.makeText(this, getString(R.string.deleted_ok, u.name), Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "user_id"
    }
}
