package com.rodapartes.app.ui.seller

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.UserRepository
import com.rodapartes.app.data.UserStatus
import com.rodapartes.app.databinding.FragmentSellerProfileBinding
import com.rodapartes.app.util.LocationHelper
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.color
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.viewScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * "Mi tienda" (RF-PER-04): nombre comercial, dirección, horario, teléfono y ubicación del local.
 * La ubicación se captura con el GPS y se guarda en users/{uid} para mostrarla a los compradores.
 */
class SellerProfileFragment : Fragment(R.layout.fragment_seller_profile) {

    private var _binding: FragmentSellerProfileBinding? = null
    private val binding get() = _binding!!
    private var lat: Double? = null
    private var lng: Double? = null

    /** Pide el permiso de ubicación solo cuando se toca el botón (RNF-SEG-06). */
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) captureLocation()
            else binding.root.snack(getString(R.string.location_denied))
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSellerProfileBinding.bind(view)
        val user = Session.user ?: return

        binding.tvStoreName.text = user.displayName
        binding.tvNit.text = user.document.ifBlank { getString(R.string.store_no_nit) }
        // Etiqueta según la revisión del administrador (RF-ADU-04)
        val verified = user.statusEnum == UserStatus.ACTIVO
        binding.tvVerified.text = getString(if (verified) R.string.store_verified else R.string.store_in_review)
        binding.tvVerified.setTextColor(requireContext().color(if (verified) R.color.exito else R.color.aviso))
        binding.tvVerified.backgroundTintList = ColorStateList.valueOf(
            requireContext().color(if (verified) R.color.exito_claro else R.color.aviso_claro))

        binding.etStoreName.setText(user.displayName)
        binding.etAddress.setText(user.storeAddress)
        binding.etSchedule.setText(user.storeSchedule)
        binding.etStorePhone.setText(user.phone)
        lat = user.storeLat
        lng = user.storeLng
        showCoords()

        binding.btnStoreLocation.setOnClickListener {
            if (LocationHelper.hasPermission(requireContext())) captureLocation()
            else locationPermission.launch(LocationHelper.PERMISSIONS)
        }
        binding.tvStoreCoords.setOnClickListener {
            val la = lat
            val lo = lng
            if (la != null && lo != null) LocationHelper.openInMaps(requireContext(), la, lo, user.displayName)
        }

        binding.cardPayout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.store_payout)
                .setMessage(R.string.store_payout_msg)
                .setPositiveButton(R.string.action_understood, null)
                .show()
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnLogout.setOnClickListener { Router.logout(requireActivity()) }
    }

    /** Obtiene la ubicación actual del local y llena la dirección con el Geocoder. */
    private fun captureLocation() {
        binding.btnStoreLocation.isEnabled = false
        binding.btnStoreLocation.setText(R.string.checkout_locating)
        viewScope.launch {
            val place = runCatching { LocationHelper.current(requireContext()) }.getOrNull()
            if (place == null) {
                binding.root.snack(getString(R.string.location_unavailable))
            } else {
                lat = place.lat
                lng = place.lng
                if (binding.etAddress.text.isNullOrBlank()) binding.etAddress.setText(place.address)
                showCoords()
            }
            binding.btnStoreLocation.isEnabled = true
            binding.btnStoreLocation.setText(R.string.store_use_location)
        }
    }

    private fun showCoords() {
        val la = lat
        val lo = lng
        binding.tvStoreCoords.text = if (la != null && lo != null)
            getString(R.string.store_coords, String.format(Locale.US, "%.5f, %.5f", la, lo))
        else getString(R.string.store_no_location)
    }

    /** Guarda los datos de la tienda en Firestore. */
    private fun save() {
        val user = Session.user ?: return
        val name = binding.etStoreName.text.toString().trim()
        if (name.length < 3) {
            binding.tilStoreName.error = getString(R.string.error_required)
            return
        }
        binding.tilStoreName.error = null
        val fields = mapOf(
            "storeName" to name,
            "storeAddress" to binding.etAddress.text.toString().trim(),
            "storeSchedule" to binding.etSchedule.text.toString().trim(),
            "phone" to binding.etStorePhone.text.toString().trim(),
            "storeLat" to lat,
            "storeLng" to lng
        )
        binding.btnSave.isEnabled = false
        viewScope.safeLaunch(binding.root, onDone = { _binding?.btnSave?.isEnabled = true }) {
            UserRepository.update(user.uid, fields)
            user.storeName = name
            user.storeAddress = fields["storeAddress"] as String
            user.storeSchedule = fields["storeSchedule"] as String
            user.phone = fields["phone"] as String
            user.storeLat = lat
            user.storeLng = lng
            binding.tvStoreName.text = name
            binding.root.snack(getString(R.string.saved_ok))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
