# RodaPartes · App Android funcional

Tienda virtual Android de repuestos y accesorios para motocicletas con tres roles: **Comprador**, **Vendedor** y **Administrador**.
Actividad 5 de **Desarrollo de Aplicaciones Móviles** (Fundación Universitaria Compensar).

**Autor:** Juan Diego Ayala Castillo

**APK:** [Releases → v2.0](https://github.com/juandiegoayalacastillo3-ctrl/RodaPartes/releases/tag/v2.0)

## Funcionalidades

| Módulo | Qué hace | Dónde está |
|---|---|---|
| Autenticación | Registro, login, cierre de sesión y recuperación de contraseña con **Firebase Authentication**. El rol se lee de Firestore y cada usuario entra a su interfaz. Bloqueo temporal después de 5 intentos fallidos. | `ui/auth/*`, `AuthRepository` |
| Huella digital | Ingreso con huella (`BiometricPrompt`, `BIOMETRIC_STRONG`) para los 3 roles. Las credenciales se guardan cifradas con **EncryptedSharedPreferences** (Android Keystore). La huella nunca sale del celular. | `BiometricHelper`, `BiometricSetup`, `SecureStore` |
| CRUD de usuarios (admin) | Listar, crear (cualquier rol), editar, aprobar vendedores, suspender y eliminar (borrado lógico). Todo queda en **Firestore** y en la bitácora de auditoría. | `ui/admin/*`, `UserRepository`, `AuditRepository` |
| CRUD de productos (vendedor) | Crear, editar, despublicar y eliminar productos. Las fotos se toman con la **cámara** o se eligen de la **galería** y se suben a **Firebase Storage**. El admin modera las publicaciones. | `ProductFormActivity`, `ProductRepository` |
| Carrito y órdenes | El carrito se guarda en Firestore (`carts/{uid}/items`) y se sincroniza en tiempo real. Al pagar, una **transacción** crea una orden por tienda, descuenta el stock y vacía el carrito. Historial para comprador, vendedor y admin. | `CartRepository`, `OrderRepository` |
| Geolocalización | La dirección de entrega se toma con **Fused Location Provider** y se convierte en dirección con **Geocoder**. Las coordenadas quedan en la orden y el vendedor las abre en Maps. | `LocationHelper`, `CheckoutActivity` |
| Pagos | **Wompi** (Bancolombia) en modo sandbox: Web Checkout con firma de integridad SHA-256. Después del pago, la app consulta la transacción en el API de Wompi y solo crea las órdenes si el pago quedó `APPROVED` y el valor y la referencia coinciden. | `WompiRepository`, `PaymentActivity` |

## Tecnologías

- Kotlin + layouts XML (ViewBinding) · Material Components 3
- `minSdk 26` (Android 8.0) · `targetSdk 34`
- Firebase BoM 33.7.0: Auth, Firestore, Storage
- Corrutinas + `kotlinx-coroutines-play-services` (`await()`)
- Play Services Location 21.3.0
- Coil 2.7.0 para cargar las fotos desde Storage
- AndroidX Biometric y Security Crypto

## Arquitectura

```
app/src/main/java/com/rodapartes/app/
├── data/        Modelos (Models.kt), repositorios de Firebase (Repositories.kt) y pasarela (WompiRepository.kt)
├── util/        Sesión y SecureStore, huella, ubicación, fotos de cámara, utilidades de interfaz
└── ui/
    ├── auth/    Splash, login, registro, recuperar contraseña
    ├── buyer/   Inicio, catálogo, detalle, carrito, entrega, pago, mis compras, perfil
    ├── seller/  Panel, mis productos, formulario de producto, pedidos, mi tienda
    ├── admin/   Dashboard, usuarios, moderación de productos, reporte de ventas
    └── common/  Adaptadores, estadísticas y gráfica de barras
```

Las pantallas no hablan con Firebase directamente: llaman a los **repositorios** de `data/`, que usan corrutinas y listeners en tiempo real.

### Colecciones de Firestore

| Colección | Contenido |
|---|---|
| `users/{uid}` | Perfil, rol (`BUYER`, `SELLER`, `ADMIN`) y estado (`ACTIVO`, `PENDIENTE`, `SUSPENDIDO`, `ELIMINADO`) |
| `products/{id}` | Producto, fotos (URLs de Storage), stock, estado de publicación y reportes |
| `carts/{uid}/items/{productId}` | Carrito de cada usuario |
| `orders/{id}` | Una orden por tienda, con dirección, coordenadas y referencia del pago |
| `payments/{id}` | Comprobante de la transacción de Wompi |
| `audit/{id}` | Bitácora de acciones del administrador |

La seguridad por rol se aplica en el servidor con `firestore.rules` y `storage.rules` (en la raíz del repo), no solo ocultando botones.

## Cómo compilarlo

1. Abrir la carpeta en Android Studio (Koala o superior) y esperar el *Gradle Sync*.
2. `app/google-services.json` ya trae la configuración del proyecto de Firebase `rodapartes`.
3. Para pagos, llenar en `gradle.properties` las llaves **sandbox** de Wompi (panel de comercios → Desarrolladores):
   ```
   wompiPublicKey=pub_test_...
   wompiIntegrity=test_integrity_...
   ```
4. `Build > Build APK(s)`. El APK también se compila solo con GitHub Actions (`.github/workflows/apk.yml`) y se publica en Releases.

## Probar los pagos (sandbox)

No se cobra dinero real.

| Tarjeta | Resultado |
|---|---|
| `4242 4242 4242 4242` | Aprobada |
| `4111 1111 1111 1111` | Rechazada |

Cualquier fecha futura y CVV de 3 dígitos.

## Probar la huella

Inicia sesión con correo y contraseña. La app pregunta si quieres activar la huella (también se activa desde **Perfil**). Cierra sesión y entra con el botón **Huella**. Debe probarse en un celular físico con una huella registrada.
