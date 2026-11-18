package com.rodapartes.app.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rodapartes.app.R
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.Product
import com.rodapartes.app.databinding.ItemOrderBinding
import com.rodapartes.app.databinding.ItemProductGridBinding
import com.rodapartes.app.util.Dates
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.bindProductImage
import com.rodapartes.app.util.visible

/**
 * Grilla de productos del catálogo (comprador).
 * @param myMoto moto guardada en el perfil para marcar los productos compatibles.
 */
class ProductGridAdapter(
    private var items: List<Product>,
    private var myMoto: String,
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductGridAdapter.VH>() {

    class VH(val b: ItemProductGridBinding) : RecyclerView.ViewHolder(b.root)

    /** Reemplaza la lista (llega del listener de Firestore). */
    fun submit(newItems: List<Product>, moto: String = myMoto) {
        items = newItems
        myMoto = moto
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val ctx = holder.itemView.context
        with(holder.b) {
            bindProductImage(tile, ivIcon, ivPhoto, p.categoryEnum, p.mainPhoto)
            tvType.text = ctx.getString(if (p.original) R.string.type_original else R.string.type_generic_short)
            tvName.text = p.name
            tvPrice.text = Money.format(p.price)
            tvSeller.text = if (p.stock > 0) p.sellerName else ctx.getString(R.string.seller_out_of_stock, p.sellerName)
            tvCompatible.visible(p.fits(myMoto))
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
        holder.b.bindOrder(o, showBuyer)
        holder.b.root.setOnClickListener { onClick(o) }
    }
}

/** Llena la tarjeta de una orden (se reutiliza en listas y en los paneles). */
fun ItemOrderBinding.bindOrder(o: Order, showBuyer: Boolean) {
    val ctx = root.context
    tvCode.text = ctx.getString(R.string.order_code, o.code)
    tvStatus.applyStatus(o.statusEnum)
    tvParties.text = if (showBuyer) ctx.getString(R.string.order_parties, o.buyerName, o.sellerName) else o.sellerName
    tvSummary.text = o.summary
    tvDate.text = ctx.resources.getQuantityString(
        R.plurals.order_date_items, o.itemCount, Dates.short(o.createdAt?.toDate()), o.itemCount
    )
    tvTotal.text = Money.format(o.total)
}
