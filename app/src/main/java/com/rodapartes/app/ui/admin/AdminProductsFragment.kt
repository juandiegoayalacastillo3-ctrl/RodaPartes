package com.rodapartes.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.data.AuditRepository
import com.rodapartes.app.data.ProductRepository
import com.rodapartes.app.util.bindProductImage
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.viewScope
import com.rodapartes.app.data.Product
import com.rodapartes.app.data.ProductStatus
import com.rodapartes.app.databinding.FragmentListBinding
import com.rodapartes.app.databinding.ItemModerationBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

/** Gestión de productos: moderar publicaciones y atender reportes (RF-ADP-01, RF-ADP-03). */
class AdminProductsFragment : Fragment(R.layout.fragment_list) {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var items: List<Product> = emptyList()
    private val adapter = Adapter()
    private var tab = 0 // 0 por revisar, 1 reportados, 2 publicados, 3 rechazados
    private var all: List<Product> = emptyList()
    private var listener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListBinding.bind(view)
        binding.toolbar.visible(false)

        listOf(R.string.filter_to_review, R.string.filter_reported, R.string.filter_published, R.string.filter_rejected)
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
            tab = ids.firstOrNull()?.let { group.findViewById<Chip>(it).tag as Int } ?: 0
            refresh()
        }

        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        // Todas las publicaciones en tiempo real (colección products)
        listener = ProductRepository.listenAll { list ->
            all = list
            if (_binding != null) refresh()
        }
    }

    /** Filtra por pestaña: por revisar, reportados, publicados o rechazados. */
    private fun refresh() {
        items = all.filter {
            when (tab) {
                1 -> it.reports > 0
                2 -> it.statusEnum == ProductStatus.PUBLICADO && it.reports == 0L
                3 -> it.statusEnum == ProductStatus.RECHAZADO
                else -> it.statusEnum == ProductStatus.EN_REVISION
            }
        }
        adapter.notifyDataSetChanged()
        binding.empty.root.visible(items.isEmpty())
        binding.empty.ivEmpty.setImageResource(R.drawable.ic_check)
        binding.empty.tvEmptyTitle.text = getString(R.string.moderation_empty)
        binding.empty.tvEmptyBody.text = getString(R.string.moderation_empty_body)
    }

    /** Aprueba la publicación: queda visible en el catálogo (RF-ADP-01). */
    private fun approve(p: Product) {
        viewScope.safeLaunch(binding.root) {
            ProductRepository.setStatus(p.id, ProductStatus.PUBLICADO)
            AuditRepository.log("Aprobó «${p.name}» de ${p.sellerName}")
            binding.root.snack(getString(R.string.moderation_approved, p.name))
        }
    }

    /** Rechazar o retirar pide un motivo; el vendedor recibe la notificación. */
    private fun reject(p: Product) {
        val reasons = resources.getStringArray(R.array.motivos_rechazo)
        var selected = if (p.reports > 0) 2 else 0
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (p.reports > 0) R.string.moderation_remove_title else R.string.moderation_reject_title)
            .setSingleChoiceItems(reasons, selected) { _, which -> selected = which }
            .setPositiveButton(R.string.action_reject) { _, _ ->
                val reason = reasons[selected]
                viewScope.safeLaunch(binding.root) {
                    ProductRepository.setStatus(p.id, ProductStatus.RECHAZADO, reason)
                    AuditRepository.log("Rechazó «${p.name}» de ${p.sellerName}: $reason")
                    binding.root.snack(getString(R.string.moderation_rejected, reason))
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val b: ItemModerationBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemModerationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            with(holder.b) {
                bindProductImage(tile, ivIcon, ivPhoto, p.categoryEnum, p.mainPhoto)
                tvStatus.applyStatus(p.statusEnum)
                tvName.text = p.name
                tvInfo.text = getString(R.string.moderation_info, p.sellerName, Money.format(p.price), p.categoryEnum.label,
                    getString(if (p.original) R.string.type_original else R.string.type_generic_short))
                tvReport.visible(p.reports > 0)
                tvReport.text = resources.getQuantityString(R.plurals.reports, p.reports.toInt(), p.reports)
                groupActions.visible(p.statusEnum == ProductStatus.EN_REVISION || p.reports > 0)
                btnApprove.text = getString(if (p.reports > 0) R.string.moderation_keep else R.string.action_approve)
                btnReject.text = getString(if (p.reports > 0) R.string.moderation_remove else R.string.action_reject)
                btnApprove.setOnClickListener { approve(p) }
                btnReject.setOnClickListener { reject(p) }
            }
        }
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }
}
