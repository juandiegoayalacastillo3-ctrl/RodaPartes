package com.rodapartes.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.rodapartes.app.R

/**
 * Gráfico de barras sencillo dibujado con Canvas (panel del vendedor y reporte de ventas).
 * Los colores se configuran desde el XML con atributos propios declarados en values/attrs.xml.
 * La última barra (periodo actual) va en color pleno y las demás atenuadas.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var values: List<Float> = emptyList()
    private var labels: List<String> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.scaledDensity
    }
    private var radius = 6f * resources.displayMetrics.density
    private val rect = RectF()

    init {
        context.obtainStyledAttributes(attrs, R.styleable.BarChartView).use { a ->
            barPaint.color = a.getColor(R.styleable.BarChartView_barColor,
                ContextCompat.getColor(context, R.color.naranja))
            mutedPaint.color = a.getColor(R.styleable.BarChartView_barMutedColor,
                ContextCompat.getColor(context, R.color.naranja_claro))
            labelPaint.color = a.getColor(R.styleable.BarChartView_labelColor,
                ContextCompat.getColor(context, R.color.texto_3))
            radius = a.getDimension(R.styleable.BarChartView_barCornerRadius, radius)
        }
        // Accesibilidad: TalkBack lee el resumen del gráfico (RNF-USA-03)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setData(values: List<Float>, labels: List<String>) {
        this.values = values
        this.labels = labels
        contentDescription = labels.zip(values).joinToString { "${it.first}: ${it.second.toInt()}" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val labelSpace = labelPaint.textSize * 1.8f
        val chartHeight = height - paddingTop - paddingBottom - labelSpace
        val slot = (width - paddingLeft - paddingRight).toFloat() / values.size
        val barWidth = slot * 0.56f
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1f

        values.forEachIndexed { i, v ->
            val left = paddingLeft + slot * i + (slot - barWidth) / 2
            val barHeight = (v / max) * chartHeight
            rect.set(left, paddingTop + chartHeight - barHeight, left + barWidth, paddingTop + chartHeight)
            val paint = if (i == values.lastIndex) barPaint else mutedPaint
            canvas.drawRoundRect(rect, radius, radius, paint)

            labels.getOrNull(i)?.let {
                canvas.drawText(it, left + barWidth / 2, height - paddingBottom - labelPaint.textSize * 0.3f, labelPaint)
            }
        }
    }
}
