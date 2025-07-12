package com.rayoai.presentation.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.data.local.db.CaptureDao
import com.rayoai.data.local.model.CaptureEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para la pantalla de historial de capturas.
 * Proporciona acceso a la lista de capturas guardadas en la base de datos.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val captureDao: CaptureDao
) : ViewModel() {

    // Flow que emite la lista de todas las capturas, ordenadas por marca de tiempo descendente.
    val captures: Flow<List<CaptureEntity>> = captureDao.getAllCaptures()

    // TODO: Añadir funciones para eliminar capturas si es necesario.
}