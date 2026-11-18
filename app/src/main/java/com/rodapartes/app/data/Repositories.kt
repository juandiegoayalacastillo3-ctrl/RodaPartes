package com.rodapartes.app.data

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Accesos únicos a Firebase y nombres de las colecciones.
 * Se usan instancias por defecto, configuradas con google-services.json.
 */
object Fire {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    const val USERS = "users"
    const val PRODUCTS = "products"
    const val CARTS = "carts"
    const val ITEMS = "items"
    const val ORDERS = "orders"
    const val PAYMENTS = "payments"

    /** Metadatos de las fotos: las reglas de Storage solo aceptan imágenes. */
    val JPEG: StorageMetadata = StorageMetadata.Builder().setContentType("image/jpeg").build()

    /** uid del usuario con sesión iniciada, o null si es invitado. */
    val uid: String? get() = auth.currentUser?.uid
}

/** Error con un mensaje ya listo para mostrar al usuario. */
class AppException(message: String) : Exception(message)

/* ============================================================================================
 *  AUTENTICACIÓN (RF-AUT-01 a RF-AUT-05)
 * ============================================================================================ */
object AuthRepository {

    /**
     * Crea la cuenta en Firebase Auth y su documento en users/{uid}.
     * Los vendedores quedan PENDIENTES hasta que el administrador revise su NIT (RF-ADU-04).
     */
    suspend fun register(
        name: String, email: String, phone: String, password: String,
        role: Role, storeName: String, nit: String, promos: Boolean
    ): AppUser {
        try {
            val result = Fire.auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user!!.uid
            val user = AppUser(
                uid = uid,
                name = name,
                email = email,
                phone = phone,
                document = nit,
                role = role.name,
                status = if (role == Role.SELLER) UserStatus.PENDIENTE.name else UserStatus.ACTIVO.name,
                promos = promos,
                storeName = storeName,
                createdAt = Timestamp.now()
            )
            Fire.db.collection(Fire.USERS).document(uid).set(user).await()
            return user
        } catch (e: Exception) {
            throw AppException(friendlyMessage(e))
        }
    }

    /**
     * Inicia sesión y devuelve el perfil con su rol.
     * Una cuenta suspendida o eliminada no puede entrar: se cierra la sesión de inmediato.
     */
    suspend fun login(email: String, password: String): AppUser {
        try {
            Fire.auth.signInWithEmailAndPassword(email, password).await()
        } catch (e: Exception) {
            throw AppException(friendlyMessage(e))
        }
        val profile = UserRepository.current() ?: run {
            Fire.auth.signOut()
            throw AppException("No encontramos el perfil de esta cuenta")
        }
        when (profile.statusEnum) {
            UserStatus.SUSPENDIDO -> {
                Fire.auth.signOut(); throw AppException("Tu cuenta está suspendida. Escribe a soporte.")
            }
            UserStatus.ELIMINADO -> {
                Fire.auth.signOut(); throw AppException("Esta cuenta fue eliminada")
            }
            else -> return profile
        }
    }

    /** Envía el correo de restablecimiento en español (RF-AUT-05). */
    suspend fun sendPasswordReset(email: String) {
        Fire.auth.setLanguageCode("es")
        try {
            Fire.auth.sendPasswordResetEmail(email).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // No se revela si el correo existe: la pantalla muestra el mismo mensaje en ambos casos
        }
    }

    /** Vuelve a pedir la contraseña antes de una acción sensible (activar huella, crear admin). */
    suspend fun reauthenticate(password: String) {
        val user = Fire.auth.currentUser ?: throw AppException("Inicia sesión de nuevo")
        try {
            user.reauthenticate(EmailAuthProvider.getCredential(user.email!!, password)).await()
        } catch (e: Exception) {
            throw AppException("La contraseña no es correcta")
        }
    }

    fun logout() = Fire.auth.signOut()

    /** Traduce los errores de Firebase Auth a mensajes claros en español. */
    fun friendlyMessage(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil"
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo"
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException ->
            "Correo o contraseña incorrectos"
        is AppException -> e.message ?: "Ocurrió un error"
        else -> if (e.message?.contains("network", true) == true)
            "Sin conexión a internet. Revisa tu red e intenta de nuevo"
        else "No se pudo completar la acción. Intenta de nuevo"
    }
}

/* ============================================================================================
 *  USUARIOS (perfil propio y CRUD del administrador, RF-ADU)
 * ============================================================================================ */
object UserRepository {

    /** Perfil del usuario con sesión iniciada (o null si es invitado). */
    suspend fun current(): AppUser? {
        val uid = Fire.uid ?: return null
        return Fire.db.collection(Fire.USERS).document(uid).get().await().toObject(AppUser::class.java)
    }

    suspend fun get(uid: String): AppUser? =
        Fire.db.collection(Fire.USERS).document(uid).get().await().toObject(AppUser::class.java)

    /** Actualiza solo los campos indicados del documento del usuario. */
    suspend fun update(uid: String, fields: Map<String, Any?>) {
        Fire.db.collection(Fire.USERS).document(uid).set(fields, SetOptions.merge()).await()
    }

    /** Sube la foto de perfil a Storage (users/{uid}/avatar.jpg) y guarda su URL. */
    suspend fun uploadAvatar(uid: String, uri: Uri): String {
        val ref = Fire.storage.reference.child("users/$uid/avatar.jpg")
        ref.putFile(uri, Fire.JPEG).await()
        val url = ref.downloadUrl.await().toString()
        update(uid, mapOf("photoUrl" to url))
        return url
    }

    /** Escucha en tiempo real todos los usuarios (lista del administrador). */
    fun listenAll(onChange: (List<AppUser>) -> Unit): ListenerRegistration =
        Fire.db.collection(Fire.USERS).orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                onChange(snap?.toObjects(AppUser::class.java).orEmpty()
                    .filter { it.statusEnum != UserStatus.ELIMINADO })
            }

    /**
     * El administrador crea una cuenta nueva (RF-ADU-02).
     * Se usa una segunda instancia de Firebase para que la sesión del administrador no se cierre
     * al crear el usuario (createUser inicia sesión automáticamente con la cuenta nueva).
     */
    suspend fun adminCreate(context: Context, user: AppUser, password: String): String {
        val main = FirebaseApp.getInstance()
        val secondary = FirebaseApp.getApps(context).firstOrNull { it.name == "admin-create" }
            ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder(main.options).build(), "admin-create")
        val secondaryAuth = FirebaseAuth.getInstance(secondary)
        try {
            val result = secondaryAuth.createUserWithEmailAndPassword(user.email, password).await()
            val uid = result.user!!.uid
            Fire.db.collection(Fire.USERS).document(uid).set(user.copy(uid = uid, createdAt = Timestamp.now())).await()
            return uid
        } catch (e: Exception) {
            throw AppException(AuthRepository.friendlyMessage(e))
        } finally {
            secondaryAuth.signOut()
        }
    }

    /**
     * Borrado lógico (RF-ADU-05): la cuenta queda ELIMINADA y se borran sus datos personales.
     * No se borra el documento para conservar la relación con las órdenes ya facturadas.
     */
    suspend fun adminDelete(uid: String) {
        update(uid, mapOf(
            "status" to UserStatus.ELIMINADO.name,
            "name" to "Usuario eliminado",
            "phone" to "",
            "document" to "",
            "photoUrl" to "",
            "storeAddress" to "",
            "deletedAt" to Timestamp.now()
        ))
    }
}

/* ============================================================================================
 *  PRODUCTOS (CRUD del vendedor y moderación del administrador, RF-PRO / RF-ADP)
 * ============================================================================================ */
object ProductRepository {

    private val col get() = Fire.db.collection(Fire.PRODUCTS)

    /** Catálogo público: solo publicaciones aprobadas (RF-INI-01). */
    fun listenCatalog(onChange: (List<Product>) -> Unit): ListenerRegistration =
        col.whereEqualTo("status", ProductStatus.PUBLICADO.name)
            .addSnapshotListener { snap, _ ->
                onChange(snap?.toObjects(Product::class.java).orEmpty()
                    .filter { it.reports < 3 }
                    .sortedByDescending { it.createdAt })
            }

    /** Inventario del vendedor con sesión iniciada. */
    fun listenBySeller(sellerId: String, onChange: (List<Product>) -> Unit): ListenerRegistration =
        col.whereEqualTo("sellerId", sellerId)
            .addSnapshotListener { snap, _ ->
                onChange(snap?.toObjects(Product::class.java).orEmpty().sortedByDescending { it.createdAt })
            }

    /** Todos los productos (moderación del administrador). */
    fun listenAll(onChange: (List<Product>) -> Unit): ListenerRegistration =
        col.addSnapshotListener { snap, _ ->
            onChange(snap?.toObjects(Product::class.java).orEmpty().sortedByDescending { it.createdAt })
        }

    suspend fun get(id: String): Product? = col.document(id).get().await().toObject(Product::class.java)

    /** Id nuevo para un producto antes de subir sus fotos. */
    fun newId(): String = col.document().id

    /**
     * Sube a Storage las fotos nuevas (products/{id}/xxx.jpg) y devuelve todas las URL
     * en el orden en que el vendedor las dejó. Las que ya eran URL se conservan.
     */
    suspend fun uploadPhotos(productId: String, photos: List<Any>): List<String> =
        photos.map { photo ->
            when (photo) {
                is String -> photo
                is Uri -> {
                    val ref = Fire.storage.reference.child("products/$productId/${UUID.randomUUID()}.jpg")
                    ref.putFile(photo, Fire.JPEG).await()
                    ref.downloadUrl.await().toString()
                }
                else -> throw IllegalArgumentException("Foto no válida")
            }
        }

    /** Crea o reemplaza el producto completo. */
    suspend fun save(product: Product) {
        col.document(product.id).set(product.copy(updatedAt = Timestamp.now())).await()
    }

    /** Borra el producto y sus fotos de Storage (RF-PRO-06). */
    suspend fun delete(product: Product) {
        product.photos.forEach { url ->
            runCatching { Fire.storage.getReferenceFromUrl(url).delete().await() }
        }
        col.document(product.id).delete().await()
    }

    /** Quita de Storage las fotos que el vendedor eliminó al editar. */
    suspend fun deletePhotos(urls: List<String>) {
        urls.forEach { runCatching { Fire.storage.getReferenceFromUrl(it).delete().await() } }
    }

    /** Moderación: aprobar o rechazar con motivo (RF-ADP-01). */
    suspend fun setStatus(id: String, status: ProductStatus, reason: String = "") {
        col.document(id).update(mapOf(
            "status" to status.name,
            "reports" to 0,
            "moderationReason" to reason,
            "updatedAt" to Timestamp.now()
        )).await()
    }

    /** Un comprador reporta una publicación (posible imitación o información engañosa). */
    suspend fun report(id: String) {
        col.document(id).update("reports", FieldValue.increment(1)).await()
    }
}

/* ============================================================================================
 *  CARRITO (CRUD en carts/{uid}/items, RF-CAR)
 * ============================================================================================ */
object CartRepository {

    private fun items(uid: String) = Fire.db.collection(Fire.CARTS).document(uid).collection(Fire.ITEMS)

    /** Escucha el carrito del usuario en tiempo real. */
    fun listen(uid: String, onChange: (List<CartItem>) -> Unit): ListenerRegistration =
        items(uid).addSnapshotListener { snap, _ ->
            onChange(snap?.toObjects(CartItem::class.java).orEmpty().sortedBy { it.name })
        }

    suspend fun getAll(uid: String): List<CartItem> =
        items(uid).get().await().toObjects(CartItem::class.java)

    /** Agrega un producto o suma cantidad si ya estaba (sin pasar del stock). */
    suspend fun add(uid: String, product: Product, qty: Long) {
        val ref = items(uid).document(product.id)
        Fire.db.runTransaction { tx ->
            val current = tx.get(ref).getLong("qty") ?: 0L
            val item = CartItem(
                productId = product.id,
                name = product.name,
                price = product.price,
                qty = (current + qty).coerceAtMost(product.stock),
                stock = product.stock,
                sellerId = product.sellerId,
                sellerName = product.sellerName,
                category = product.category,
                photo = product.mainPhoto.orEmpty()
            )
            tx.set(ref, item)
        }.await()
    }

    suspend fun updateQty(uid: String, productId: String, qty: Long) {
        items(uid).document(productId).update("qty", qty).await()
    }

    suspend fun remove(uid: String, productId: String) {
        items(uid).document(productId).delete().await()
    }

    /** Vacía el carrito con una escritura por lotes. */
    suspend fun clear(uid: String) {
        val docs = items(uid).get().await()
        val batch = Fire.db.batch()
        docs.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}

/* ============================================================================================
 *  ÓRDENES (historial y estados, RF-COM)
 * ============================================================================================ */
object OrderRepository {

    private val col get() = Fire.db.collection(Fire.ORDERS)

    fun listenByBuyer(uid: String, onChange: (List<Order>) -> Unit): ListenerRegistration =
        col.whereEqualTo("buyerId", uid).addSnapshotListener { snap, _ ->
            onChange(snap?.toObjects(Order::class.java).orEmpty().sortedByDescending { it.createdAt })
        }

    fun listenBySeller(uid: String, onChange: (List<Order>) -> Unit): ListenerRegistration =
        col.whereEqualTo("sellerId", uid).addSnapshotListener { snap, _ ->
            onChange(snap?.toObjects(Order::class.java).orEmpty().sortedByDescending { it.createdAt })
        }

    fun listenAll(onChange: (List<Order>) -> Unit): ListenerRegistration =
        col.addSnapshotListener { snap, _ ->
            onChange(snap?.toObjects(Order::class.java).orEmpty().sortedByDescending { it.createdAt })
        }

    suspend fun setStatus(id: String, status: OrderStatus) {
        col.document(id).update(mapOf("status" to status.name, "updatedAt" to Timestamp.now())).await()
    }

    /**
     * Registra la compra después de que la pasarela confirma el pago:
     * 1. Crea una orden por cada vendedor del carrito.
     * 2. Descuenta el stock de cada producto.
     * 3. Guarda el comprobante en payments/{ref} y vacía el carrito.
     * Todo va en una sola transacción: o se guarda completo o no se guarda nada.
     */
    suspend fun createFromCart(
        buyer: AppUser,
        cart: List<CartItem>,
        shippingPerOrder: Long,
        delivery: Boolean,
        address: String,
        addressDetail: String,
        lat: Double?,
        lng: Double?,
        paymentRef: String,
        paymentMethod: String
    ): List<String> {
        val bySeller = cart.groupBy { it.sellerId }
        val codes = mutableListOf<String>()
        Fire.db.runTransaction { tx ->
            // Lecturas primero (regla de las transacciones): stock actual de cada producto
            val stock = cart.associate { item ->
                item.productId to (tx.get(Fire.db.collection(Fire.PRODUCTS).document(item.productId)).getLong("stock") ?: 0L)
            }
            cart.forEach { item ->
                if ((stock[item.productId] ?: 0L) < item.qty) throw AppException("Ya no hay stock suficiente de ${item.name}")
            }
            codes.clear()
            bySeller.forEach { (sellerId, items) ->
                val ref = col.document()
                val code = "RP-" + ref.id.takeLast(5).uppercase()
                codes += code
                val subtotal = items.sumOf { it.subtotal }
                val shipping = if (delivery) shippingPerOrder else 0L
                tx.set(ref, Order(
                    code = code,
                    buyerId = buyer.uid,
                    buyerName = buyer.name,
                    sellerId = sellerId,
                    sellerName = items.first().sellerName,
                    items = items.map { OrderItem(it.productId, it.name, it.price, it.qty, it.category) },
                    subtotal = subtotal,
                    shipping = shipping,
                    total = subtotal + shipping,
                    status = OrderStatus.NUEVO.name,
                    address = if (delivery) address else "Recoge en la tienda",
                    addressDetail = addressDetail,
                    lat = lat,
                    lng = lng,
                    delivery = delivery,
                    paymentRef = paymentRef,
                    paymentMethod = paymentMethod,
                    paymentStatus = "Aceptada",
                    createdAt = Timestamp.now()
                ))
            }
            cart.forEach { item ->
                tx.update(Fire.db.collection(Fire.PRODUCTS).document(item.productId),
                    "stock", FieldValue.increment(-item.qty))
            }
            tx.set(Fire.db.collection(Fire.PAYMENTS).document(paymentRef), mapOf(
                "buyerId" to buyer.uid,
                "ref" to paymentRef,
                "method" to paymentMethod,
                "total" to cart.sumOf { it.subtotal } + (if (delivery) shippingPerOrder * bySeller.size else 0L),
                "orders" to codes.toList(),
                "createdAt" to Timestamp.now()
            ))
            cart.forEach { item ->
                tx.delete(Fire.db.collection(Fire.CARTS).document(buyer.uid).collection(Fire.ITEMS).document(item.productId))
            }
        }.await()
        return codes
    }
}

/** Valores de precio usados en el carrito y el checkout. */
object Pricing {
    /** Costo del domicilio por cada tienda del pedido (RF-PAG-02). */
    const val SHIPPING_PER_STORE = 9_000L

    fun subtotal(cart: List<CartItem>) = cart.sumOf { it.subtotal }
    fun stores(cart: List<CartItem>) = cart.map { it.sellerId }.distinct().size
    fun shipping(cart: List<CartItem>, delivery: Boolean = true) =
        if (delivery) SHIPPING_PER_STORE * stores(cart) else 0L
    fun total(cart: List<CartItem>, delivery: Boolean = true) = subtotal(cart) + shipping(cart, delivery)
}

/* ============================================================================================
 *  BITÁCORA DE AUDITORÍA (RF-ADU-06): cada acción del administrador queda registrada
 * ============================================================================================ */
object AuditRepository {

    private val col get() = Fire.db.collection("audit")

    /** Registra quién hizo qué y cuándo. Si falla, no detiene la acción principal. */
    suspend fun log(action: String) {
        runCatching {
            col.add(mapOf(
                "adminId" to Fire.uid,
                "adminEmail" to Fire.auth.currentUser?.email,
                "action" to action,
                "createdAt" to Timestamp.now()
            )).await()
        }
    }

    /** Últimas 30 acciones, de la más reciente a la más antigua. */
    suspend fun recent(): List<Pair<java.util.Date?, String>> =
        col.orderBy("createdAt", Query.Direction.DESCENDING).limit(30).get().await()
            .documents.map { it.getTimestamp("createdAt")?.toDate() to (it.getString("action") ?: "") }
}
