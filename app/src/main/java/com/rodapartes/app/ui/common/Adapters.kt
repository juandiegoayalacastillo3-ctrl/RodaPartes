package com.rodapartes.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.Product
import com.rodapartes.app.databinding.ItemOrderBinding
import com.rodapartes.app.databinding.ItemProductGridBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.visible
import java.util.Locale

/** Pinta la "foto" de un producto: degradado de su categoría + ícono. */
fun bindTile(tile: View, icon: ImageView, product: Product) {
    tile.setBackgroundResource(product.category.tileRes)
    icon.setImageResource(product.category.iconRes)
}

fun km(value: Double): String = String.format(Locale("es", "CO"), "%.1f km", value)

/** Grilla de productos del catálogo (comprador). */
class ProductGridAdapter(
    private var items: List<Product>,
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductGridAdapter.VH>() {

    class VH(val b: ItemProductGridBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(newItems: List<Product>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val ctx = holder.itemView.context
        with(holder.b) {
            bindTile(tile, ivIcon, p)
            tvType.text = ctx.getString(if (p.original) R.string.type_original else R.string.type_generic_short)
            tvName.text = p.name
            tvPrice.text = Money.format(p.price)
            tvSeller.text = ctx.getString(R.string.seller_distance, p.seller, km(p.distanceKm))
            tvCompatible.visible(p.fits(MockData.myMoto))
            root.contentDescription = "${p.name}, ${Money.format(p.price)}"
            root.setOnClickListener { onClick(p) }
        }
    }
}

/** Lista de órdenes: compras del comprador o ventas para el administrador. */
class OrderAdapter(
    private var items: List<Order>,
    private val showBuyer: Boolean,
    private val onClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.VH>() {

    class VH(val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(newItems: List<Order>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val o = items[position]
        val ctx = holder.itemView.context
        with(holder.b) {
            tvCode.text = ctx.getString(R.string.order_code, o.code)
            tvStatus.applyStatus(o.status)
            tvParties.text = if (showBuyer) ctx.getString(R.string.order_parties, o.buyer, o.seller) else o.seller
            tvSummary.text = o.summary
            tvDate.text = ctx.resources.getQuantityString(R.plurals.order_date_items, o.items, o.date, o.items)
            tvTotal.text = Money.format(o.total)
            root.setOnClickListener { onClick(o) }
        }
    }
}
