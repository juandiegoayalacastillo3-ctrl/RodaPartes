package com.rodapartes.app.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.Product
import com.rodapartes.app.data.ProductStatus
import com.rodapartes.app.databinding.FragmentListBinding
import com.rodapartes.app.databinding.ItemSellerProductBinding
import com.rodapartes.app.ui.common.bindTile
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

/** "Mis productos": listar, buscar, filtrar, crear, editar y eliminar (CRUD, RF-PRO). */
class SellerProductsFragment : Fragment(R.layout.fragment_list) {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var items: List<Product> = emptyList()
    private val adapter = Adapter()
    private var filter = 0 // 0 todos, 1 publicados, 2 en revisión/borrador, 3 sin stock

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListBinding.bind(view)
        binding.toolbar.title = getString(R.string.seller_products_title)

        binding.tilSearch.visible(true)
        binding.etSearch.hint = getString(R.string.seller_search_hint)
        binding.etSearch.doAfterTextChanged { refresh() }

        listOf(R.string.filter_all, R.string.filter_published, R.string.filter_review, R.string.filter_no_stock)
            .forEachIndexed { i, label ->
                binding.chipFilters.addView(Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = getString(label)
                    tag = i
                    isCheckable = true
                    isChecked = i == 0
                })
            }
        binding.chipFilters.setOnCheckedStateChangeListener { group, ids ->
            filter = ids.firstOrNull()?.let { group.findViewById<Chip>(it).tag as Int } ?: 0
            refresh()
        }

        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.fab.visible(true)
        binding.fab.contentDescription = getString(R.string.form_new_title)
        binding.fab.setOnClickListener { startActivity(Intent(requireContext(), ProductFormActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val q = binding.etSearch.text?.toString()?.trim().orEmpty()
        items = MockData.sellerProducts().filter { p ->
            val byFilter = when (filter) {
                1 -> p.status == ProductStatus.PUBLICADO
                2 -> p.status == ProductStatus.EN_REVISION || p.status == ProductStatus.BORRADOR
                3 -> p.stock == 0
                else -> true
            }
            byFilter && (q.isEmpty() || p.name.contains(q, true) || p.ref.contains(q, true))
        }
        adapter.notifyDataSetChanged()
        binding.empty.root.visible(items.isEmpty())
        binding.empty.ivEmpty.setImageResource(R.drawable.ic_box)
        binding.empty.tvEmptyTitle.text = getString(R.string.seller_products_empty)
        binding.empty.tvEmptyBody.text = getString(R.string.seller_products_empty_body)
    }

    private fun edit(p: Product) {
        startActivity(Intent(requireContext(), ProductFormActivity::class.java)
            .putExtra(ProductFormActivity.EXTRA_ID, p.id))
    }

    /** Eliminar con confirmación. Si tuviera compras se haría borrado lógico (RF-PRO-06). */
    private fun confirmDelete(p: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_product_title)
            .setMessage(getString(R.string.delete_product_msg, p.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                MockData.products.remove(p)
                refresh()
                binding.root.snack(getString(R.string.deleted_ok, p.name))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val b: ItemSellerProductBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemSellerProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            with(holder.b) {
                bindTile(tile, ivIcon, p)
                tvName.text = p.name
                tvInfo.text = getString(R.string.seller_product_info, Money.format(p.price), p.stock)
                tvStatus.applyStatus(p.status)
                tvLowStock.visible(p.lowStock)
                root.setOnClickListener { edit(p) }
                btnEdit.setOnClickListener { edit(p) }
                btnDelete.setOnClickListener { confirmDelete(p) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
