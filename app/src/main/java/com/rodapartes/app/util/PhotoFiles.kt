package com.rodapartes.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Archivos temporales para las fotos tomadas con la cámara (RF-PRO-02).
 *
 * La cámara del celular necesita una URI donde escribir la foto. FileProvider crea esa URI
 * para un archivo dentro de la caché de la app, sin pedir permiso de almacenamiento.
 */
object PhotoFiles {

    /** Crea un archivo vacío en cache/photos y devuelve su URI compartible. */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File.createTempFile("foto_", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
