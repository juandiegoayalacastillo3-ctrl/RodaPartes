# RodaPartes · Frontend Android

Tienda virtual Android de repuestos y accesorios para motocicletas.
Actividad 3 de **Desarrollo de Aplicaciones Móviles** (Fundación Universitaria Compensar) — interfaces gráficas de los tres roles definidos en la Actividad 2.

**Autor:** Juan Diego Ayala Castillo

## Tecnologías

- Kotlin + layouts XML (ViewBinding)
- Material Components 3 con tema propio (sin colores por defecto)
- `minSdk 26` (Android 8.0) · `targetSdk 34`
- AndroidX Biometric (`BiometricPrompt`, clase `BIOMETRIC_STRONG`)
- Corrutinas (`lifecycleScope`) para splash, contadores y simulación del pago

## Cómo ejecutarlo

1. Abrir la carpeta en Android Studio (Koala o superior) y esperar el *Gradle Sync*.
2. Ejecutar en un emulador o celular con Android 8.0+.
3. **Generar el APK:** `Build > Build App Bundle(s) / APK(s) > Build APK(s)`. Queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Cuentas de prueba

El rol se elige según el correo (en producción vendrá del *custom claim* del token de Firebase). Contraseña: cualquiera de 8 o más caracteres con letras y números, por ejemplo `moto2026`.

| Rol | Correo |
|---|---|
| Comprador | `comprador@rodapartes.co` |
| Vendedor | `vendedor@rodapartes.co` |
| Administrador | `admin@rodapartes.co` |

Para probar la huella: iniciar sesión como comprador → Perfil → activar "Ingreso con huella" (en el emulador primero registrar una huella en *Ajustes > Seguridad*). Luego cerrar sesión y usar el botón **Huella** del login.

## Pantallas por rol

| Rol | Pantalla | Archivo de layout |
|---|---|---|
| Transversal | Splash screen | `activity_splash.xml` |
| | Inicio de sesión (correo, Google, huella) | `activity_login.xml` |
| | Registro con elección de rol | `activity_register.xml` |
| | Recuperar contraseña | `activity_recover_password.xml` |
| | Menús de navegación | `menu/bottom_nav_*.xml`, `menu/drawer_admin.xml` |
| Comprador | Inicio | `fragment_home.xml` |
| | Catálogo (Buscar) | `fragment_catalog.xml` |
| | Detalle de producto | `activity_product_detail.xml` |
| | Carrito | `fragment_cart.xml` |
| | Entrega (checkout) | `activity_checkout.xml` |
| | Pasarela de pagos | `activity_payment.xml` |
| | Mis compras | `fragment_list.xml` (OrdersFragment) |
| | Perfil y edición | `fragment_profile.xml`, `activity_edit_profile.xml` |
| Vendedor | Panel de ventas | `fragment_seller_dashboard.xml` |
| | Mis productos (CRUD) | `fragment_list.xml`, `activity_product_form.xml` |
| | Cámara / escáner | `activity_camera.xml` |
| | Pedidos recibidos | `fragment_list.xml` (SellerOrdersFragment) |
| | Mi tienda | `fragment_seller_profile.xml` |
| Administrador | Dashboard | `fragment_admin_dashboard.xml` |
| | Usuarios (listar, crear, editar, eliminar) | `fragment_list.xml`, `activity_user_form.xml` |
| | Productos (moderación y reportes) | `fragment_list.xml` (AdminProductsFragment) |
| | Reporte de ventas | `fragment_admin_sales.xml` |

## Estructura

```
app/src/main/
├── java/com/rodapartes/app/
│   ├── data/          Modelos y datos de prueba en memoria (MockData)
│   ├── util/          Sesión, formato de pesos, navegación por rol, huella
│   └── ui/
│       ├── auth/      Splash, login, registro, recuperar contraseña
│       ├── buyer/     Pantallas del comprador
│       ├── seller/    Pantallas del vendedor + cámara
│       ├── admin/     Pantallas del administrador
│       └── common/    Adaptadores compartidos y BarChartView (vista propia)
└── res/
    ├── values/        colors, dimens, strings, styles, themes, arrays, attrs
    ├── values-v31/    Splash del sistema en Android 12+
    ├── color/         Selectores de color por estado (chips, navegación, campos)
    ├── drawable/      Íconos vectoriales, formas y degradados
    ├── anim/          Animaciones de la splash
    ├── menu/          Bottom Navigation de cada rol y Navigation Drawer del admin
    └── layout/        Activities, fragments, ítems de lista y vistas reutilizables
```

## Decisiones de diseño

- **Un color por rol** (igual que en los mockups): naranja `#E8590C` comprador, verde azulado `#0E8A7B` vendedor y azul pizarra `#34466B` administrador. Cada rol tiene su tema (`Theme.RodaPartes.Buyer/Seller/Admin`) que solo cambia `colorPrimary`; los layouts usan `?attr/colorPrimary`, por eso una misma pantalla (por ejemplo `fragment_list.xml`) toma el color del rol que la abre.
- **Tipografía:** títulos en `sans-serif-condensed` (estilo de catálogo técnico), cuerpo en `sans-serif` y referencias de piezas en `monospace`.
- **Fotos de productos:** en esta fase son degradados por categoría con un ícono (`bg_tile_*.xml` + `ic_cat_*.xml`). Las fotos reales llegan de Cloud Storage en la fase de backend.
- Lo que depende de servicios externos (Firebase, Wompi, Google Maps, CameraX/ML Kit) tiene la interfaz completa y una simulación para poder navegar; se conecta en la siguiente actividad.
