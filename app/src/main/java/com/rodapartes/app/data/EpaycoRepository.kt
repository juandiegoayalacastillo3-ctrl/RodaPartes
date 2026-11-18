package com.rodapartes.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Resultado de la consulta de una transacción en ePayco. */
data class EpaycoResult(
    val refPayco: String,
    val code: Int,          // 1 Aceptada · 2 Rechazada · 3 Pendiente · 4 Fallida
    val response: String,   // texto que devuelve ePayco ("Aceptada", "Rechazada"...)
    val amount: Long,
    val invoice: String,
    val method: String      // franquicia o medio: VS, MC, PSE, NEQUI...
) {
    val accepted get() = code == 1
    val pending get() = code == 3
}

/**
 * Confirmación de pagos con ePayco (RF-PAG-04).
 *
 * Después de pagar, ePayco redirige a la URL de respuesta con ?ref_payco=xxxx. Con esa
 * referencia se consulta el servicio público de validación de ePayco para saber si la
 * transacción quedó aceptada, y se verifica que el valor y la factura coincidan con la compra.
 */
object EpaycoRepository {

    /** URL a la que ePayco redirige al terminar; la app la intercepta, no se carga nunca. */
    const val RESPONSE_URL = "https://rodapartes.app/pago/respuesta"

    private const val VALIDATION_URL = "https://secure.epayco.co/validation/v1/reference/"

    /** Consulta el estado de la transacción [refPayco] en el servicio de ePayco. */
    suspend fun validate(refPayco: String): EpaycoResult = withContext(Dispatchers.IO) {
        val conn = URL(VALIDATION_URL + refPayco).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (!json.optBoolean("success")) throw AppException("ePayco no encontró la transacción")
            val data = json.getJSONObject("data")
            EpaycoResult(
                refPayco = refPayco,
                code = data.optInt("x_cod_response", data.optInt("x_cod_transaction_state", 4)),
                response = data.optString("x_response", "Sin respuesta"),
                amount = data.optString("x_amount", "0").toDoubleOrNull()?.toLong() ?: 0L,
                invoice = data.optString("x_id_invoice"),
                method = data.optString("x_franchise", "ePayco")
            )
        } finally {
            conn.disconnect()
        }
    }
}
