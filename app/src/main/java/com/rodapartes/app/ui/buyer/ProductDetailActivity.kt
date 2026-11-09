package com.rodapartes.app.ui.buyer

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.Product
import com.rodapartes.app.databinding.ActivityProductDetailBinding
import com.rodapartes.app.ui.common.bindTile
import com.rodapartes.app.ui.common.km
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.color
import com.rodapartes.app.util.snack

/** Detalle de un producto con selector de cantidad y "Agregar al carrito" (RF-CAR-01). */
class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var product: Product
    private var qty = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        product = MockData.product(intent.getIntExtra(EXTRA_ID, 1))
        bind()

        binding.stepper.btnMinus.setOnClickListener { if (qty > 1) { qty--; updateQty() } }
        binding.stepper.btnPlus.setOnClickListener {
            if (qty < product.stock) { qty++; updateQty() }
            else binding.root.snack(getString(R.string.cart_max_stock, product.stock))
        }
        binding.btnAdd.setOnClickListener { addToCart() }
        binding.btnReport.setOnClickListener { report() }
    }

    private fun bind() = with(binding) {
        bindTile(tile, ivIcon, product)
        tvPhotos.text = getString(R.string.detail_photos, product.photos)
        tvType.text = getString(if (product.original) R.string.type_original else R.string.type_generic)
        tvType.setTextColor(color(if (product.original) R.color.info else R.color.aviso))
        tvType.backgroundTintList = ColorStateList.valueOf(color(if (product.original) R.color.info_claro else R.color.aviso_claro))
        tvRef.text = getString(R.string.ref_label, product.ref)
        tvName.text = product.name
        tvPrice.text = Money.format(product.price)
        tvStock.text = if (product.stock > 0) getString(R.string.detail_units, product.stock) else getString(R.string.detail_out)
        tvWarranty.text = if (product.warrantyMonths > 0)
            resources.getQuantityString(R.plurals.months, product.warrantyMonths, product.warrantyMonths)
        else getString(R.string.detail_no_warranty)
        tvCategory.text = product.category.label
        tvDescription.text = product.description
        tvCompatList.text = getString(R.string.detail_fits_list, product.compatible.joinToString())
        tvSeller.text = product.seller
        tvSellerInfo.text = getString(R.string.detail_seller_info, sellerNit(product.seller), km(product.distanceKm))

        // Compatibilidad con la moto guardada: verde si sirve, ámbar si no está confirmada
        val fits = product.fits(MockData.myMoto)
        val fg = color(if (fits) R.color.exito else R.color.aviso)
        tvFit.text = getString(if (fits) R.string.detail_fits else R.string.detail_not_fits, MockData.myMoto)
        tvFit.setTextColor(fg)
        tvFit.backgroundTintList = ColorStateList.valueOf(color(if (fits) R.color.exito_claro else R.color.aviso_claro))
        TextViewCompat.setCompoundDrawableTintList(tvFit, ColorStateList.valueOf(fg))

        if (product.stock == 0) {
            btnAdd.isEnabled = false
            btnAdd.text = getString(R.string.detail_out)
        }
        updateQty()
    }

    private fun updateQty() {
        binding.stepper.tvQty.text = qty.toString()
    }

    private fun addToCart() {
        if (!Session(this).loggedIn) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.guest_title)
                .setMessage(R.string.guest_message)
                .setPositiveButton(R.string.action_close, null)
                .show()
            return
        }
        MockData.addToCart(product, qty)
        Snackbar.make(binding.root, getString(R.string.detail_added, qty), Snackbar.LENGTH_LONG)
            .setAction(R.string.detail_go_cart) {
                startActivity(BuyerActivity.intentForTab(this, R.id.nav_cart))
                finish()
            }
            .show()
    }

    /** Reportar publicación (imitaciones / información engañosa, RF-ADP-03). */
    private fun report() {
        val reasons = resources.getStringArray(R.array.motivos_rechazo)
        var selected = 2
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.detail_report)
            .setSingleChoiceItems(reasons, selected) { _, which -> selected = which }
            .setPositiveButton(R.string.action_send) { _, _ ->
                product.reports++
                binding.root.snack(getString(R.string.detail_report_sent))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "product_id"

        /** NIT de las tiendas de prueba (identidad del vendedor, Ley 1480). */
        fun sellerNit(seller: String) = when (seller) {
            "MotoCentro 80" -> "NIT 901.234.567-8"
            "Repuestos La 68" -> "NIT 900.112.334-1"
            "Electro Motos Suba" -> "NIT 901.445.902-6"
            "Llantas Express" -> "NIT 900.555.321-4"
            else -> "NIT 901.876.110-2"
        }
    }
}
