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
import com.rodapartes.app.R
import com.rodapartes.app.data.CartItem
import com.rodapartes.app.data.MockData
import com.rodapartes.app.databinding.FragmentCartBinding
import com.rodapartes.app.databinding.ItemCartBinding
import com.rodapartes.app.ui.common.bindTile
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

/** Carrito: ver, cambiar cantidades, quitar productos y vaciar (CRUD de carrito). */
class CartFragment : Fragment(R.layout.fragment_cart) {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private val adapter = CartAdapter()

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
                    MockData.cart.clear()
                    refresh()
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
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val empty = MockData.cart.isEmpty()
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

        val sellers = MockData.cart.map { it.product.seller }.distinct()
        binding.tvSellerGroup.text = getString(R.string.cart_sold_by, sellers.joinToString())
        binding.tvSubtotal.text = Money.format(MockData.cartSubtotal())
        binding.tvShipping.text = Money.format(MockData.SHIPPING)
        binding.tvTotal.text = Money.format(MockData.cartTotal())
        (activity as? BuyerActivity)?.updateCartBadge()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Adaptador interno del carrito con el selector de cantidad. */
    private inner class CartAdapter : RecyclerView.Adapter<CartAdapter.VH>() {

        inner class VH(val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = MockData.cart.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item: CartItem = MockData.cart[position]
            with(holder.b) {
                bindTile(tile, ivIcon, item.product)
                tvName.text = item.product.name
                tvUnit.text = getString(R.string.cart_unit_price, Money.format(item.product.price))
                tvSubtotal.text = Money.format(item.subtotal)
                stepper.tvQty.text = item.qty.toString()

                stepper.btnPlus.setOnClickListener {
                    if (item.qty < item.product.stock) {
                        item.qty++
                        refresh()
                    } else {
                        root.snack(getString(R.string.cart_max_stock, item.product.stock))
                    }
                }
                stepper.btnMinus.setOnClickListener {
                    if (item.qty > 1) {
                        item.qty--
                        refresh()
                    } else remove(item)
                }
                btnDelete.setOnClickListener { remove(item) }
            }
        }

        private fun remove(item: CartItem) {
            MockData.cart.remove(item)
            refresh()
            binding.root.snack(getString(R.string.cart_removed, item.product.name))
        }
    }
}
