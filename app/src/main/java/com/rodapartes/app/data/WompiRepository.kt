package com.rodapartes.app.data

import android.net.Uri
import com.rodapartes.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Resultado de consultar una transacción en Wompi. */
data class WompiResult(
    val transactionId: String,
    val status: String,      // APPROVED · DECLINED · VOIDED · ERROR · PENDING
    val amountInCents: Long,
    val reference: String,
    val method: String       // CARD, PSE, NEQUI, BANCOLOMBIA_TRANSFER...
) {
    val accepted get() = status == "APPROVED"
    val pending get() = status == "PENDING"
}

/**
 * Pasarela de pagos Wompi (RF-PAG-03 y RF-PAG-04).
 *
 * 1. [checkoutUrl] arma la URL del Web Checkout oficial de Wompi con el valor, la referencia
 *    y la firma de integridad (SHA-256), para que nadie pueda cambiar el valor a pagar.
 * 2. Al terminar, Wompi redirige a [REDIRECT_URL]?id=ID_TRANSACCION y la app intercepta esa URL.
 * 3. [validate] consulta la transacción en el API público de Wompi para confirmar el estado.
 *
 * Los datos de la tarjeta los recibe Wompi directamente: la app nunca los ve.
 */
object WompiRepository {

    /** URL a la que Wompi redirige al terminar; la app la intercepta, no se carga nunca. */
    const val REDIRECT_URL = "https://rodapartes.app/pago/respuesta"

    private const val CHECKOUT_URL = "https://checkout.wompi.co/p/"

    /** Las llaves de pruebas empiezan por pub_test_ y usan el ambiente sandbox. */
    val isSandbox get() = BuildConfig.WOMPI_PUBLIC_KEY.startsWith("pub_test_")

    private val apiUrl get() =
        if (isSandbox) "https://sandbox.wompi.co/v1" else "https://production.wompi.co/v1"

    /** URL del checkout de Wompi para cobrar [amount] pesos con la referencia [reference]. */
    fun checkoutUrl(reference: String, amount: Long, email: String, name: String, phone: String): String {
        val cents = amount * 100
        return Uri.parse(CHECKOUT_URL).buildUpon()
            .appendQueryParameter("public-key", BuildConfig.WOMPI_PUBLIC_KEY)
            .appendQueryParameter("currency", "COP")
            .appendQueryParameter("amount-in-cents", cents.toString())
            .appendQueryParameter("reference", reference)
            .appendQueryParameter("signature:integrity", integrity(reference, cents))
            .appendQueryParameter("redirect-url", REDIRECT_URL)
            .appendQueryParameter("customer-data:email", email)
            .appendQueryParameter("customer-data:full-name", name)
            .apply {
                if (phone.isNotBlank()) {
                    appendQueryParameter("customer-data:phone-number", phone.filter { it.isDigit() })
                    appendQueryParameter("customer-data:phone-number-prefix", "+57")
                }
            }
            .build().toString()
    }

    /** Firma de integridad: SHA-256 de referencia + valor en centavos + moneda + secreto. */
    private fun integrity(reference: String, cents: Long): String {
        val text = reference + cents + "COP" + BuildConfig.WOMPI_INTEGRITY
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** Consulta en Wompi el estado de la transacción [id]. */
    suspend fun validate(id: String): WompiResult = withContext(Dispatchers.IO) {
        val conn = URL("$apiUrl/transactions/$id").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) throw AppException("Wompi no encontró la transacción")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).getJSONObject("data")
            WompiResult(
                transactionId = data.optString("id", id),
                status = data.optString("status", "ERROR"),
                amountInCents = data.optLong("amount_in_cents", 0L),
                reference = data.optString("reference"),
                method = data.optString("payment_method_type", "Wompi")
            )
        } finally {
            conn.disconnect()
        }
    }
}
