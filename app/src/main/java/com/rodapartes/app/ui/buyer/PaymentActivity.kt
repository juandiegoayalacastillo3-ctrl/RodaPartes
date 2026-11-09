package com.rodapartes.app.ui.buyer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.Order
import com.rodapartes.app.data.OrderStatus
import com.rodapartes.app.databinding.ActivityPaymentBinding
import com.rodapartes.app.databinding.DialogPaymentSuccessBinding
import com.rodapartes.app.util.Money
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.visible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pasarela de pagos (interfaz). Los datos de la tarjeta irán directo al widget de Wompi:
 * la app solo guarda el token y la referencia (RF-PAG-03, RNF-SEG-02).
 */
class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private var total = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        total = intent.getIntExtra(EXTRA_TOTAL, MockData.cartTotal())
        binding.tvTotal.text = Money.format(total)
        binding.btnPay.text = getString(R.string.payment_pay, Money.format(total))

        binding.toggleMethod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.groupCard.visible(checkedId == R.id.btnCard)
            binding.groupPse.visible(checkedId == R.id.btnPse)
            binding.groupNequi.visible(checkedId == R.id.btnNequi)
        }

        setupCardPreview()
        binding.btnPay.setOnClickListener { if (validate()) pay() }
    }

    /** La tarjeta de muestra se actualiza mientras el usuario escribe. */
    private fun setupCardPreview() {
        binding.etCardNumber.addTextChangedListener(object : TextWatcher {
            private var editing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (editing || s == null) return
                editing = true
                // Agrupa en bloques de 4: 4111 1111 1111 1111
                val digits = s.filter { it.isDigit() }.take(16)
                val formatted = digits.chunked(4).joinToString(" ")
                if (formatted != s.toString()) s.replace(0, s.length, formatted)
                val masked = digits.padEnd(16, '•').chunked(4).joinToString(" ")
                binding.tvCardNumber.text = masked
                editing = false
            }
        })
        binding.etCardName.doAfterTextChanged {
            binding.tvCardName.text = if (it.isNullOrBlank()) getString(R.string.card_name_placeholder) else it
        }
        binding.etExp.doAfterTextChanged {
            binding.tvCardExp.text = if (it.isNullOrBlank()) getString(R.string.card_exp_placeholder) else it
        }
    }

    private fun validate(): Boolean {
        val required = getString(R.string.error_required)
        return when (binding.toggleMethod.checkedButtonId) {
            R.id.btnCard -> {
                val number = binding.etCardNumber.text.toString().filter { it.isDigit() }
                val exp = binding.etExp.text.toString()
                binding.tilCardNumber.error = if (number.length < 15) getString(R.string.error_card) else null
                binding.tilCardName.error = if (binding.etCardName.text.isNullOrBlank()) required else null
                binding.tilExp.error = if (!Regex("(0[1-9]|1[0-2])/\\d{2}").matches(exp)) getString(R.string.error_exp) else null
                binding.tilCvv.error = if ((binding.etCvv.text?.length ?: 0) < 3) required else null
                listOf(binding.tilCardNumber, binding.tilCardName, binding.tilExp, binding.tilCvv).all { it.error == null }
            }
            R.id.btnPse -> {
                binding.tilBank.error = if (binding.actBank.text.isNullOrBlank()) required else null
                binding.tilDocument.error = if (binding.etDocument.text.isNullOrBlank()) required else null
                binding.tilBank.error == null && binding.tilDocument.error == null
            }
            else -> {
                binding.tilNequi.error = if ((binding.etNequi.text?.length ?: 0) != 10) getString(R.string.error_phone) else null
                binding.tilNequi.error == null
            }
        }
    }

    /** Simula la respuesta de la pasarela. En producción el estado lo confirma el webhook (RF-PAG-04). */
    private fun pay() {
        binding.btnPay.isEnabled = false
        binding.btnPay.text = getString(R.string.payment_processing)
        lifecycleScope.launch {
            delay(1500)
            val code = "RP-" + (1043 + MockData.myOrders.size)
            val summary = MockData.cart.joinToString { it.product.name }
            val order = Order(code, Session(this@PaymentActivity).name, MockData.cart.first().product.seller,
                MockData.cartCount(), total, OrderStatus.NUEVO, getString(R.string.today),
                intent.getStringExtra(EXTRA_ADDRESS) ?: "", MockData.cart.first().product.distanceKm, summary)
            MockData.myOrders.add(0, order)
            MockData.orders.add(0, order)
            MockData.cart.clear()
            showSuccess(code)
        }
    }

    private fun showSuccess(code: String) {
        val dialog = DialogPaymentSuccessBinding.inflate(layoutInflater)
        dialog.tvOrderCode.text = getString(R.string.order_code, code)
        MaterialAlertDialogBuilder(this)
            .setView(dialog.root)
            .setCancelable(false)
            .setPositiveButton(R.string.payment_see_orders) { _, _ ->
                startActivity(BuyerActivity.intentForTab(this, R.id.nav_orders))
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_TOTAL = "total"
        const val EXTRA_ADDRESS = "address"
    }
}
