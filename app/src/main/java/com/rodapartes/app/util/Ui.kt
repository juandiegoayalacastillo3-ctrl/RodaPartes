package com.rodapartes.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.rodapartes.app.data.AuthRepository
import com.rodapartes.app.data.Category
import com.rodapartes.app.data.Role
import com.rodapartes.app.data.Status
import com.rodapartes.app.ui.admin.AdminActivity
import com.rodapartes.app.ui.auth.LoginActivity
import com.rodapartes.app.ui.buyer.BuyerActivity
import com.rodapartes.app.ui.seller.SellerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formato de pesos colombianos con separador de miles: $ 38.900 (RNF-USA-04). */
object Money {
    private val format = DecimalFormat("#,###", DecimalFormatSymbols(Locale("es", "CO")))
    /** Formatea un valor en pesos colombianos: $ 157.900 */
    fun format(value: Long): String = "$ " + format.format(value)
    /** Igual que format(Long), para valores Int. */
    fun format(value: Int): String = format(value.toLong())

    /** Cifras grandes de los paneles: $ 2,4 M */
    fun short(value: Long): String = when {
        value >= 1_000_000 -> "$ " + String.format(Locale("es", "CO"), "%.1f M", value / 1_000_000.0)
        value >= 1_000 -> "$ " + (value / 1_000) + " mil"
        else -> "$ $value"
    }
}

/** Fechas cortas en español: "6 nov., 9:12 a. m." */
object Dates {
    private val format = SimpleDateFormat("d MMM, h:mm a", Locale("es", "CO"))
    /** Fecha corta (dd/MM/yyyy) para listas. */
    fun short(date: Date?): String = date?.let { format.format(it) } ?: ""
}

/** Pinta una etiqueta de estado con sus colores (texto y fondo). */
fun TextView.applyStatus(status: Status) {
    text = status.label
    setTextColor(ContextCompat.getColor(context, status.textColor))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, status.bgColor))
}

/** Muestra un mensaje corto en la parte inferior de la pantalla. */
fun View.snack(message: CharSequence) = Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()

/** Muestra u oculta la vista (VISIBLE / GONE). */
fun View.visible(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

/**
 * Muestra la foto del producto si tiene URL; si no, el degradado de su categoría con el ícono.
 * Coil descarga la imagen de Storage y la guarda en caché.
 */
fun bindProductImage(tile: View, icon: ImageView, photo: ImageView, category: Category, url: String?) {
    tile.setBackgroundResource(category.tileRes)
    tile.clipToOutline = true // la foto respeta las esquinas redondeadas del fondo
    icon.setImageResource(category.iconRes)
    if (url.isNullOrBlank()) {
        photo.visible(false)
        icon.visible(true)
    } else {
        photo.visible(true)
        icon.visible(false)
        photo.load(url) { crossfade(true) }
    }
}

/**
 * Ejecuta una acción con Firebase y muestra el error, si lo hay, en un Snackbar.
 * Evita repetir try/catch en cada pantalla.
 */
fun CoroutineScope.safeLaunch(anchor: View, onDone: () -> Unit = {}, block: suspend () -> Unit) = launch {
    try {
        block()
    } catch (e: Exception) {
        anchor.snack(AuthRepository.friendlyMessage(e))
    } finally {
        onDone()
    }
}

/** Atajo para fragments: corrutina atada al ciclo de vida de su vista. */
val Fragment.viewScope get() = viewLifecycleOwner.lifecycleScope

/** Abre la interfaz del rol y limpia la pila de pantallas (RF-AUT-07). */
object Router {
    /** Abre la pantalla principal del rol del usuario (RF-AUT-07). */
    fun goHome(activity: Activity, role: Role) {
        val target = when (role) {
            Role.BUYER -> BuyerActivity::class.java
            Role.SELLER -> SellerActivity::class.java
            Role.ADMIN -> AdminActivity::class.java
        }
        activity.startActivity(
            Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity.finish()
    }

    /** Cierra la sesión en Firebase y vuelve al login (RF-AUT-06). */
    fun logout(activity: Activity) {
        AuthRepository.logout()
        Session.clear()
        activity.startActivity(
            Intent(activity, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity.finish()
    }
}

/** Iniciales para los avatares de las listas. */
fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

/** Atajo para leer un color de los recursos. */
fun Context.color(id: Int) = ContextCompat.getColor(this, id)
