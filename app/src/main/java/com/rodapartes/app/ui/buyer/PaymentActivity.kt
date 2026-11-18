package com.rodapartes.app.ui.buyer

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
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
import com.rodapartes.app.data.EpaycoRepository
import com.rodapartes.app.data.EpaycoResult
import com.rodapartes.app.data.OrderRepository
import com.rodapartes.app.data.Pricing
import com.rodapartes.app.databinding.ActivityPaymentBinding
import com.rodapartes.app.databinding.DialogPaymentSuccessBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible
import kotlinx.coroutines.launch

/**
 * Pasarela de pagos con ePayco (RF-PAG-03 y RF-PAG-04).
 *
 * 1. Muestra el total y genera un número de factura único.
 * 2. Abre el checkout oficial de ePayco en un WebView (tarjeta, PSE, Nequi, Daviplata...).
 * 3. Cuando ePayco redirige a la URL de respuesta, toma ref_payco y valida la transacción.
 * 4. Si quedó aceptada y el valor coincide, crea las órdenes en Firestore y vacía el carrito.
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
        binding.tvTestNote.visible(BuildConfig.EPAYCO_TEST)
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
            binding.btnPay.text = getString(R.string.payment_pay_epayco, Money.format(total))
            binding.btnPay.isEnabled = cart.isNotEmpty()
        }

        setupWebView()
        binding.btnPay.setOnClickListener { openCheckout() }
    }

    /** Configura el WebView: JavaScript para el script de ePayco e intercepción de la respuesta. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() = with(binding.webView) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        webChromeClient = WebChromeClient()
        addJavascriptInterface(Bridge(), "RodaPartes")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                interceptResponse(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                binding.progress.visible(false)
            }
        }
    }

    /** Arma el HTML del checkout con los datos de la compra y lo carga en el WebView. */
    private fun openCheckout() {
        val user = Session.user ?: return
        val html = assets.open("epayco_checkout.html").bufferedReader().use { it.readText() }
            .replace("{{PUBLIC_KEY}}", BuildConfig.EPAYCO_PUBLIC_KEY)
            .replace("{{TEST}}", BuildConfig.EPAYCO_TEST.toString())
            .replace("{{DESCRIPTION}}", js(getString(R.string.payment_description, cart.sumOf { it.qty })))
            .replace("{{INVOICE}}", invoice)
            .replace("{{AMOUNT}}", total.toString())
            .replace("{{RESPONSE_URL}}", EpaycoRepository.RESPONSE_URL)
            .replace("{{NAME}}", js(user.name))
            .replace("{{EMAIL}}", js(user.email))
            .replace("{{PHONE}}", js(user.phone))
            .replace("{{ADDRESS}}", js(CheckoutState.address))
        showCheckout(true)
        binding.progress.visible(true)
        binding.webView.loadDataWithBaseURL("https://rodapartes.app/", html, "text/html", "utf-8", null)
    }

    /** Si la URL es la de respuesta, toma ref_payco y valida el pago. */
    private fun interceptResponse(url: Uri): Boolean {
        if (!url.toString().startsWith(EpaycoRepository.RESPONSE_URL)) return false
        val ref = url.getQueryParameter("ref_payco") ?: url.getQueryParameter("x_ref_payco")
        if (ref.isNullOrBlank()) {
            showCheckout(false)
            binding.root.snack(getString(R.string.payment_cancelled))
        } else {
            confirm(ref)
        }
        return true
    }

    /**
     * Valida la transacción en ePayco. Solo con estado "Aceptada", el mismo valor y la misma
     * factura se registran las órdenes (evita que una respuesta alterada cree compras).
     */
    private fun confirm(refPayco: String) {
        if (handled) return
        handled = true
        showCheckout(false)
        binding.progress.visible(true)
        binding.btnPay.isEnabled = false
        binding.btnPay.setText(R.string.payment_validating)
        lifecycleScope.launch {
            try {
                val result = EpaycoRepository.validate(refPayco)
                when {
                    result.accepted && result.amount == total && result.invoice == invoice -> saveOrders(result)
                    result.pending -> showResult(R.string.payment_pending_title, getString(R.string.payment_pending_msg, refPayco))
                    else -> showResult(R.string.payment_rejected_title, getString(R.string.payment_rejected_msg, result.response, refPayco))
                }
            } catch (e: Exception) {
                handled = false
                binding.btnPay.isEnabled = true
                binding.btnPay.text = getString(R.string.payment_pay_epayco, Money.format(total))
                binding.root.snack(AuthRepository.friendlyMessage(e))
            } finally {
                binding.progress.visible(false)
            }
        }
    }

    /** Crea las órdenes en Firestore (una por tienda), descuenta stock y vacía el carrito. */
    private suspend fun saveOrders(result: EpaycoResult) {
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
            paymentRef = result.refPayco,
            paymentMethod = result.method
        )
        val dialog = DialogPaymentSuccessBinding.inflate(layoutInflater)
        dialog.tvOrderCode.text = getString(R.string.payment_ok_codes, codes.joinToString(), result.refPayco)
        MaterialAlertDialogBuilder(this)
            .setView(dialog.root)
            .setCancelable(false)
            .setPositiveButton(R.string.payment_see_orders) { _, _ ->
                startActivity(BuyerActivity.intentForTab(this, R.id.nav_orders))
                finish()
            }
            .show()
    }

    private fun showResult(title: Int, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.action_understood) { _, _ -> finish() }
            .show()
    }

    private fun showCheckout(show: Boolean) {
        binding.webView.visible(show)
        binding.groupSummary.visible(!show)
        binding.btnPay.visible(!show)
    }

    /** Escapa comillas y saltos para insertar texto dentro del JavaScript. */
    private fun js(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    /** Puente para que la página avise si el script de ePayco no cargó. */
    private inner class Bridge {
        @JavascriptInterface
        fun onError(message: String) = runOnUiThread {
            showCheckout(false)
            binding.root.snack(getString(R.string.payment_load_error, message))
        }
    }
}
