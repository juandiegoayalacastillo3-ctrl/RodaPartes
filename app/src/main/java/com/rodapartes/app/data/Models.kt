package com.rodapartes.app.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.rodapartes.app.R

/*
 * Modelos de la app. En esta entrega (frontend) los datos son de prueba y viven en memoria
 * (MockData). En la siguiente fase estos mismos modelos se llenan desde Firestore.
 */

enum class Role(val label: String) {
    BUYER("Comprador"), SELLER("Vendedor"), ADMIN("Administrador")
}

enum class Category(val label: String, @DrawableRes val tileRes: Int, @DrawableRes val iconRes: Int) {
    FRENOS("Frenos", R.drawable.bg_tile_frenos, R.drawable.ic_cat_frenos),
    ACEITES("Aceites", R.drawable.bg_tile_aceite, R.drawable.ic_cat_aceite),
    CASCOS("Cascos", R.drawable.bg_tile_cascos, R.drawable.ic_cat_cascos),
    ELECTRICO("Eléctrico", R.drawable.bg_tile_electrico, R.drawable.ic_cat_electrico),
    MOTOR("Motor", R.drawable.bg_tile_motor, R.drawable.ic_cat_motor),
    LLANTAS("Llantas", R.drawable.bg_tile_llantas, R.drawable.ic_cat_llantas);

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: FRENOS
    }
}

/** Estado con su par de colores (texto / fondo) para pintar las etiquetas. */
interface Status {
    val label: String
    @get:ColorRes val textColor: Int
    @get:ColorRes val bgColor: Int
}

enum class ProductStatus(
    override val label: String,
    override val textColor: Int,
    override val bgColor: Int
) : Status {
    PUBLICADO("Publicado", R.color.exito, R.color.exito_claro),
    EN_REVISION("En revisión", R.color.aviso, R.color.aviso_claro),
    BORRADOR("Borrador", R.color.texto_2, R.color.fondo),
    RECHAZADO("Rechazado", R.color.peligro, R.color.peligro_claro)
}

enum class OrderStatus(
    override val label: String,
    override val textColor: Int,
    override val bgColor: Int
) : Status {
    NUEVO("Nuevo", R.color.naranja_oscuro, R.color.naranja_claro),
    PREPARACION("En preparación", R.color.aviso, R.color.aviso_claro),
    ENVIADO("Enviado", R.color.info, R.color.info_claro),
    ENTREGADO("Entregado", R.color.exito, R.color.exito_claro),
    CANCELADO("Cancelado", R.color.texto_2, R.color.fondo),
    RETRACTO("Retracto", R.color.aviso, R.color.aviso_claro),
    REVERSION("Reversión", R.color.peligro, R.color.peligro_claro);

    /** Siguiente estado en el flujo del vendedor (RF-COM-03). */
    fun next(): OrderStatus = when (this) {
        NUEVO -> PREPARACION
        PREPARACION -> ENVIADO
        ENVIADO -> ENTREGADO
        else -> this
    }
}

enum class UserStatus(
    override val label: String,
    override val textColor: Int,
    override val bgColor: Int
) : Status {
    ACTIVO("Activo", R.color.exito, R.color.exito_claro),
    PENDIENTE("Pendiente", R.color.aviso, R.color.aviso_claro),
    SUSPENDIDO("Suspendido", R.color.peligro, R.color.peligro_claro);

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: ACTIVO
    }
}

data class Product(
    val id: Int,
    var name: String,
    var ref: String,
    var category: Category,
    var price: Int,
    var stock: Int,
    var minStock: Int,
    var seller: String,
    var distanceKm: Double,
    var compatible: List<String>,
    var original: Boolean,
    var status: ProductStatus,
    var description: String,
    var warrantyMonths: Int,
    var reports: Int = 0,
    var photos: Int = 2
) {
    val lowStock get() = stock <= minStock
    fun fits(moto: String) = compatible.contains(moto) || compatible.contains("Todas las motos")
}

data class CartItem(val product: Product, var qty: Int) {
    val subtotal get() = product.price * qty
}

data class Order(
    val code: String,
    val buyer: String,
    val seller: String,
    val items: Int,
    val total: Int,
    var status: OrderStatus,
    val date: String,
    val address: String,
    val distanceKm: Double,
    val summary: String
)

data class User(
    val id: Int,
    var name: String,
    var email: String,
    var phone: String,
    var document: String,
    var role: Role,
    var status: UserStatus
)
