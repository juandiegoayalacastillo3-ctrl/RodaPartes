package com.rodapartes.app.data

/*
 * Datos de prueba en memoria para poder navegar todas las pantallas.
 * Se reemplaza por repositorios de Firestore en la fase de backend.
 */
object MockData {

    /** Moto guardada en el perfil del comprador (RF-INI-04). */
    var myMoto = "AKT NKD 125"
    const val SHIPPING = 9_000

    val products = mutableListOf(
        Product(1, "Pastillas de freno delanteras", "PF-AKT125-D", Category.FRENOS, 38_900, 14, 5,
            "MotoCentro 80", 2.1, listOf("AKT NKD 125", "TVS Raider 125"), false, ProductStatus.PUBLICADO,
            "Pastillas semimetálicas para disco delantero. Buen agarre en lluvia y bajo ruido.", 3),
        Product(2, "Aceite 20W-50 4T 1 L", "AC-2050-1L", Category.ACEITES, 32_500, 3, 6,
            "MotoCentro 80", 2.1, listOf("Todas las motos"), true, ProductStatus.PUBLICADO,
            "Aceite mineral para motores de cuatro tiempos. Norma API SL / JASO MA2.", 0),
        Product(3, "Casco abatible certificado", "CA-ABT-M", Category.CASCOS, 289_000, 6, 2,
            "Repuestos La 68", 3.4, listOf("Todas las motos"), true, ProductStatus.PUBLICADO,
            "Casco abatible con doble visor y certificación DOT. Talla M.", 12),
        Product(4, "Batería 12V 5Ah sellada", "BT-12V5", Category.ELECTRICO, 115_000, 9, 3,
            "Electro Motos Suba", 5.8, listOf("AKT NKD 125", "Bajaj Pulsar 135", "Honda CB 125F"), true,
            ProductStatus.PUBLICADO, "Batería libre de mantenimiento, lista para instalar.", 6),
        Product(5, "Kit de arrastre 428", "KA-428-NKD", Category.MOTOR, 129_000, 6, 2,
            "MotoCentro 80", 2.1, listOf("AKT NKD 125"), false, ProductStatus.PUBLICADO,
            "Cadena 428 con piñón y corona. Incluye candado.", 3),
        Product(6, "Llanta trasera 90/90-18", "LL-9090-18", Category.LLANTAS, 168_000, 4, 2,
            "Llantas Express", 4.0, listOf("TVS Raider 125", "Honda CB 125F", "AKT NKD 125"), true,
            ProductStatus.PUBLICADO, "Llanta de uso urbano con buen drenaje para lluvia.", 6),
        Product(7, "Bombillo LED H4", "BL-H4-LED", Category.ELECTRICO, 24_000, 20, 5,
            "MotoCentro 80", 2.1, listOf("Todas las motos"), false, ProductStatus.EN_REVISION,
            "Bombillo LED de luz blanca, bajo consumo.", 3),
        Product(8, "Disco de freno Pulsar 135", "DF-P135", Category.FRENOS, 96_000, 0, 2,
            "Repuestos La 68", 3.4, listOf("Bajaj Pulsar 135"), true, ProductStatus.PUBLICADO,
            "Disco delantero original Bajaj.", 6),
        Product(9, "Casco réplica marca X", "CA-RPX", Category.CASCOS, 120_000, 8, 2,
            "Moto Outlet 13", 7.2, listOf("Todas las motos"), true, ProductStatus.PUBLICADO,
            "Publicado como original. Reportado por posible imitación.", 0, reports = 3),
        Product(10, "Filtro de aire FZ 2.0", "FA-FZ20", Category.MOTOR, 27_500, 12, 4,
            "Llantas Express", 4.0, listOf("Yamaha FZ 2.0"), false, ProductStatus.EN_REVISION,
            "Filtro de aire de reemplazo.", 0)
    )

    val orders = mutableListOf(
        Order("RP-1042", "Laura Gómez", "MotoCentro 80", 3, 157_900, OrderStatus.NUEVO, "Hoy, 9:12 a. m.",
            "Calle 80 # 69-22", 2.1, "Pastillas de freno, Aceite 20W-50 ×2"),
        Order("RP-1040", "Carlos Méndez", "MotoCentro 80", 1, 289_000, OrderStatus.PREPARACION, "Hoy, 8:03 a. m.",
            "Cra 70 # 75-10", 1.4, "Casco abatible certificado"),
        Order("RP-1039", "Pedro López", "MotoCentro 80", 1, 45_000, OrderStatus.ENVIADO, "Ayer",
            "Av. Boyacá # 64-30", 3.3, "Guantes touch"),
        Order("RP-1031", "Carlos Méndez", "Repuestos La 68", 1, 96_000, OrderStatus.REVERSION, "3 nov.",
            "Cra 70 # 75-10", 3.4, "Disco de freno Pulsar 135"),
        Order("RP-1027", "Ana Rodríguez", "Llantas Express", 1, 168_000, OrderStatus.RETRACTO, "2 nov.",
            "Calle 63 # 24-11", 4.0, "Llanta trasera 90/90-18"),
        Order("RP-1019", "Pedro López", "MotoCentro 80", 2, 77_500, OrderStatus.ENTREGADO, "28 oct.",
            "Av. Boyacá # 64-30", 3.3, "Aceite 20W-50, Bombillo LED H4")
    )

    /** Compras del comprador que inició sesión. */
    val myOrders = mutableListOf(
        Order("RP-1036", "Juan Pérez", "MotoCentro 80", 2, 71_400, OrderStatus.ENVIADO, "6 nov.",
            "Calle 80 # 69-22", 2.1, "Pastillas de freno, Aceite 20W-50"),
        Order("RP-1012", "Juan Pérez", "Electro Motos Suba", 1, 124_000, OrderStatus.ENTREGADO, "21 oct.",
            "Calle 80 # 69-22", 5.8, "Batería 12V 5Ah sellada"),
        Order("RP-0988", "Juan Pérez", "Repuestos La 68", 1, 298_000, OrderStatus.ENTREGADO, "2 oct.",
            "Calle 80 # 69-22", 3.4, "Casco abatible certificado")
    )

    val users = mutableListOf(
        User(1, "Laura Gómez", "laura.gomez@correo.com", "300 456 7788", "CC 1.019.884.221", Role.BUYER, UserStatus.ACTIVO),
        User(2, "MotoCentro 80", "ventas@motocentro80.co", "601 744 1200", "NIT 901.234.567-8", Role.SELLER, UserStatus.ACTIVO),
        User(3, "Moto Outlet 13", "contacto@motooutlet13.co", "601 300 9090", "NIT 901.876.110-2", Role.SELLER, UserStatus.PENDIENTE),
        User(4, "Carlos Méndez", "carlos.mendez@correo.com", "311 222 3344", "CC 80.123.456", Role.BUYER, UserStatus.SUSPENDIDO),
        User(5, "Ana Ruiz", "ana.ruiz@rodapartes.co", "320 555 1010", "CC 52.987.654", Role.ADMIN, UserStatus.ACTIVO),
        User(6, "Llantas Express", "info@llantasexpress.co", "601 555 7878", "NIT 900.555.321-4", Role.SELLER, UserStatus.PENDIENTE),
        User(7, "Pedro López", "pedro.lopez@correo.com", "315 777 8899", "CC 1.032.444.909", Role.BUYER, UserStatus.ACTIVO)
    )

    val cart = mutableListOf<CartItem>()

    // ---------- Carrito (RF-CAR) ----------
    fun addToCart(product: Product, qty: Int) {
        val item = cart.firstOrNull { it.product.id == product.id }
        if (item != null) item.qty = (item.qty + qty).coerceAtMost(product.stock)
        else cart.add(CartItem(product, qty))
    }

    fun cartCount() = cart.sumOf { it.qty }
    fun cartSubtotal() = cart.sumOf { it.subtotal }
    fun cartTotal() = if (cart.isEmpty()) 0 else cartSubtotal() + SHIPPING

    fun product(id: Int) = products.first { it.id == id }
    fun user(id: Int) = users.first { it.id == id }

    /** Productos visibles para el comprador: solo los aprobados (RF-INI-01). */
    fun catalog() = products.filter { it.status == ProductStatus.PUBLICADO && it.reports == 0 }

    /** Productos de la tienda con la que se hace el demo de vendedor. */
    fun sellerProducts() = products.filter { it.seller == "MotoCentro 80" }

    fun sellerOrders() = orders.filter { it.seller == "MotoCentro 80" }

    private var nextId = 100
    fun newId() = nextId++
}
