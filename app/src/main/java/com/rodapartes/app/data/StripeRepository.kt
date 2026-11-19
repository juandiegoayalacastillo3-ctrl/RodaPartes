package com.rodapartes.app.data

import com.rodapartes.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Sesión de pago creada en Stripe: [url] es la página de pago que se abre en el WebView. */
data class StripeSession(val id: String, val url: String)

/** Resultado de consultar una sesión de pago en Stripe. */
data class StripeResult(
    val sessionId: String,
    val paymentStatus: String,   // paid · unpaid · no_payment_required
    val status: String,          // complete · open · expired
    val amount: Long,            // en centavos de peso
    val reference: String,       // número de factura de RodaPartes (client_reference_id)
    val paymentIntent: String
) {
    val accepted get() = paymentStatus == "paid" && status == "complete"
    val pending get() = status == "open"
}

/**
 * Pasarela de pagos Stripe en modo de pruebas (RF-PAG-03 y RF-PAG-04).
 *
 * 1. [createSession] crea una sesión de Stripe Checkout con el valor de la compra en pesos (COP)
 *    y la referencia de la factura. Stripe devuelve la URL de su página de pago.
 * 2. El comprador paga en esa página; al terminar, Stripe redirige a [SUCCESS_URL] con el id de
 *    la sesión y la app intercepta esa URL.
 * 3. [validate] consulta la sesión en el API de Stripe para confirmar que quedó pagada.
 *
 * Los datos de la tarjeta los recibe Stripe directamente: la app nunca los ve. La llave es de
 * pruebas y restringida (solo puede crear y leer sesiones de Checkout).
 */
object StripeRepository {

    /** URLs a las que Stripe redirige al terminar; la app las intercepta, no se cargan nunca. */
    const val SUCCESS_URL = "https://rodapartes.app/pago/respuesta"
    const val CANCEL_URL = "https://rodapartes.app/pago/cancelado"

    private const val API = "https://api.stripe.com/v1/checkout/sessions"

    /** Las llaves de pruebas empiezan por sk_test_ o rk_test_: no se cobra dinero real. */
    val isTestMode get() = BuildConfig.STRIPE_KEY.contains("_test_")

    val isConfigured get() = BuildConfig.STRIPE_KEY.isNotBlank()

    /** Crea la sesión de pago por [amount] pesos con la referencia [reference]. */
    suspend fun createSession(reference: String, amount: Long, email: String, description: String): StripeSession =
        withContext(Dispatchers.IO) {
            val form = listOf(
                "mode" to "payment",
                "locale" to "es",
                "payment_method_types[0]" to "card",
                "client_reference_id" to reference,
                "customer_email" to email,
                "success_url" to "$SUCCESS_URL?session_id={CHECKOUT_SESSION_ID}",
                "cancel_url" to CANCEL_URL,
                "line_items[0][quantity]" to "1",
                "line_items[0][price_data][currency]" to "cop",
                // COP en Stripe usa centavos: $ 157.900 = 15790000
                "line_items[0][price_data][unit_amount]" to (amount * 100).toString(),
                "line_items[0][price_data][product_data][name]" to description,
                "line_items[0][price_data][product_data][description]" to "Factura $reference",
                "metadata[reference]" to reference
            ).joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
            val json = request("POST", API, form)
            StripeSession(json.getString("id"), json.getString("url"))
        }

    /** Consulta en Stripe el estado de la sesión [sessionId]. */
    suspend fun validate(sessionId: String): StripeResult = withContext(Dispatchers.IO) {
        val json = request("GET", "$API/${enc(sessionId)}", null)
        StripeResult(
            sessionId = json.optString("id", sessionId),
            paymentStatus = json.optString("payment_status"),
            status = json.optString("status"),
            amount = json.optLong("amount_total", 0L),
            reference = json.optString("client_reference_id"),
            paymentIntent = json.optString("payment_intent")
        )
    }

    /** Petición HTTPS al API de Stripe con la llave en el encabezado Authorization. */
    private fun request(method: String, url: String, body: String?): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.STRIPE_KEY}")
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val ok = conn.responseCode in 200..299
            val text = (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw AppException("Stripe: " + (msg ?: "error ${conn.responseCode}"))
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    /** Codifica un texto para enviarlo en el formulario. */
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
