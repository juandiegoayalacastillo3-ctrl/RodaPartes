package com.rodapartes.app.ui.buyer

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.BounceInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.databinding.ActivityCheckoutBinding
import com.rodapartes.app.util.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Entrega: dirección con mapa, método de envío y resumen antes de pagar. */
class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private var shipping = MockData.SHIPPING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.tvMapAddress.text = binding.etAddress.text
        binding.etAddress.doAfterTextChanged { binding.tvMapAddress.text = it }

        binding.btnMyLocation.setOnClickListener { useCurrentLocation() }

        binding.rgShipping.setOnCheckedChangeListener { _, checkedId ->
            shipping = if (checkedId == R.id.rbPickup) 0 else MockData.SHIPPING
            updateSummary()
        }

        binding.btnGoPay.setOnClickListener {
            if (binding.etAddress.text.isNullOrBlank()) {
                binding.tilAddress.error = getString(R.string.error_required)
                return@setOnClickListener
            }
            binding.tilAddress.error = null
            startActivity(Intent(this, PaymentActivity::class.java)
                .putExtra(PaymentActivity.EXTRA_TOTAL, MockData.cartSubtotal() + shipping)
                .putExtra(PaymentActivity.EXTRA_ADDRESS, binding.etAddress.text.toString()))
        }
        updateSummary()
    }

    /**
     * Demo de "usar mi ubicación actual". En la fase de backend se reemplaza por
     * Fused Location Provider + Places API, pidiendo el permiso en contexto (RNF-SEG-06).
     */
    private fun useCurrentLocation() {
        binding.btnMyLocation.isEnabled = false
        binding.btnMyLocation.text = getString(R.string.checkout_locating)
        lifecycleScope.launch {
            delay(900)
            binding.etAddress.setText(R.string.address_current)
            ObjectAnimator.ofFloat(binding.ivPin, "translationY", -60f, 0f).apply {
                duration = 700
                interpolator = BounceInterpolator()
                start()
            }
            binding.btnMyLocation.isEnabled = true
            binding.btnMyLocation.text = getString(R.string.checkout_use_location)
        }
    }

    private fun updateSummary() {
        val sellers = MockData.cart.map { it.product.seller }.distinct()
        binding.tvSellerLegal.text = sellers.joinToString("\n") {
            getString(R.string.checkout_seller_legal, it, ProductDetailActivity.sellerNit(it))
        }
        binding.tvItems.text = resources.getQuantityString(R.plurals.products_count, MockData.cartCount(), MockData.cartCount())
        binding.tvEta.text = getString(if (shipping == 0) R.string.checkout_eta_pickup else R.string.checkout_eta_delivery)
        binding.tvSubtotal.text = Money.format(MockData.cartSubtotal())
        binding.tvShipping.text = if (shipping == 0) getString(R.string.free) else Money.format(shipping)
        binding.tvTotal.text = Money.format(MockData.cartSubtotal() + shipping)
    }
}
