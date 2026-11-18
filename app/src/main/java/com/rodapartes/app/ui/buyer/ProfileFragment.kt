package com.rodapartes.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.R
import com.rodapartes.app.data.Fire
import com.rodapartes.app.data.OrderRepository
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.data.UserRepository
import com.rodapartes.app.databinding.FragmentProfileBinding
import com.rodapartes.app.util.BiometricSetup
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.SecureStore
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.viewScope
import kotlinx.coroutines.tasks.await

/** Perfil del comprador: datos, compras, huella, promociones, privacidad y cierre de sesión. */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var secure: SecureStore
    private var listener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)
        secure = SecureStore(requireContext())

        binding.rowEdit.setOnClickListener { openEdit() }
        binding.rowMoto.setOnClickListener { openEdit() }
        binding.rowAddresses.setOnClickListener { view.snack(getString(R.string.profile_addresses_msg)) }
        binding.rowOrders.setOnClickListener { (requireActivity() as BuyerActivity).openTab(R.id.nav_orders) }
        binding.rowPrivacy.setOnClickListener { showPrivacy() }

        // Ingreso con huella: activar pide la contraseña y la huella (RF-PER-05)
        binding.swFingerprint.isChecked = secure.biometricEnabled
        binding.swFingerprint.setOnCheckedChangeListener { sw, checked ->
            if (!sw.isPressed) return@setOnCheckedChangeListener
            if (checked) {
                BiometricSetup.enableWithPassword(this) { ok -> _binding?.swFingerprint?.isChecked = ok }
            } else {
                secure.disableBiometric()
            }
        }

        // Promociones: consentimiento aparte guardado en Firestore (Ley 2300 de 2023)
        binding.swPromos.isChecked = Session.user?.promos == true
        binding.swPromos.setOnCheckedChangeListener { sw, checked ->
            if (!sw.isPressed) return@setOnCheckedChangeListener
            val user = Session.user ?: return@setOnCheckedChangeListener
            viewScope.safeLaunch(binding.root) {
                UserRepository.update(user.uid, mapOf("promos" to checked))
                user.promos = checked
            }
        }

        binding.btnLogout.setOnClickListener { Router.logout(requireActivity()) }
        binding.btnDeleteAccount.setOnClickListener { confirmDelete() }

        // Resumen de compras en tiempo real
        listener = OrderRepository.listenByBuyer(Session.user?.uid.orEmpty()) { orders ->
            val onTheWay = orders.count { it.statusEnum == OrderStatus.ENVIADO }
            _binding?.tvOrdersSub?.text = getString(R.string.profile_orders_sub, orders.size, onTheWay)
        }
    }

    override fun onResume() {
        super.onResume()
        val user = Session.user ?: return
        binding.tvName.text = user.name
        binding.tvEmail.text = user.email
        binding.tvMotoSub.text = user.moto.ifBlank { getString(R.string.home_choose_moto) }
        if (user.photoUrl.isNotBlank()) {
            binding.ivAvatar.imageTintList = null
            binding.ivAvatar.setPadding(0, 0, 0, 0)
            binding.ivAvatar.load(user.photoUrl)
        }
    }

    private fun openEdit() = startActivity(Intent(requireContext(), EditProfileActivity::class.java))

    private fun showPrivacy() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_privacy)
            .setMessage(getString(R.string.privacy_detail,
                getString(if (Session.user?.promos == true) R.string.state_active else R.string.state_inactive)))
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    /**
     * Eliminar la cuenta (RF-USU-04): pide la contraseña, anonimiza el perfil (borrado lógico)
     * y borra el usuario de Firebase Auth.
     */
    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_msg)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                BiometricSetup.askPassword(this, R.string.delete_account_title2) { password ->
                    val user = Session.user ?: return@askPassword
                    viewScope.safeLaunch(binding.root) {
                        com.rodapartes.app.data.AuthRepository.reauthenticate(password)
                        UserRepository.adminDelete(user.uid)
                        secure.disableBiometric()
                        Fire.auth.currentUser?.delete()?.await()
                        Router.logout(requireActivity())
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }
}
