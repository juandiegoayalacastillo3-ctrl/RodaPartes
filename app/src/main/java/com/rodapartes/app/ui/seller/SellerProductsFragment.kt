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
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.data.ProductRepository
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.bindProductImage
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.viewScope
import com.rodapartes.app.data.Product
import com.rodapartes.app.data.ProductStatus
import com.rodapartes.app.databinding.FragmentListBinding
import com.rodapartes.app.databinding.ItemSellerProductBinding
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
    private var all: List<Product> = emptyList()
    private var listener: ListenerRegistration? = null

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

        // Inventario de la tienda en tiempo real (products donde sellerId = uid)
        listener = ProductRepository.listenBySeller(Session.user?.uid.orEmpty()) { list ->
            all = list
            if (_binding != null) refresh()
        }
    }

    /** Aplica la búsqueda y el filtro de estado al inventario recibido. */
    private fun refresh() {
        val q = binding.etSearch.text?.toString()?.trim().orEmpty()
        items = all.filter { p ->
            val byFilter = when (filter) {
                1 -> p.statusEnum == ProductStatus.PUBLICADO
                2 -> p.statusEnum == ProductStatus.EN_REVISION || p.statusEnum == ProductStatus.BORRADOR
                3 -> p.stock == 0L
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

    /** Abre el formulario en modo edición. */
    private fun edit(p: Product) {
        startActivity(Intent(requireContext(), ProductFormActivity::class.java)
            .putExtra(ProductFormActivity.EXTRA_ID, p.id))
    }

    /** Eliminar con confirmación. Borra el documento y sus fotos de Storage (RF-PRO-06); las órdenes conservan su copia. */
    private fun confirmDelete(p: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_product_title)
            .setMessage(getString(R.string.delete_product_msg, p.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewScope.safeLaunch(binding.root) {
                    ProductRepository.delete(p)
                    binding.root.snack(getString(R.string.deleted_ok, p.name))
                }
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
                bindProductImage(tile, ivIcon, ivPhoto, p.categoryEnum, p.mainPhoto)
                tvName.text = p.name
                tvInfo.text = getString(R.string.seller_product_info, Money.format(p.price), p.stock)
                tvStatus.applyStatus(p.statusEnum)
                tvLowStock.visible(p.lowStock)
                root.setOnClickListener { edit(p) }
                btnEdit.setOnClickListener { edit(p) }
                btnDelete.setOnClickListener { confirmDelete(p) }
            }
        }
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }
}
