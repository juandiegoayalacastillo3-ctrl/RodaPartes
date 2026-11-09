package com.rodapartes.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.rodapartes.app.data.Role
import com.rodapartes.app.data.Status
import com.rodapartes.app.ui.admin.AdminActivity
import com.rodapartes.app.ui.buyer.BuyerActivity
import com.rodapartes.app.ui.seller.SellerActivity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formato de pesos colombianos con separador de miles: $ 38.900 (RNF-USA-04). */
object Money {
    private val format = DecimalFormat("#,###", DecimalFormatSymbols(Locale("es", "CO")))
    fun format(value: Int): String = "$ " + format.format(value)

    /** Cifras grandes del panel: $ 2,4 M */
    fun short(value: Long): String = when {
        value >= 1_000_000 -> "$ " + String.format(Locale("es", "CO"), "%.1f M", value / 1_000_000.0)
        value >= 1_000 -> "$ " + (value / 1_000) + " mil"
        else -> "$ $value"
    }
}

/** Pinta una etiqueta de estado con sus colores (texto y fondo). */
fun TextView.applyStatus(status: Status) {
    text = status.label
    setTextColor(ContextCompat.getColor(context, status.textColor))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, status.bgColor))
}

fun View.snack(message: CharSequence) = Snackbar.make(this, message, Snackbar.LENGTH_SHORT).show()

fun View.visible(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

/** Abre la interfaz del rol y limpia la pila de pantallas (RF-AUT-07). */
object Router {
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

    fun logout(activity: Activity) {
        Session(activity).logout()
        activity.startActivity(
            Intent(activity, com.rodapartes.app.ui.auth.LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity.finish()
    }
}

/** Iniciales para los avatares de las listas. */
fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

fun Context.color(id: Int) = ContextCompat.getColor(this, id)
