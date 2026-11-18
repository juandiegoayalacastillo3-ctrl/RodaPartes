package com.rodapartes.app.ui.seller

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.rodapartes.app.R
import com.rodapartes.app.data.Category
import com.rodapartes.app.data.Product
import com.rodapartes.app.data.ProductRepository
import com.rodapartes.app.data.ProductStatus
import com.rodapartes.app.databinding.ActivityProductFormBinding
import com.rodapartes.app.databinding.ItemPhotoThumbBinding
import com.rodapartes.app.util.PhotoFiles
import com.rodapartes.app.util.Session
import com.rodapartes.app.util.safeLaunch
import com.rodapartes.app.util.snack
import com.rodapartes.app.util.visible
import kotlinx.coroutines.launch

/**
 * Crear o editar un producto (RF-PRO-01 a RF-PRO-06).
 *
 * Las fotos se toman con la cámara o se eligen de la galería. Mientras se edita, la lista
 * [photos] mezcla URL (fotos ya guardadas en Storage) y Uri (fotos nuevas en el celular).
 * Al guardar, las nuevas se suben a Firebase Storage y el producto se escribe en Firestore.
 */
class ProductFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductFormBinding
    private var product: Product? = null
    private val photos = mutableListOf<Any>()          // String (URL) o Uri (local)
    private val removedUrls = mutableListOf<String>()  // fotos que se borrarán de Storage
    private var cameraUri: Uri? = null
    private val maxPhotos = 6

    /** Galería: permite elegir varias fotos a la vez. */
    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxPhotos)) { uris ->
        uris.take(maxPhotos - photos.size).forEach { photos += it }
        renderPhotos()
    }

    /** Cámara del sistema: guarda la foto en el archivo temporal que le pasamos. */
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) {
            photos += uri
            renderPhotos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        buildModelChips()
        binding.tvReviewNote.text = getString(R.string.form_new_note)
        renderPhotos()

        // Modo edición: carga el producto desde Firestore
        intent.getStringExtra(EXTRA_ID)?.let { id ->
            lifecycleScope.launch {
                runCatching { ProductRepository.get(id) }.getOrNull()?.let { fill(it) }
            }
        }

        binding.btnTakePhoto.setOnClickListener {
            if (photos.size >= maxPhotos) binding.root.snack(getString(R.string.form_max_photos))
            else choosePhotoSource()
        }
        binding.btnPublish.setOnClickListener { save(publish = true) }
        binding.btnDraft.setOnClickListener { save(publish = false) }
    }

    /** Pregunta si la foto viene de la cámara o de la galería (RF-PRO-02). */
    private fun choosePhotoSource() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.form_take_photo)
            .setItems(resources.getStringArray(R.array.photo_sources)) { _, which ->
                if (which == 0) {
                    cameraUri = PhotoFiles.newCameraUri(this)
                    takePhoto.launch(cameraUri!!)
                } else {
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            .show()
    }

    /** Llena el formulario con un producto existente. */
    private fun fill(p: Product) {
        product = p
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
        binding.actCategory.setText(p.categoryEnum.label, false)
        binding.etRef.setText(p.ref)
        binding.toggleType.check(if (p.original) R.id.btnOriginal else R.id.btnGeneric)
        for (i in 0 until binding.chipModels.childCount) {
            val chip = binding.chipModels.getChildAt(i) as Chip
            chip.isChecked = p.compatible.contains(chip.text.toString())
        }
        photos.clear()
        photos += p.photos
        renderPhotos()
        binding.tvReviewNote.text = getString(R.string.form_review_note)
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

    /** Dibuja las miniaturas después del botón "Agregar foto". */
    private fun renderPhotos() {
        while (binding.llPhotos.childCount > 1) binding.llPhotos.removeViewAt(1)
        val category = Category.fromLabel(binding.actCategory.text.toString())
        photos.forEachIndexed { i, photo ->
            val thumb = ItemPhotoThumbBinding.inflate(layoutInflater, binding.llPhotos, false)
            thumb.tile.setBackgroundResource(category.tileRes)
            thumb.tile.clipToOutline = true
            thumb.ivIcon.visible(false)
            thumb.ivPhoto.visible(true)
            when (photo) {
                is Uri -> thumb.ivPhoto.setImageURI(photo)
                is String -> thumb.ivPhoto.load(photo)
            }
            thumb.tvIndex.text = (i + 1).toString()
            thumb.btnRemove.setOnClickListener {
                (photo as? String)?.let { removedUrls += it }
                photos.remove(photo)
                renderPhotos()
            }
            binding.llPhotos.addView(thumb.root)
        }
        binding.tvPhotosLabel.text = getString(R.string.form_photos_count, photos.size, maxPhotos)
    }

    /**
     * Valida, sube las fotos nuevas a Storage y guarda el producto en Firestore.
     * Publicar lo deja EN_REVISION para el administrador; guardar borrador lo deja oculto.
     * Si solo cambian precio o stock de un producto publicado, sigue publicado (RF-PRO-05).
     */
    private fun save(publish: Boolean) {
        val required = getString(R.string.error_required)
        val name = binding.etName.text.toString().trim()
        val price = binding.etPrice.text.toString().toLongOrNull() ?: 0L
        val stock = binding.etStock.text.toString().toLongOrNull()
        val category = binding.actCategory.text.toString()
        val models = (0 until binding.chipModels.childCount)
            .map { binding.chipModels.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .map { it.text.toString() }

        binding.tilName.error = if (name.length < 4) required else null
        binding.tilPrice.error = if (price <= 0) getString(R.string.error_price) else null
        binding.tilStock.error = if (stock == null || stock < 0) required else null
        binding.tilCategory.error = if (category.isBlank()) required else null
        if (listOf(binding.tilName, binding.tilPrice, binding.tilStock, binding.tilCategory).any { it.error != null }) return
        if (publish && photos.isEmpty()) {
            binding.root.snack(getString(R.string.form_need_photo)); return
        }
        if (publish && models.isEmpty()) {
            binding.root.snack(getString(R.string.form_need_model)); return
        }

        val user = Session.user ?: return
        val existing = product
        val id = existing?.id ?: ProductRepository.newId()
        val nameOrPhotosChanged = existing == null || existing.name != name ||
            photos.any { it is Uri } || removedUrls.isNotEmpty()
        val status = when {
            !publish -> ProductStatus.BORRADOR
            existing?.statusEnum == ProductStatus.PUBLICADO && !nameOrPhotosChanged -> ProductStatus.PUBLICADO
            else -> ProductStatus.EN_REVISION
        }

        setSaving(true)
        lifecycleScope.safeLaunch(binding.root, onDone = { setSaving(false) }) {
            val urls = ProductRepository.uploadPhotos(id, photos.toList())
            ProductRepository.deletePhotos(removedUrls)
            ProductRepository.save(Product(
                id = id,
                name = name,
                ref = binding.etRef.text.toString().trim().uppercase(),
                category = Category.fromLabel(category).name,
                price = price,
                stock = stock ?: 0,
                minStock = binding.etMinStock.text.toString().toLongOrNull() ?: 2,
                sellerId = user.uid,
                sellerName = user.displayName,
                compatible = models,
                original = binding.toggleType.checkedButtonId == R.id.btnOriginal,
                status = status.name,
                description = binding.etDescription.text.toString().trim(),
                warrantyMonths = existing?.warrantyMonths ?: 3,
                reports = existing?.reports ?: 0,
                photos = urls,
                createdAt = existing?.createdAt ?: Timestamp.now()
            ))
            MaterialAlertDialogBuilder(this@ProductFormActivity)
                .setTitle(if (status == ProductStatus.BORRADOR) R.string.form_draft_title
                    else if (status == ProductStatus.PUBLICADO) R.string.form_saved_title else R.string.form_sent_title)
                .setMessage(if (status == ProductStatus.BORRADOR) R.string.form_draft_msg
                    else if (status == ProductStatus.PUBLICADO) R.string.form_saved_msg else R.string.form_sent_msg)
                .setPositiveButton(R.string.action_understood) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun setSaving(saving: Boolean) {
        binding.btnPublish.isEnabled = !saving
        binding.btnDraft.isEnabled = !saving
        if (saving) binding.btnPublish.setText(R.string.form_uploading)
        else binding.btnPublish.setText(if (product == null) R.string.form_publish else R.string.form_save_changes)
    }

    /** Elimina el producto y sus fotos después de confirmar. */
    private fun confirmDelete(p: Product) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_product_title)
            .setMessage(getString(R.string.delete_product_msg, p.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.safeLaunch(binding.root) {
                    ProductRepository.delete(p)
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "product_id"
    }
}
