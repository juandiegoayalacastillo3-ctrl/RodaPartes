package com.rodapartes.app.ui.buyer

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.BounceInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.rodapartes.app.R
import com.rodapartes.app.data.CartItem
import com.rodapartes.app.data.CartRepository
import com.rodapartes.app.data.Pricing
import com.rodapartes.app.databinding.ActivityCheckoutBinding
import com.rodapartes.app.util.LocationHelper
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Datos de la entrega elegidos en el checkout. Se guardan en memoria mientras el comprador
 * pasa a la pasarela de pagos y se copian a cada orden cuando el pago queda aprobado.
 */
object CheckoutState {
    var delivery: Boolean = true
    var address: String = ""
    var addressDetail: String = ""
    var lat: Double? = null
    var lng: Double? = null
}

/**
 * Entrega: dirección con geolocalización (RF-PAG-02), método de envío y resumen
 * de la compra antes de pagar (RF-PAG-01).
 */
class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private var cart: List<CartItem> = emptyList()
    private var delivery = true

    /** Pide el permiso de ubicación justo cuando el usuario toca el botón (RNF-SEG-06). */
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) useCurrentLocation()
            else binding.root.snack(getString(R.string.location_denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Limpia la ubicación de compras anteriores
        CheckoutState.lat = null
        CheckoutState.lng = null

        binding.tvMapAddress.text = getString(R.string.checkout_no_location)
        binding.etAddress.doAfterTextChanged {
            if (!it.isNullOrBlank()) binding.tvMapAddress.text = it
        }
        binding.btnMyLocation.setOnClickListener {
            if (LocationHelper.hasPermission(this)) useCurrentLocation()
            else locationPermission.launch(LocationHelper.PERMISSIONS)
        }
        // Tocar el mapa abre Google Maps en el punto capturado
        binding.ivPin.setOnClickListener {
            val lat = CheckoutState.lat
            val lng = CheckoutState.lng
            if (lat != null && lng != null) LocationHelper.openInMaps(this, lat, lng, getString(R.string.checkout_address))
        }

        binding.rgShipping.setOnCheckedChangeListener { _, checkedId ->
            delivery = checkedId != R.id.rbPickup
            binding.tilAddress.isEnabled = delivery
            binding.btnMyLocation.isEnabled = delivery
            updateSummary()
        }

        binding.btnGoPay.setOnClickListener { goToPayment() }
        binding.btnGoPay.isEnabled = false

        // Lee el carrito de Firestore para calcular el resumen
        lifecycleScope.launch {
            cart = runCatching { CartRepository.getAll(Session.user?.uid.orEmpty()) }.getOrDefault(emptyList())
            binding.btnGoPay.isEnabled = cart.isNotEmpty()
            updateSummary()
        }
    }

    /**
     * Captura la ubicación con Fused Location Provider y la convierte en dirección con Geocoder.
     * El comprador puede corregir la dirección a mano antes de pagar.
     */
    private fun useCurrentLocation() {
        binding.btnMyLocation.isEnabled = false
        binding.btnMyLocation.text = getString(R.string.checkout_locating)
        lifecycleScope.launch {
            val place = runCatching { LocationHelper.current(this@CheckoutActivity) }.getOrNull()
            if (place == null) {
                binding.root.snack(getString(R.string.location_unavailable))
            } else {
                CheckoutState.lat = place.lat
                CheckoutState.lng = place.lng
                binding.etAddress.setText(place.address)
                binding.tvMapAddress.text = getString(
                    R.string.checkout_coords, place.address,
                    String.format(Locale.US, "%.5f, %.5f", place.lat, place.lng)
                )
                ObjectAnimator.ofFloat(binding.ivPin, "translationY", -60f, 0f).apply {
                    duration = 700
                    interpolator = BounceInterpolator()
                    start()
                }
            }
            binding.btnMyLocation.isEnabled = true
            binding.btnMyLocation.text = getString(R.string.checkout_use_location)
        }
    }

    /** Valida la dirección, guarda los datos de entrega y abre la pasarela de pagos. */
    private fun goToPayment() {
        if (delivery && binding.etAddress.text.isNullOrBlank()) {
            binding.tilAddress.error = getString(R.string.error_required)
            return
        }
        binding.tilAddress.error = null
        CheckoutState.delivery = delivery
        CheckoutState.address = binding.etAddress.text.toString().trim()
        CheckoutState.addressDetail = binding.etDetail.text.toString().trim()
        startActivity(Intent(this, PaymentActivity::class.java))
    }

    /** Resumen: vendedores, cantidad de productos, tiempo estimado y totales. */
    private fun updateSummary() {
        val sellers = cart.map { it.sellerName }.distinct()
        binding.tvSellerLegal.text = sellers.joinToString("\n") { getString(R.string.checkout_seller_legal_simple, it) }
        val count = cart.sumOf { it.qty }.toInt()
        binding.tvItems.text = resources.getQuantityString(R.plurals.products_count, count, count)
        binding.tvEta.text = getString(if (delivery) R.string.checkout_eta_delivery else R.string.checkout_eta_pickup)
        val shipping = Pricing.shipping(cart, delivery)
        binding.tvSubtotal.text = Money.format(Pricing.subtotal(cart))
        binding.tvShipping.text = if (shipping == 0L) getString(R.string.free) else Money.format(shipping)
        binding.tvTotal.text = Money.format(Pricing.total(cart, delivery))
    }
}
