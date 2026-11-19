package com.rodapartes.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.R
import com.rodapartes.app.data.CartItem
import com.rodapartes.app.data.CartRepository
import com.rodapartes.app.data.Pricing
import com.rodapartes.app.databinding.FragmentCartBinding
import com.rodapartes.app.databinding.ItemCartBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.bindProductImage
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.viewScope
import com.rodapartes.app.util.visible

/**
 * Carrito del comprador (CRUD sobre carts/{uid}/items en Firestore, RF-CAR):
 * leer en tiempo real, cambiar cantidades, quitar productos y vaciar.
 */
class CartFragment : Fragment(R.layout.fragment_cart) {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private val adapter = CartAdapter()
    private var items: List<CartItem> = emptyList()
    private var listener: ListenerRegistration? = null
    private val uid get() = Session.user?.uid.orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCartBinding.bind(view)

        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = adapter

        binding.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cart_clear_title)
                .setMessage(R.string.cart_clear_msg)
                .setPositiveButton(R.string.cart_clear) { _, _ ->
                    viewScope.safeLaunch(binding.root) { CartRepository.clear(uid) }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
        binding.btnCheckout.setOnClickListener {
            startActivity(Intent(requireContext(), CheckoutActivity::class.java))
        }
        binding.empty.btnEmptyAction.setOnClickListener {
            (requireActivity() as BuyerActivity).openTab(R.id.nav_search)
        }

        listener = CartRepository.listen(uid) { list ->
            items = list
            if (_binding != null) render()
        }
    }

    /** Dibuja la lista y el resumen con los datos que llegan de Firestore. */
    private fun render() {
        val empty = items.isEmpty()
        binding.groupContent.visible(!empty)
        binding.empty.root.visible(empty)
        if (empty) {
            binding.empty.ivEmpty.setImageResource(R.drawable.ic_cart)
            binding.empty.tvEmptyTitle.text = getString(R.string.cart_empty_title)
            binding.empty.tvEmptyBody.text = getString(R.string.cart_empty_body)
            binding.empty.btnEmptyAction.text = getString(R.string.cart_empty_action)
            binding.empty.btnEmptyAction.visible(true)
        }
        adapter.notifyDataSetChanged()

        val sellers = items.map { it.sellerName }.distinct()
        binding.tvSellerGroup.text = getString(R.string.cart_sold_by, sellers.joinToString())
        binding.tvSubtotal.text = Money.format(Pricing.subtotal(items))
        binding.tvShipping.text = Money.format(Pricing.shipping(items))
        binding.tvTotal.text = Money.format(Pricing.total(items))
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }

    /** Adaptador del carrito con el selector de cantidad. Cada cambio se guarda en Firestore. */
    private inner class CartAdapter : RecyclerView.Adapter<CartAdapter.VH>() {

        /** Contenedor de las vistas de un producto del carrito. */
        inner class VH(val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            with(holder.b) {
                bindProductImage(tile, ivIcon, ivPhoto, item.categoryEnum, item.photo)
                tvName.text = item.name
                tvUnit.text = getString(R.string.cart_unit_price, Money.format(item.price))
                tvSubtotal.text = Money.format(item.subtotal)
                stepper.tvQty.text = item.qty.toString()

                stepper.btnPlus.setOnClickListener {
                    if (item.qty < item.stock) update(item, item.qty + 1)
                    else root.snack(getString(R.string.cart_max_stock, item.stock))
                }
                stepper.btnMinus.setOnClickListener {
                    if (item.qty > 1) update(item, item.qty - 1) else remove(item)
                }
                btnDelete.setOnClickListener { remove(item) }
            }
        }

        /** Cambia la cantidad en Firestore (RF-CAR-03). */
        private fun update(item: CartItem, qty: Long) {
            viewScope.safeLaunch(binding.root) { CartRepository.updateQty(uid, item.productId, qty) }
        }

        /** Quita el producto del carrito en Firestore (RF-CAR-04). */
        private fun remove(item: CartItem) {
            viewScope.safeLaunch(binding.root) {
                CartRepository.remove(uid, item.productId)
                binding.root.snack(getString(R.string.cart_removed, item.name))
            }
        }
    }
}
