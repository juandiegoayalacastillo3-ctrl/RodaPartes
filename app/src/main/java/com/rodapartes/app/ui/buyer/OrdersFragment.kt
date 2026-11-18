package com.rodapartes.app.ui.buyer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.google.firebase.firestore.ListenerRegistration
import com.rodapartes.app.data.OrderRepository
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.viewScope
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.databinding.FragmentListBinding
import com.rodapartes.app.ui.common.OrderAdapter
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

/** "Mis compras" del comprador (RF-COM-02) con solicitud de retracto (RF-COM-04). */
class OrdersFragment : Fragment(R.layout.fragment_list) {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OrderAdapter
    private var filter = 0 // 0 todas, 1 en curso, 2 entregadas
    private var orders: List<Order> = emptyList()
    private var listener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListBinding.bind(view)
        binding.toolbar.title = getString(R.string.nav_orders)

        listOf(R.string.filter_all, R.string.filter_in_progress, R.string.filter_delivered)
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

        adapter = OrderAdapter(emptyList(), showBuyer = false) { showDetail(it) }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        // Historial del comprador en tiempo real (orders donde buyerId = uid)
        listener = OrderRepository.listenByBuyer(Session.user?.uid.orEmpty()) { list ->
            orders = list
            if (_binding != null) refresh()
        }
    }

    /** Aplica el filtro de estado a las órdenes recibidas. */
    private fun refresh() {
        val list = orders.filter {
            when (filter) {
                1 -> it.statusEnum in listOf(OrderStatus.NUEVO, OrderStatus.PREPARACION, OrderStatus.ENVIADO)
                2 -> it.statusEnum == OrderStatus.ENTREGADO
                else -> true
            }
        }
        adapter.submit(list)
        binding.empty.root.visible(list.isEmpty())
        binding.empty.ivEmpty.setImageResource(R.drawable.ic_bag)
        binding.empty.tvEmptyTitle.text = getString(R.string.orders_empty_title)
        binding.empty.tvEmptyBody.text = getString(R.string.orders_empty_body)
    }

    /** Detalle de la compra; si ya fue enviada o entregada permite pedir el retracto (RF-COM-04). */
    private fun showDetail(order: Order) {
        val canRetract = order.statusEnum == OrderStatus.ENTREGADO || order.statusEnum == OrderStatus.ENVIADO
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.order_code, order.code))
            .setMessage(getString(R.string.order_detail_msg, order.sellerName, order.summary,
                Money.format(order.total), order.statusEnum.label, order.address, order.paymentRef))
            .setPositiveButton(R.string.action_close, null)
        if (canRetract) {
            dialog.setNeutralButton(R.string.order_retract) { _, _ ->
                viewScope.safeLaunch(binding.root) {
                    OrderRepository.setStatus(order.id, OrderStatus.RETRACTO)
                    binding.root.snack(getString(R.string.order_retract_sent))
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        listener?.remove()
        super.onDestroyView()
        _binding = null
    }
}
