package com.rodapartes.app.ui.admin

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
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.Role
import com.rodapartes.app.data.User
import com.rodapartes.app.databinding.FragmentListBinding
import com.rodapartes.app.databinding.ItemUserBinding
import com.rodapartes.app.util.applyStatus
import com.rodapartes.app.util.initials
import com.rodapartes.app.util.visible

/** Gestión de usuarios: listado con búsqueda y filtro por rol, crear y editar (RF-ADU-01). */
class AdminUsersFragment : Fragment(R.layout.fragment_list) {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private var items: List<User> = emptyList()
    private val adapter = Adapter()
    private var roleFilter: Role? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListBinding.bind(view)
        binding.toolbar.visible(false) // el título lo pone la barra de AdminActivity

        binding.tilSearch.visible(true)
        binding.etSearch.hint = getString(R.string.users_search_hint)
        binding.etSearch.doAfterTextChanged { refresh() }

        val filters = listOf<Role?>(null) + Role.entries
        filters.forEachIndexed { i, role ->
            binding.chipFilters.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = role?.label ?: getString(R.string.filter_all)
                tag = i
                isCheckable = true
                isChecked = i == 0
            })
        }
        binding.chipFilters.setOnCheckedStateChangeListener { group, ids ->
            roleFilter = ids.firstOrNull()?.let { filters[group.findViewById<Chip>(it).tag as Int] }
            refresh()
        }

        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.fab.visible(true)
        binding.fab.contentDescription = getString(R.string.user_new_title)
        binding.fab.setOnClickListener { startActivity(Intent(requireContext(), UserFormActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val q = binding.etSearch.text?.toString()?.trim().orEmpty()
        items = MockData.users.filter {
            (roleFilter == null || it.role == roleFilter) &&
                (q.isEmpty() || it.name.contains(q, true) || it.email.contains(q, true) || it.document.contains(q, true))
        }
        adapter.notifyDataSetChanged()
        binding.empty.root.visible(items.isEmpty())
        binding.empty.ivEmpty.setImageResource(R.drawable.ic_group)
        binding.empty.tvEmptyTitle.text = getString(R.string.users_empty)
        binding.empty.tvEmptyBody.text = getString(R.string.users_empty_body)
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val b: ItemUserBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val u = items[position]
            with(holder.b) {
                tvInitials.text = initials(u.name)
                tvName.text = u.name
                tvInfo.text = getString(R.string.user_line, u.role.label, maskEmail(u.email))
                tvStatus.applyStatus(u.status)
                root.setOnClickListener {
                    startActivity(Intent(requireContext(), UserFormActivity::class.java)
                        .putExtra(UserFormActivity.EXTRA_ID, u.id))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Enmascara el correo en la lista: la***@correo.com */
        fun maskEmail(email: String): String {
            val at = email.indexOf('@')
            return if (at <= 2) email else email.take(2) + "***" + email.substring(at)
        }
    }
}
