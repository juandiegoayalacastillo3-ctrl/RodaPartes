package com.rodapartes.app.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.rodapartes.app.R

/*
 * Modelos de la app. Cada data class corresponde a un documento de Firestore:
 *   users/{uid}                 -> AppUser
 *   products/{productId}        -> Product
 *   carts/{uid}/items/{pid}     -> CartItem
 *   orders/{orderId}            -> Order (con su lista de OrderItem)
 *
 * Firestore necesita un constructor vacío para convertir los documentos en objetos, por eso
 * todos los campos tienen valor por defecto. Los enums se guardan como texto (su nombre) y se
 * exponen con propiedades marcadas con @get:Exclude para que no se guarden dos veces.
 */

/** Rol del usuario. Define qué interfaz se abre después del login. */
enum class Role(val label: String) {
    BUYER("Comprador"), SELLER("Vendedor"), ADMIN("Administrador");

    companion object {
        /** Convierte el texto guardado en Firestore al enum; si no existe, comprador. */
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: BUYER
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: BUYER
    }
}

/** Categorías del catálogo. Cada una tiene su degradado e ícono para cuando no hay foto. */
enum class Category(val label: String, @DrawableRes val tileRes: Int, @DrawableRes val iconRes: Int) {
    FRENOS("Frenos", R.drawable.bg_tile_frenos, R.drawable.ic_cat_frenos),
    ACEITES("Aceites", R.drawable.bg_tile_aceite, R.drawable.ic_cat_aceite),
    CASCOS("Cascos", R.drawable.bg_tile_cascos, R.drawable.ic_cat_cascos),
    ELECTRICO("Eléctrico", R.drawable.bg_tile_electrico, R.drawable.ic_cat_electrico),
    MOTOR("Motor", R.drawable.bg_tile_motor, R.drawable.ic_cat_motor),
    LLANTAS("Llantas", R.drawable.bg_tile_llantas, R.drawable.ic_cat_llantas);

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: FRENOS
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: FRENOS
    }
}

/** Estado con su par de colores (texto / fondo) para pintar las etiquetas de estado. */
interface Status {
    val label: String
    @get:ColorRes val textColor: Int
    @get:ColorRes val bgColor: Int
}

/** Estados de una publicación. Las nuevas pasan por revisión del administrador (RF-ADP-01). */
enum class ProductStatus(
    override val label: String,
    override val textColor: Int,
    override val bgColor: Int
) : Status {
    PUBLICADO("Publicado", R.color.exito, R.color.exito_claro),
    EN_REVISION("En revisión", R.color.aviso, R.color.aviso_claro),
    BORRADOR("Borrador", R.color.texto_2, R.color.fondo),
    RECHAZADO("Rechazado", R.color.peligro, R.color.peligro_claro);

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: EN_REVISION
    }
}

/** Estados de una orden de compra. */
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

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: NUEVO
    }
}

/** Estados de una cuenta. ELIMINADO = borrado lógico (los datos personales se anonimizan). */
enum class UserStatus(
    override val label: String,
    override val textColor: Int,
    override val bgColor: Int
) : Status {
    ACTIVO("Activo", R.color.exito, R.color.exito_claro),
    PENDIENTE("Pendiente", R.color.aviso, R.color.aviso_claro),
    SUSPENDIDO("Suspendido", R.color.peligro, R.color.peligro_claro),
    ELIMINADO("Eliminado", R.color.texto_2, R.color.fondo);

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: ACTIVO
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: ACTIVO
    }
}

/** Documento users/{uid}. Guarda el perfil y el rol de cada cuenta. */
data class AppUser(
    @DocumentId var uid: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var document: String = "",
    var role: String = Role.BUYER.name,
    var status: String = UserStatus.ACTIVO.name,
    var photoUrl: String = "",
    var moto: String = "",
    var promos: Boolean = false,
    // Datos de la tienda (solo vendedores)
    var storeName: String = "",
    var storeAddress: String = "",
    var storeSchedule: String = "",
    var storeLat: Double? = null,
    var storeLng: Double? = null,
    var createdAt: Timestamp? = null
) {
    @get:Exclude val roleEnum get() = Role.from(role)
    @get:Exclude val statusEnum get() = UserStatus.from(status)

    /** Nombre que se muestra: la tienda para vendedores, el nombre para los demás. */
    @get:Exclude val displayName get() = if (roleEnum == Role.SELLER && storeName.isNotBlank()) storeName else name
}

/** Documento products/{id}. Las fotos se guardan en Storage y aquí solo sus URL. */
data class Product(
    @DocumentId var id: String = "",
    var name: String = "",
    var ref: String = "",
    var category: String = Category.FRENOS.name,
    var price: Long = 0,
    var stock: Long = 0,
    var minStock: Long = 2,
    var sellerId: String = "",
    var sellerName: String = "",
    var compatible: List<String> = emptyList(),
    var original: Boolean = false,
    var status: String = ProductStatus.EN_REVISION.name,
    var description: String = "",
    var warrantyMonths: Long = 0,
    var reports: Long = 0,
    var photos: List<String> = emptyList(),
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null
) {
    @get:Exclude val categoryEnum get() = Category.from(category)
    @get:Exclude val statusEnum get() = ProductStatus.from(status)
    @get:Exclude val lowStock get() = stock <= minStock
    @get:Exclude val mainPhoto get() = photos.firstOrNull()

    /** ¿Le sirve a la moto del comprador? (RF-INI-04) */
    fun fits(moto: String) = moto.isNotBlank() && (compatible.contains(moto) || compatible.contains("Todas las motos"))
}

/** Documento carts/{uid}/items/{productId}. El id del documento es el del producto. */
data class CartItem(
    @DocumentId var productId: String = "",
    var name: String = "",
    var price: Long = 0,
    var qty: Long = 1,
    var stock: Long = 0,
    var sellerId: String = "",
    var sellerName: String = "",
    var category: String = Category.FRENOS.name,
    var photo: String = ""
) {
    @get:Exclude val subtotal get() = price * qty
    @get:Exclude val categoryEnum get() = Category.from(category)
}

/** Producto dentro de una orden (copia del precio al momento de comprar). */
data class OrderItem(
    var productId: String = "",
    var name: String = "",
    var price: Long = 0,
    var qty: Long = 1,
    var category: String = Category.FRENOS.name
)

/** Documento orders/{id}. Se crea una orden por cada vendedor del carrito. */
data class Order(
    @DocumentId var id: String = "",
    var code: String = "",
    var buyerId: String = "",
    var buyerName: String = "",
    var sellerId: String = "",
    var sellerName: String = "",
    var items: List<OrderItem> = emptyList(),
    var subtotal: Long = 0,
    var shipping: Long = 0,
    var total: Long = 0,
    var status: String = OrderStatus.NUEVO.name,
    var address: String = "",
    var addressDetail: String = "",
    var lat: Double? = null,
    var lng: Double? = null,
    var delivery: Boolean = true,
    var paymentRef: String = "",
    var paymentMethod: String = "",
    var paymentStatus: String = "",
    var createdAt: Timestamp? = null
) {
    @get:Exclude val statusEnum get() = OrderStatus.from(status)
    @get:Exclude val itemCount get() = items.sumOf { it.qty }.toInt()
    @get:Exclude val summary get() = items.joinToString { if (it.qty > 1) "${it.name} ×${it.qty}" else it.name }
}
