package com.rayoai.presentation.ui.screens.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.R
import com.rayoai.core.ResultWrapper
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.DescribeImageUseCase
import com.rayoai.domain.usecase.SaveCaptureUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

enum class SharedImageDescriptionStatus { PENDING, IMPORTING, DESCRIBING, SUCCEEDED, FAILED }

data class SharedImageDescription(
    val uri: Uri,
    val status: SharedImageDescriptionStatus = SharedImageDescriptionStatus.PENDING,
    val description: String? = null,
    val captureId: Long? = null,
    val error: String? = null
)

data class MultiImagePassageUiState(
    val images: List<SharedImageDescription> = emptyList(),
    val currentIndex: Int = 0,
    val isProcessing: Boolean = false,
    val batchError: String? = null
)

/** Procesa una foto a la vez para conservar orden, cuota y memoria estables. */
@HiltViewModel
class MultiImagePassageViewModel @Inject constructor(
    private val describeImageUseCase: DescribeImageUseCase,
    private val saveCaptureUseCase: SaveCaptureUseCase,
    private val imageStorageManager: ImageStorageManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(MultiImagePassageUiState())
    val uiState: StateFlow<MultiImagePassageUiState> = _uiState.asStateFlow()

    fun start(imageUris: List<Uri>) {
        if (_uiState.value.images.isNotEmpty() || imageUris.size < 3) return
        _uiState.value = MultiImagePassageUiState(images = imageUris.map(::SharedImageDescription))
        processPendingImages()
    }

    fun showPrevious() = showImageAt(_uiState.value.currentIndex - 1)

    fun showNext() = showImageAt(_uiState.value.currentIndex + 1)

    fun retryCurrentImage() {
        val current = _uiState.value.images.getOrNull(_uiState.value.currentIndex) ?: return
        if (!_uiState.value.isProcessing && current.status == SharedImageDescriptionStatus.FAILED) {
            replaceImage(_uiState.value.currentIndex) { it.copy(status = SharedImageDescriptionStatus.PENDING, error = null) }
            processPendingImages()
        }
    }

    private fun showImageAt(index: Int) {
        val image = _uiState.value.images.getOrNull(index) ?: return
        if (image.status == SharedImageDescriptionStatus.SUCCEEDED) {
            _uiState.update { it.copy(currentIndex = index) }
        }
    }

    private fun processPendingImages() {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _uiState.update { it.copy(batchError = appContext.getString(R.string.scan_pdf_user_message_missing_api_key)) }
                return@launch
            }
            _uiState.update { it.copy(isProcessing = true, batchError = null) }
            while (true) {
                val nextIndex = _uiState.value.images.indexOfFirst { it.status == SharedImageDescriptionStatus.PENDING }
                if (nextIndex < 0) break
                describeImageAt(nextIndex, apiKey)
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    private suspend fun describeImageAt(index: Int, apiKey: String) {
        replaceImage(index) { it.copy(status = SharedImageDescriptionStatus.IMPORTING, error = null) }
        val sourceImage = _uiState.value.images[index]
        val localImageUri = withContext(Dispatchers.IO) {
            imageStorageManager.importSharedImage(sourceImage.uri)
        }
        if (localImageUri == null) {
            replaceImage(index) {
                it.copy(status = SharedImageDescriptionStatus.FAILED, error = appContext.getString(R.string.error_loading_shared_image))
            }
            return
        }
        replaceImage(index) { it.copy(uri = localImageUri, status = SharedImageDescriptionStatus.DESCRIBING) }
        val image = _uiState.value.images[index]
        val bitmap = withContext(Dispatchers.IO) { imageStorageManager.getBitmapFromUri(image.uri) }
        if (bitmap == null) {
            replaceImage(index) {
                it.copy(status = SharedImageDescriptionStatus.FAILED, error = appContext.getString(R.string.error_loading_shared_image))
            }
            return
        }

        val userMessage = ChatMessage(appContext.getString(R.string.shared_image_description), isFromUser = true)
        try {
            val languageCode = Locale.getDefault().language
            val model = userPreferencesRepository.defaultModel.first().ifBlank { GeminiModelConfig.DEFAULT_MODEL }
            describeImageUseCase(
                apiKey = apiKey,
                image = bitmap,
                userPrePrompt = "",
                languageCode = languageCode,
                model = model
            ).collect { result ->
                when (result) {
                    is ResultWrapper.Success -> {
                        val messages = listOf(userMessage, ChatMessage(result.data, isFromUser = false))
                        val captureId = saveCaptureUseCase(listOf(image.uri.toString()), messages)
                        replaceImage(index) {
                            it.copy(
                                status = SharedImageDescriptionStatus.SUCCEEDED,
                                description = result.data,
                                captureId = captureId,
                                error = null
                            )
                        }
                        _uiState.update { it.copy(currentIndex = index) }
                    }
                    is ResultWrapper.Error -> replaceImage(index) {
                        it.copy(status = SharedImageDescriptionStatus.FAILED, error = result.message)
                    }
                    ResultWrapper.Loading -> Unit
                }
            }
        } catch (e: Exception) {
            replaceImage(index) {
                it.copy(status = SharedImageDescriptionStatus.FAILED, error = appContext.getString(R.string.unexpected_error))
            }
        }
    }

    private fun replaceImage(index: Int, transform: (SharedImageDescription) -> SharedImageDescription) {
        _uiState.update { state ->
            state.copy(images = state.images.mapIndexed { itemIndex, item -> if (itemIndex == index) transform(item) else item })
        }
    }
}
