package com.rodapartes.app.ui.common

import com.rodapartes.app.data.Order
import com.rodapartes.app.data.OrderStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Cálculos para los reportes de ventas (paneles del vendedor y del administrador).
 * Trabajan sobre las órdenes leídas de Firestore; no se guardan cifras precalculadas.
 */
object SalesStats {

    /** Órdenes que cuentan como venta: se excluyen canceladas, retractos y reversiones. */
    fun valid(orders: List<Order>) = orders.filter {
        it.statusEnum !in listOf(OrderStatus.CANCELADO, OrderStatus.RETRACTO, OrderStatus.REVERSION)
    }

    /** Órdenes creadas en los últimos [days] días. */
    fun lastDays(orders: List<Order>, days: Int): List<Order> {
        val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.time
        return orders.filter { it.createdAt?.toDate()?.after(from) == true }
    }

    /**
     * Ventas por mes de los últimos [months] meses en millones de pesos.
     * Devuelve los valores y las etiquetas ("jun", "jul"...), el mes actual al final.
     */
    fun byMonth(orders: List<Order>, months: Int): Pair<List<Float>, List<String>> {
        val label = SimpleDateFormat("MMM", Locale("es", "CO"))
        val buckets = (months - 1 downTo 0).map { back ->
            Calendar.getInstance().apply { add(Calendar.MONTH, -back) }
        }
        val values = buckets.map { month ->
            orders.filter { o ->
                val d = o.createdAt?.toDate() ?: return@filter false
                val c = Calendar.getInstance().apply { time = d }
                c.get(Calendar.YEAR) == month.get(Calendar.YEAR) && c.get(Calendar.MONTH) == month.get(Calendar.MONTH)
            }.sumOf { it.total } / 1_000_000f
        }
        return values to buckets.map { label.format(it.time).trimEnd('.') }
    }

    /** Ventas por día de los últimos 7 días en millones de pesos (hoy al final). */
    fun byDay(orders: List<Order>, dayLabels: Array<String>): Pair<List<Float>, List<String>> {
        val days = (6 downTo 0).map { back -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) } }
        val values = days.map { day ->
            orders.filter { o ->
                val d = o.createdAt?.toDate() ?: return@filter false
                val c = Calendar.getInstance().apply { time = d }
                c.get(Calendar.YEAR) == day.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
            }.sumOf { it.total } / 1_000_000f
        }
        // Calendar: domingo = 1 ... sábado = 7; dayLabels empieza en lunes
        return values to days.map { dayLabels[(it.get(Calendar.DAY_OF_WEEK) + 5) % 7] }
    }

    /** Ventas por semana de las últimas 4 semanas en millones de pesos. */
    fun byWeek(orders: List<Order>): Pair<List<Float>, List<String>> {
        val values = (3 downTo 0).map { back ->
            val end = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7 * back) }.time
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7 * (back + 1)) }.time
            orders.filter { o ->
                val d = o.createdAt?.toDate() ?: return@filter false
                d.after(start) && !d.after(end)
            }.sumOf { it.total } / 1_000_000f
        }
        return values to listOf("S1", "S2", "S3", "S4")
    }

    /** Participación (%) de cada categoría en el valor vendido. */
    fun byCategory(orders: List<Order>): List<Pair<String, Int>> {
        val totals = orders.flatMap { it.items }.groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.price * it.qty } }
        val sum = totals.values.sum().takeIf { it > 0 } ?: return emptyList()
        return totals.entries.sortedByDescending { it.value }
            .map { it.key to (it.value * 100 / sum).toInt() }
    }
}
