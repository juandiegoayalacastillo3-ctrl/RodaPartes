package com.rodapartes.app.ui.seller

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.databinding.FragmentSellerProfileBinding
import com.rodapartes.app.ui.buyer.ProductDetailActivity
import com.rodapartes.app.util.Router
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack

/** "Mi tienda": datos del establecimiento del vendedor (RF-PER-04). */
class SellerProfileFragment : Fragment(R.layout.fragment_seller_profile) {

    private var _binding: FragmentSellerProfileBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSellerProfileBinding.bind(view)
        val session = Session(requireContext())

        binding.tvStoreName.text = session.name
        binding.tvNit.text = ProductDetailActivity.sellerNit(session.name)
        binding.etStoreName.setText(session.name)
        binding.etAddress.setText(R.string.store_address_default)
        binding.etSchedule.setText(R.string.store_schedule_default)
        binding.etStorePhone.setText(R.string.store_phone_default)

        binding.cardPayout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.store_payout)
                .setMessage(R.string.store_payout_msg)
                .setPositiveButton(R.string.action_understood, null)
                .show()
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etStoreName.text.toString().trim()
            if (name.length < 3) {
                binding.tilStoreName.error = getString(R.string.error_required)
                return@setOnClickListener
            }
            binding.tilStoreName.error = null
            session.name = name
            binding.tvStoreName.text = name
            view.snack(getString(R.string.saved_ok))
        }
        binding.btnLogout.setOnClickListener { Router.logout(requireActivity()) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
