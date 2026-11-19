package com.rodapartes.app.ui.seller

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
import com.rodapartes.app.data.OrderRepository
import com.rodapartes.app.util.LocationHelper
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.viewScope
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.databinding.FragmentListBinding
import com.rodapartes.app.databinding.ItemSellerOrderBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

/** Historial de pedidos recibidos con cambio de estado (RF-COM-02, RF-COM-03). */
class SellerOrdersFragment : Fragment(R.layout.fragment_list) {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var items: List<Order> = emptyList()
    private val adapter = Adapter()
    private var filter: OrderStatus? = null
    private var all: List<Order> = emptyList()
    private var listener: ListenerRegistration? = null

    private val tabs = listOf(null, OrderStatus.NUEVO, OrderStatus.PREPARACION, OrderStatus.ENVIADO, OrderStatus.ENTREGADO)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListBinding.bind(view)
        binding.toolbar.title = getString(R.string.seller_orders_title)

        tabs.forEachIndexed { i, status ->
            binding.chipFilters.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = status?.label ?: getString(R.string.filter_all)
                tag = i
                isCheckable = true
                isChecked = i == 0
            })
        }
        binding.chipFilters.setOnCheckedStateChangeListener { group, ids ->
            filter = ids.firstOrNull()?.let { tabs[group.findViewById<Chip>(it).tag as Int] }
            refresh()
        }

        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        // Pedidos recibidos por la tienda en tiempo real (orders donde sellerId = uid)
        listener = OrderRepository.listenBySeller(Session.user?.uid.orEmpty()) { list ->
            all = list
            if (_binding != null) refresh()
        }
    }

    /** Aplica el filtro de estado a los pedidos recibidos. */
    private fun refresh() {
        items = all.filter { filter == null || it.statusEnum == filter }
        adapter.notifyDataSetChanged()
        binding.empty.root.visible(items.isEmpty())
        binding.empty.ivEmpty.setImageResource(R.drawable.ic_receipt)
        binding.empty.tvEmptyTitle.text = getString(R.string.seller_orders_empty)
        binding.empty.tvEmptyBody.text = getString(R.string.seller_orders_empty_body)
    }

    /** Guarda el nuevo estado en Firestore; el comprador lo ve al instante en "Mis compras". */
    private fun changeStatus(order: Order, status: OrderStatus) {
        viewScope.safeLaunch(binding.root) {
            OrderRepository.setStatus(order.id, status)
            binding.root.snack(getString(R.string.order_status_changed, order.code, status.label))
        }
    }

    /** Rechazar un pedido nuevo lo deja CANCELADO (el reembolso se gestiona con Wompi). */
    private fun reject(order: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.order_reject_title)
            .setMessage(getString(R.string.order_reject_msg, order.code))
            .setPositiveButton(R.string.action_reject) { _, _ -> changeStatus(order, OrderStatus.CANCELADO) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Adaptador de la lista de pedidos recibidos. */
    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        /** Contenedor de las vistas de una fila. */
        inner class VH(val b: ItemSellerOrderBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemSellerOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val o = items[position]
            with(holder.b) {
                tvCode.text = getString(R.string.order_code, o.code)
                tvStatus.applyStatus(o.statusEnum)
                tvBuyer.text = resources.getQuantityString(R.plurals.seller_order_line, o.itemCount, o.buyerName, o.itemCount, Money.format(o.total))
                tvSummary.text = o.summary
                tvAddress.text = listOf(o.address, o.addressDetail).filter { it.isNotBlank() }.joinToString(" · ")
                // Con coordenadas, tocar la dirección abre Google Maps para llevar el pedido
                val lat = o.lat
                val lng = o.lng
                tvAddress.setOnClickListener {
                    if (lat != null && lng != null) LocationHelper.openInMaps(requireContext(), lat, lng, o.buyerName)
                }

                groupNew.visible(o.statusEnum == OrderStatus.NUEVO)
                val next = o.statusEnum.next()
                btnNext.visible(o.statusEnum == OrderStatus.PREPARACION || o.statusEnum == OrderStatus.ENVIADO)
                btnNext.text = getString(R.string.order_mark_as, next.label.lowercase())

                btnAccept.setOnClickListener { changeStatus(o, OrderStatus.PREPARACION) }
                btnReject.setOnClickListener { reject(o) }
                btnNext.setOnClickListener { changeStatus(o, next) }
            }
        }
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }
}
