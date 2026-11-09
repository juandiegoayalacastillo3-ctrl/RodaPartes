package com.rodapartes.app.ui.seller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodapartes.app.R
import com.rodapartes.app.data.Category
import com.rodapartes.app.data.MockData
import com.rodapartes.app.data.Product
import com.rodapartes.app.data.ProductStatus
import com.rodapartes.app.databinding.ActivityProductFormBinding
import com.rodapartes.app.databinding.ItemPhotoThumbBinding
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible

/** Crear o editar un producto. Si llega EXTRA_ID es edición. */
class ProductFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductFormBinding
    private var product: Product? = null
    private var photos = 0
    private val maxPhotos = 6

    // Resultado de la cámara: una foto nueva o un código escaneado
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val code = result.data?.getStringExtra(CameraActivity.EXTRA_CODE)
        if (code != null) {
            binding.etRef.setText(code)
            binding.root.snack(getString(R.string.form_code_read, code))
        } else {
            addPhoto()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        product = intent.getIntExtra(EXTRA_ID, -1).takeIf { it > 0 }?.let { MockData.product(it) }

        buildModelChips()
        product?.let { fill(it) } ?: run {
            binding.tvReviewNote.text = getString(R.string.form_new_note)
        }
        renderPhotos()

        binding.btnTakePhoto.setOnClickListener {
            if (photos >= maxPhotos) binding.root.snack(getString(R.string.form_max_photos))
            else cameraLauncher.launch(Intent(this, CameraActivity::class.java)
                .putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_PHOTO))
        }
        binding.tilRef.setEndIconOnClickListener {
            cameraLauncher.launch(Intent(this, CameraActivity::class.java)
                .putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_SCAN))
        }
        binding.btnPublish.setOnClickListener { save(publish = true) }
        binding.btnDraft.setOnClickListener { save(publish = false) }
    }

    private fun fill(p: Product) {
        binding.toolbar.title = getString(R.string.form_edit_title)
        binding.toolbar.inflateMenu(R.menu.menu_delete)
        binding.toolbar.setOnMenuItemClickListener {
            confirmDelete(p)
            true
        }
        binding.etName.setText(p.name)
        binding.etDescription.setText(p.description)
        binding.etPrice.setText(p.price.toString())
        binding.etStock.setText(p.stock.toString())
        binding.etMinStock.setText(p.minStock.toString())
        binding.actCategory.setText(p.category.label, false)
        binding.etRef.setText(p.ref)
        binding.toggleType.check(if (p.original) R.id.btnOriginal else R.id.btnGeneric)
        for (i in 0 until binding.chipModels.childCount) {
            val chip = binding.chipModels.getChildAt(i) as Chip
            chip.isChecked = p.compatible.contains(chip.text.toString())
        }
        photos = p.photos
        binding.btnPublish.text = getString(R.string.form_save_changes)
    }

    /** Un chip por modelo de moto (selección múltiple). */
    private fun buildModelChips() {
        resources.getStringArray(R.array.modelos_moto).forEach { model ->
            binding.chipModels.addView(Chip(this).apply {
                id = View.generateViewId()
                text = model
                isCheckable = true
            })
        }
    }

    /** Dibuja las miniaturas de las fotos después del botón "Tomar foto". */
    private fun renderPhotos() {
        while (binding.llPhotos.childCount > 1) binding.llPhotos.removeViewAt(1)
        val category = product?.category
            ?: Category.fromLabel(binding.actCategory.text.toString())
        for (i in 1..photos) {
            val thumb = ItemPhotoThumbBinding.inflate(layoutInflater, binding.llPhotos, false)
            thumb.tile.setBackgroundResource(category.tileRes)
            thumb.ivIcon.setImageResource(category.iconRes)
            thumb.tvIndex.text = i.toString()
            thumb.btnRemove.setOnClickListener {
                photos--
                renderPhotos()
            }
            binding.llPhotos.addView(thumb.root)
        }
        binding.tvPhotosLabel.text = getString(R.string.form_photos_count, photos, maxPhotos)
    }

    private fun addPhoto() {
        photos++
        renderPhotos()
    }

    private fun save(publish: Boolean) {
        val required = getString(R.string.error_required)
        val name = binding.etName.text.toString().trim()
        val price = binding.etPrice.text.toString().toIntOrNull() ?: 0
        val stock = binding.etStock.text.toString().toIntOrNull()
        val category = binding.actCategory.text.toString()
        val models = (0 until binding.chipModels.childCount)
            .map { binding.chipModels.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .map { it.text.toString() }

        // Validaciones (en el servidor también: precio > 0 y stock ≥ 0)
        binding.tilName.error = if (name.length < 4) required else null
        binding.tilPrice.error = if (price <= 0) getString(R.string.error_price) else null
        binding.tilStock.error = if (stock == null || stock < 0) required else null
        binding.tilCategory.error = if (category.isBlank()) required else null
        if (publish && photos == 0) binding.root.snack(getString(R.string.form_need_photo))
        if (listOf(binding.tilName, binding.tilPrice, binding.tilStock, binding.tilCategory).any { it.error != null }) return
        if (publish && photos == 0) return
        if (publish && models.isEmpty()) {
            binding.root.snack(getString(R.string.form_need_model))
            return
        }

        val status = if (publish) ProductStatus.EN_REVISION else ProductStatus.BORRADOR
        val existing = product
        if (existing == null) {
            MockData.products.add(Product(
                MockData.newId(), name, binding.etRef.text.toString(), Category.fromLabel(category), price,
                stock ?: 0, binding.etMinStock.text.toString().toIntOrNull() ?: 2, "MotoCentro 80", 2.1,
                models, binding.toggleType.checkedButtonId == R.id.btnOriginal, status,
                binding.etDescription.text.toString(), 3, photos = photos
            ))
        } else {
            // Cambiar nombre o fotos manda a revisión; precio y stock no (RF-PRO-05)
            val needsReview = existing.name != name || existing.photos != photos
            existing.name = name
            existing.price = price
            existing.stock = stock ?: 0
            existing.minStock = binding.etMinStock.text.toString().toIntOrNull() ?: existing.minStock
            existing.category = Category.fromLabel(category)
            existing.ref = binding.etRef.text.toString()
            existing.description = binding.etDescription.text.toString()
            existing.original = binding.toggleType.checkedButtonId == R.id.btnOriginal
            existing.compatible = models
            existing.photos = photos
            existing.status = when {
                !publish -> ProductStatus.BORRADOR
                needsReview || existing.status != ProductStatus.PUBLICADO -> ProductStatus.EN_REVISION
                else -> ProductStatus.PUBLICADO
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (publish) R.string.form_sent_title else R.string.form_draft_title)
            .setMessage(if (publish) R.string.form_sent_msg else R.string.form_draft_msg)
            .setPositiveButton(R.string.action_understood) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun confirmDelete(p: Product) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_product_title)
            .setMessage(getString(R.string.delete_product_msg, p.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                MockData.products.remove(p)
                finish()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "product_id"
    }
}
