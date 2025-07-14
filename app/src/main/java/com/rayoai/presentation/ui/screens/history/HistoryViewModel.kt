package com.rayoai.presentation.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.data.local.db.CaptureDao
import com.rayoai.data.local.model.CaptureEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para la pantalla de historial de capturas.
 * Gestiona la lógica para mostrar, eliminar y navegar desde el historial.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val captureDao: CaptureDao,
    private val imageStorageManager: ImageStorageManager
) : ViewModel() {

    // Flow que emite la lista de todas las capturas, ordenadas por marca de tiempo descendente.
    val captures: Flow<List<CaptureEntity>> = captureDao.getAllCaptures()

    /**
     * Elimina una única captura, borrando su entrada en la base de datos y el archivo de imagen asociado.
     * @param capture La entidad [CaptureEntity] a eliminar.
     */
    fun deleteCapture(capture: CaptureEntity) {
        viewModelScope.launch {
            // Eliminar la imagen del almacenamiento físico.
            imageStorageManager.deleteImage(capture.imageUri)
            // Eliminar la entrada de la base de datos.
            captureDao.deleteCapture(capture.id)
        }
    }

    /**
     * Elimina todas las capturas del historial.
     * Itera sobre todas las capturas, elimina cada archivo de imagen y luego limpia la base de datos.
     */
    fun deleteAllCaptures() {
        viewModelScope.launch {
            // Obtener la lista actual de capturas para poder eliminar los archivos.
            val allCaptures = captureDao.getAllCapturesList()
            allCaptures.forEach { capture ->
                imageStorageManager.deleteImage(capture.imageUri)
            }
            // Eliminar todas las entradas de la base de datos.
            captureDao.deleteAllCaptures()
        }
    }
}