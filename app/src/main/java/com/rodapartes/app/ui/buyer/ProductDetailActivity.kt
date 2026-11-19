package com.rodapartes.app.ui.buyer

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.rodapartes.app.R
import com.rodapartes.app.data.AppUser
import com.rodapartes.app.data.CartRepository
import com.rodapartes.app.data.Product
import com.rodapartes.app.data.ProductRepository
import com.rodapartes.app.data.UserRepository
import com.rodapartes.app.databinding.ActivityProductDetailBinding
import com.rodapartes.app.util.LocationHelper
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.bindProductImage
import com.rodapartes.app.util.color
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.snack
import kotlinx.coroutines.launch

/**
 * Detalle de un producto (leído de Firestore por su id) con selector de cantidad
 * y "Agregar al carrito" (RF-CAR-01). Tocar la foto pasa a la siguiente.
 */
class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private var product: Product? = null
    private var seller: AppUser? = null
    private var qty = 1L
    private var photoIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnAdd.isEnabled = false

        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        lifecycleScope.launch {
            val p = runCatching { ProductRepository.get(id) }.getOrNull()
            if (p == null) {
                binding.root.snack(getString(R.string.detail_not_found))
                return@launch
            }
            product = p
            bind(p)
            // Datos públicos de la tienda: identidad del vendedor antes de comprar (Ley 1480, art. 50)
            seller = runCatching { UserRepository.get(p.sellerId) }.getOrNull()
            bindSeller(p)
        }

        binding.stepper.btnMinus.setOnClickListener { if (qty > 1) { qty--; updateQty() } }
        binding.stepper.btnPlus.setOnClickListener {
            val stock = product?.stock ?: 0
            if (qty < stock) { qty++; updateQty() }
            else binding.root.snack(getString(R.string.cart_max_stock, stock))
        }
        binding.tile.setOnClickListener { nextPhoto() }
        binding.btnAdd.setOnClickListener { addToCart() }
        binding.btnReport.setOnClickListener { report() }
    }

    /** Pinta todos los datos del producto. */
    private fun bind(p: Product) = with(binding) {
        showPhoto(0)
        tvType.text = getString(if (p.original) R.string.type_original else R.string.type_generic)
        tvType.setTextColor(color(if (p.original) R.color.info else R.color.aviso))
        tvType.backgroundTintList = ColorStateList.valueOf(color(if (p.original) R.color.info_claro else R.color.aviso_claro))
        tvRef.text = if (p.ref.isBlank()) "" else getString(R.string.ref_label, p.ref)
        tvName.text = p.name
        tvPrice.text = Money.format(p.price)
        tvStock.text = if (p.stock > 0) getString(R.string.detail_units, p.stock) else getString(R.string.detail_out)
        tvWarranty.text = if (p.warrantyMonths > 0)
            resources.getQuantityString(R.plurals.months, p.warrantyMonths.toInt(), p.warrantyMonths)
        else getString(R.string.detail_no_warranty)
        tvCategory.text = p.categoryEnum.label
        tvDescription.text = p.description
        tvCompatList.text = getString(R.string.detail_fits_list, p.compatible.joinToString())

        // Compatibilidad con la moto guardada: verde si sirve, ámbar si no está confirmada
        val moto = Session.user?.moto.orEmpty()
        if (moto.isBlank()) {
            tvFit.text = getString(R.string.detail_no_moto)
        } else {
            tvFit.text = getString(if (p.fits(moto)) R.string.detail_fits else R.string.detail_not_fits, moto)
        }
        val fits = p.fits(moto)
        val fg = color(if (fits) R.color.exito else R.color.aviso)
        tvFit.setTextColor(fg)
        tvFit.backgroundTintList = ColorStateList.valueOf(color(if (fits) R.color.exito_claro else R.color.aviso_claro))
        TextViewCompat.setCompoundDrawableTintList(tvFit, ColorStateList.valueOf(fg))

        btnAdd.isEnabled = p.stock > 0
        if (p.stock == 0L) btnAdd.text = getString(R.string.detail_out)
        updateQty()
    }

    /** Nombre, NIT y ubicación de la tienda; con ubicación se puede abrir en Google Maps. */
    private fun bindSeller(p: Product) {
        val s = seller
        binding.tvSeller.text = s?.displayName ?: p.sellerName
        binding.tvSellerInfo.text = listOfNotNull(
            s?.document?.ifBlank { null },
            s?.storeAddress?.ifBlank { null }
        ).joinToString(" · ").ifBlank { getString(R.string.detail_seller_verified) }
        val lat = s?.storeLat
        val lng = s?.storeLng
        if (lat != null && lng != null) {
            binding.tvSellerInfo.setOnClickListener {
                LocationHelper.openInMaps(this, lat, lng, s?.displayName.orEmpty())
            }
        }
    }

    /** Muestra la foto número [index] y actualiza el contador "1 / 3". */
    private fun showPhoto(index: Int) {
        val p = product ?: return
        photoIndex = index
        bindProductImage(binding.tile, binding.ivIcon, binding.ivPhoto, p.categoryEnum, p.photos.getOrNull(index))
        binding.tvPhotos.text = if (p.photos.isEmpty()) getString(R.string.detail_no_photos)
        else getString(R.string.detail_photo_counter, index + 1, p.photos.size)
    }

    /** Pasa a la siguiente foto del producto. */
    private fun nextPhoto() {
        val count = product?.photos?.size ?: 0
        if (count > 1) showPhoto((photoIndex + 1) % count)
    }

    /** Actualiza la cantidad elegida y el subtotal. */
    private fun updateQty() {
        binding.stepper.tvQty.text = qty.toString()
    }

    /** Guarda el producto en carts/{uid}/items en Firestore. */
    private fun addToCart() {
        val p = product ?: return
        val user = Session.user
        if (user == null) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.guest_title)
                .setMessage(R.string.guest_message)
                .setPositiveButton(R.string.action_close, null)
                .show()
            return
        }
        binding.btnAdd.isEnabled = false
        lifecycleScope.safeLaunch(binding.root, onDone = { binding.btnAdd.isEnabled = true }) {
            CartRepository.add(user.uid, p, qty)
            Snackbar.make(binding.root, getString(R.string.detail_added, qty), Snackbar.LENGTH_LONG)
                .setAction(R.string.detail_go_cart) {
                    startActivity(BuyerActivity.intentForTab(this, R.id.nav_cart))
                    finish()
                }
                .show()
        }
    }

    /** Reportar publicación (imitaciones / información engañosa, RF-ADP-03). */
    private fun report() {
        val p = product ?: return
        if (Session.user == null) {
            binding.root.snack(getString(R.string.guest_message))
            return
        }
        val reasons = resources.getStringArray(R.array.motivos_rechazo)
        var selected = 2
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.detail_report)
            .setSingleChoiceItems(reasons, selected) { _, which -> selected = which }
            .setPositiveButton(R.string.action_send) { _, _ ->
                lifecycleScope.safeLaunch(binding.root) {
                    ProductRepository.report(p.id)
                    binding.root.snack(getString(R.string.detail_report_sent))
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "product_id"
    }
}
