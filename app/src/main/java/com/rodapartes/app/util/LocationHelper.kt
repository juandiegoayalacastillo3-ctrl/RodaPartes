package com.rodapartes.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/** Ubicación obtenida: coordenadas y la dirección legible que devuelve el Geocoder. */
data class PlaceResult(val lat: Double, val lng: Double, val address: String)

/**
 * Geolocalización con las APIs de Google Play Services (RF-PAG-02).
 *
 * 1. FusedLocationProviderClient pide la ubicación actual (GPS + red).
 * 2. Geocoder convierte las coordenadas en una dirección ("Calle 80 # 69-22, Bogotá").
 * El permiso se pide en el momento en que el usuario toca "Usar mi ubicación" (RNF-SEG-06).
 */
object LocationHelper {

    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /** true si el usuario ya dio permiso de ubicación (precisa o aproximada). */
    fun hasPermission(context: Context) = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Devuelve la ubicación actual o null si el GPS está apagado o no hubo señal. */
    @SuppressLint("MissingPermission") // el permiso se revisa antes de llamar
    suspend fun current(context: Context): PlaceResult? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token
        ).await() ?: client.lastLocation.await() ?: return null
        val address = reverseGeocode(context, location.latitude, location.longitude)
        return PlaceResult(location.latitude, location.longitude, address)
    }

    /** Convierte coordenadas en una dirección. Si no hay servicio, deja las coordenadas. */
    @Suppress("DEPRECATION") // getFromLocation síncrono: se llama fuera del hilo principal
    suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String =
        withContext(Dispatchers.IO) {
            runCatching {
                Geocoder(context, Locale("es", "CO")).getFromLocation(lat, lng, 1)
                    ?.firstOrNull()?.let { a ->
                        listOfNotNull(a.thoroughfare?.let { t -> listOfNotNull(t, a.subThoroughfare).joinToString(" # ") },
                            a.locality).joinToString(", ").ifBlank { a.getAddressLine(0) }
                    }
            }.getOrNull() ?: String.format(Locale.US, "%.5f, %.5f", lat, lng)
        }

    /** Abre Google Maps en el punto indicado (o la app de mapas que tenga el celular). */
    fun openInMaps(context: Context, lat: Double, lng: Double, label: String) {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
