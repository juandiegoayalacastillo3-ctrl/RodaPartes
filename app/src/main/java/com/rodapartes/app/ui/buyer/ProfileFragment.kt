package com.rodapartes.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.databinding.DialogConfirmPasswordBinding
import com.rodapartes.app.databinding.FragmentProfileBinding
import com.rodapartes.app.ui.auth.LoginActivity
import com.rodapartes.app.util.BiometricHelper
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack

/** Perfil del comprador. */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: Session

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)
        session = Session(requireContext())

        binding.rowEdit.setOnClickListener { openEdit() }
        binding.rowMoto.setOnClickListener { openEdit() }
        binding.rowOrders.setOnClickListener { (requireActivity() as BuyerActivity).openTab(R.id.nav_orders) }
        binding.rowAddresses.setOnClickListener { view.snack(getString(R.string.profile_addresses_msg)) }
        binding.rowPrivacy.setOnClickListener { showPrivacy() }

        binding.swFingerprint.isChecked = session.biometricEnabled
        binding.swFingerprint.setOnCheckedChangeListener { sw, checked ->
            if (!sw.isPressed) return@setOnCheckedChangeListener
            if (checked) enableFingerprint() else session.biometricEnabled = false
        }

        binding.swPromos.isChecked = session.promos
        binding.swPromos.setOnCheckedChangeListener { _, checked -> session.promos = checked }

        binding.btnLogout.setOnClickListener { Router.logout(requireActivity()) }
        binding.btnDeleteAccount.setOnClickListener { confirmDelete() }
    }

    override fun onResume() {
        super.onResume()
        binding.tvName.text = session.name
        binding.tvEmail.text = session.email
        binding.tvMotoSub.text = MockData.myMoto
        val onTheWay = MockData.myOrders.count { it.status == OrderStatus.ENVIADO }
        binding.tvOrdersSub.text = getString(R.string.profile_orders_sub, MockData.myOrders.size, onTheWay)
    }

    private fun openEdit() = startActivity(Intent(requireContext(), EditProfileActivity::class.java))

    /** Activar la huella pide confirmar la contraseña (RF-PER-05). */
    private fun enableFingerprint() {
        if (!BiometricHelper.isAvailable(requireActivity())) {
            binding.swFingerprint.isChecked = false
            binding.root.snack(getString(R.string.bio_not_available))
            return
        }
        val dialogBinding = DialogConfirmPasswordBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bio_confirm_title)
            .setMessage(R.string.bio_confirm_msg)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                if (dialogBinding.etPassword.text.isNullOrBlank()) {
                    binding.swFingerprint.isChecked = false
                } else {
                    BiometricHelper.authenticate(requireActivity(),
                        onSuccess = {
                            session.biometricEnabled = true
                            binding.root.snack(getString(R.string.bio_enabled))
                        },
                        onError = {
                            binding.swFingerprint.isChecked = false
                            binding.root.snack(it)
                        })
                }
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> binding.swFingerprint.isChecked = false }
            .setCancelable(false)
            .show()
    }

    private fun showPrivacy() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_privacy)
            .setMessage(getString(R.string.privacy_detail,
                getString(if (session.promos) R.string.state_active else R.string.state_inactive)))
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    /** Eliminar cuenta con doble confirmación (RF-USU-04). */
    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_msg)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_account_title2)
                    .setMessage(R.string.delete_account_msg2)
                    .setPositiveButton(R.string.profile_delete_account) { _, _ ->
                        session.clearAll()
                        startActivity(Intent(requireContext(), LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
