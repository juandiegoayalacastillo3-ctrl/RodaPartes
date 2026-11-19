package com.rodapartes.app.ui.buyer

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.AuthRepository
import com.rodapartes.app.data.CartItem
import com.rodapartes.app.data.CartRepository
import com.rodapartes.app.data.OrderRepository
import com.rodapartes.app.data.Pricing
import com.rodapartes.app.data.StripeRepository
import com.rodapartes.app.data.StripeResult
import com.rodapartes.app.databinding.ActivityPaymentBinding
import com.rodapartes.app.databinding.DialogPaymentSuccessBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible
import kotlinx.coroutines.launch

/**
 * Pasarela de pagos con Stripe en modo de pruebas (RF-PAG-03 y RF-PAG-04).
 *
 * 1. Muestra el total y genera un número de factura único.
 * 2. Crea una sesión de Stripe Checkout y abre su página de pago en un WebView.
 * 3. Cuando Stripe redirige a la URL de respuesta, toma el id de la sesión y la valida.
 * 4. Si quedó pagada y el valor y la factura coinciden, crea las órdenes en Firestore
 *    y vacía el carrito.
 */
class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private var cart: List<CartItem> = emptyList()
    private var total = 0L
    private val invoice = "RP-" + System.currentTimeMillis().toString(36).uppercase()
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.tvInvoice.text = getString(R.string.payment_invoice, invoice)
        binding.tvTestNote.visible(StripeRepository.isTestMode)
        binding.btnPay.isEnabled = false

        // Atrás: si el checkout está abierto, vuelve al resumen en lugar de salir
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.visibility == android.view.View.VISIBLE) showCheckout(false)
                else finish()
            }
        })

        lifecycleScope.launch {
            cart = runCatching { CartRepository.getAll(Session.user?.uid.orEmpty()) }.getOrDefault(emptyList())
            total = Pricing.total(cart, CheckoutState.delivery)
            val count = cart.sumOf { it.qty }.toInt()
            binding.tvTotalBig.text = Money.format(total)
            binding.tvItems.text = getString(
                R.string.payment_items_line,
                resources.getQuantityString(R.plurals.products_count, count, count),
                if (CheckoutState.delivery) CheckoutState.address else getString(R.string.checkout_pickup_short)
            )
            binding.btnPay.text = getString(R.string.payment_pay_stripe, Money.format(total))
            binding.btnPay.isEnabled = cart.isNotEmpty()
        }

        setupWebView()
        binding.btnPay.setOnClickListener { openCheckout() }
    }

    /** Configura el WebView: JavaScript para el checkout de Stripe e intercepción de la respuesta. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() = with(binding.webView) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                interceptResponse(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                binding.progress.visible(false)
            }
        }
    }

    /** Crea la sesión de pago en Stripe con el valor y la factura, y abre su página de pago. */
    private fun openCheckout() {
        val user = Session.user ?: return
        if (!StripeRepository.isConfigured) {
            binding.root.snack(getString(R.string.payment_load_error, "falta la llave de Stripe")); return
        }
        binding.btnPay.isEnabled = false
        binding.progress.visible(true)
        lifecycleScope.launch {
            try {
                val description = getString(R.string.payment_description, cart.sumOf { it.qty }.toInt())
                val session = StripeRepository.createSession(invoice, total, user.email, description)
                showCheckout(true)
                binding.webView.loadUrl(session.url)
            } catch (e: Exception) {
                binding.progress.visible(false)
                binding.root.snack(getString(R.string.payment_load_error, e.message ?: ""))
            } finally {
                binding.btnPay.isEnabled = true
            }
        }
    }

    /** Si la URL es la de respuesta o la de cancelar, valida el pago o vuelve al resumen. */
    private fun interceptResponse(url: Uri): Boolean {
        val text = url.toString()
        if (text.startsWith(StripeRepository.CANCEL_URL)) {
            showCheckout(false)
            binding.root.snack(getString(R.string.payment_cancelled))
            return true
        }
        if (!text.startsWith(StripeRepository.SUCCESS_URL)) return false
        val id = url.getQueryParameter("session_id")
        if (id.isNullOrBlank()) {
            showCheckout(false)
            binding.root.snack(getString(R.string.payment_cancelled))
        } else {
            confirm(id)
        }
        return true
    }

    /**
     * Valida la sesión en Stripe. Solo si quedó pagada, con el mismo valor y la misma factura,
     * se registran las órdenes (evita que una respuesta alterada cree compras).
     */
    private fun confirm(sessionId: String) {
        if (handled) return
        handled = true
        showCheckout(false)
        binding.progress.visible(true)
        binding.btnPay.isEnabled = false
        binding.btnPay.setText(R.string.payment_validating)
        lifecycleScope.launch {
            try {
                val result = StripeRepository.validate(sessionId)
                when {
                    result.accepted && result.amount == total * 100 && result.reference == invoice -> saveOrders(result)
                    result.pending -> showResult(R.string.payment_pending_title, getString(R.string.payment_pending_msg, sessionId))
                    else -> showResult(R.string.payment_rejected_title, getString(R.string.payment_rejected_msg, result.paymentStatus, sessionId))
                }
            } catch (e: Exception) {
                handled = false
                binding.btnPay.isEnabled = true
                binding.btnPay.text = getString(R.string.payment_pay_stripe, Money.format(total))
                binding.root.snack(AuthRepository.friendlyMessage(e))
            } finally {
                binding.progress.visible(false)
            }
        }
    }

    /** Crea las órdenes en Firestore (una por tienda), descuenta stock y vacía el carrito. */
    private suspend fun saveOrders(result: StripeResult) {
        val user = Session.user ?: return
        val ref = result.paymentIntent.ifBlank { result.sessionId }
        val codes = OrderRepository.createFromCart(
            buyer = user,
            cart = cart,
            shippingPerOrder = Pricing.SHIPPING_PER_STORE,
            delivery = CheckoutState.delivery,
            address = CheckoutState.address,
            addressDetail = CheckoutState.addressDetail,
            lat = CheckoutState.lat,
            lng = CheckoutState.lng,
            paymentRef = ref,
            paymentMethod = "Stripe (tarjeta)"
        )
        val dialog = DialogPaymentSuccessBinding.inflate(layoutInflater)
        dialog.tvOrderCode.text = getString(R.string.payment_ok_codes, codes.joinToString(), ref)
        MaterialAlertDialogBuilder(this)
            .setView(dialog.root)
            .setCancelable(false)
            .setPositiveButton(R.string.payment_see_orders) { _, _ ->
                startActivity(BuyerActivity.intentForTab(this, R.id.nav_orders))
                finish()
            }
            .show()
    }

    /** Muestra el resultado (pendiente o rechazado) y cierra al aceptar. */
    private fun showResult(title: Int, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.action_understood) { _, _ -> finish() }
            .show()
    }

    /** Alterna entre el resumen de la compra y la página de pago de Stripe. */
    private fun showCheckout(show: Boolean) {
        binding.webView.visible(show)
        binding.groupSummary.visible(!show)
        binding.btnPay.visible(!show)
    }
}
