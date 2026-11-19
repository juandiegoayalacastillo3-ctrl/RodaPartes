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
import com.rodapartes.app.BuildConfig
import com.rodapartes.app.R
import com.rodapartes.app.data.AuthRepository
import com.rodapartes.app.data.CartItem
import com.rodapartes.app.data.CartRepository
import com.rodapartes.app.data.OrderRepository
import com.rodapartes.app.data.Pricing
import com.rodapartes.app.data.WompiRepository
import com.rodapartes.app.data.WompiResult
import com.rodapartes.app.databinding.ActivityPaymentBinding
import com.rodapartes.app.databinding.DialogPaymentSuccessBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible
import kotlinx.coroutines.launch

/**
 * Pasarela de pagos con Wompi (RF-PAG-03 y RF-PAG-04).
 *
 * 1. Muestra el total y genera una referencia de pago única.
 * 2. Abre el Web Checkout oficial de Wompi en un WebView (tarjeta, PSE, Nequi, Bancolombia...).
 * 3. Cuando Wompi redirige a la URL de respuesta, toma el id de la transacción y la valida.
 * 4. Si quedó aprobada y el valor y la referencia coinciden, crea las órdenes en Firestore
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
        binding.tvTestNote.visible(WompiRepository.isSandbox)
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
            binding.btnPay.text = getString(R.string.payment_pay_wompi, Money.format(total))
            binding.btnPay.isEnabled = cart.isNotEmpty()
        }

        setupWebView()
        binding.btnPay.setOnClickListener { openCheckout() }
    }

    /** Configura el WebView: JavaScript para el checkout de Wompi e intercepción de la respuesta. */
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

    /** Abre el checkout de Wompi con el valor, la referencia y los datos del comprador. */
    private fun openCheckout() {
        val user = Session.user ?: return
        if (BuildConfig.WOMPI_PUBLIC_KEY.isBlank()) {
            binding.root.snack(getString(R.string.payment_load_error, "falta la llave pública")); return
        }
        showCheckout(true)
        binding.progress.visible(true)
        binding.webView.loadUrl(
            WompiRepository.checkoutUrl(invoice, total, user.email, user.name, user.phone)
        )
    }

    /** Si la URL es la de respuesta, toma el id de la transacción y valida el pago. */
    private fun interceptResponse(url: Uri): Boolean {
        if (!url.toString().startsWith(WompiRepository.REDIRECT_URL)) return false
        val id = url.getQueryParameter("id")
        if (id.isNullOrBlank()) {
            showCheckout(false)
            binding.root.snack(getString(R.string.payment_cancelled))
        } else {
            confirm(id)
        }
        return true
    }

    /**
     * Valida la transacción en Wompi. Solo con estado APPROVED, el mismo valor y la misma
     * referencia se registran las órdenes (evita que una respuesta alterada cree compras).
     */
    private fun confirm(transactionId: String) {
        if (handled) return
        handled = true
        showCheckout(false)
        binding.progress.visible(true)
        binding.btnPay.isEnabled = false
        binding.btnPay.setText(R.string.payment_validating)
        lifecycleScope.launch {
            try {
                val result = WompiRepository.validate(transactionId)
                when {
                    result.accepted && result.amountInCents == total * 100 && result.reference == invoice -> saveOrders(result)
                    result.pending -> showResult(R.string.payment_pending_title, getString(R.string.payment_pending_msg, transactionId))
                    else -> showResult(R.string.payment_rejected_title, getString(R.string.payment_rejected_msg, result.status, transactionId))
                }
            } catch (e: Exception) {
                handled = false
                binding.btnPay.isEnabled = true
                binding.btnPay.text = getString(R.string.payment_pay_wompi, Money.format(total))
                binding.root.snack(AuthRepository.friendlyMessage(e))
            } finally {
                binding.progress.visible(false)
            }
        }
    }

    /** Crea las órdenes en Firestore (una por tienda), descuenta stock y vacía el carrito. */
    private suspend fun saveOrders(result: WompiResult) {
        val user = Session.user ?: return
        val codes = OrderRepository.createFromCart(
            buyer = user,
            cart = cart,
            shippingPerOrder = Pricing.SHIPPING_PER_STORE,
            delivery = CheckoutState.delivery,
            address = CheckoutState.address,
            addressDetail = CheckoutState.addressDetail,
            lat = CheckoutState.lat,
            lng = CheckoutState.lng,
            paymentRef = result.transactionId,
            paymentMethod = result.method
        )
        val dialog = DialogPaymentSuccessBinding.inflate(layoutInflater)
        dialog.tvOrderCode.text = getString(R.string.payment_ok_codes, codes.joinToString(), result.transactionId)
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

    /** Alterna entre el resumen de la compra y el checkout de Wompi. */
    private fun showCheckout(show: Boolean) {
        binding.webView.visible(show)
        binding.groupSummary.visible(!show)
        binding.btnPay.visible(!show)
    }
}
