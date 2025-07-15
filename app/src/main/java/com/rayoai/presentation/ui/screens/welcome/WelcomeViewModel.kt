
package com.rayoai.presentation.ui.screens.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _isFirstRun = MutableStateFlow(false)
    val isFirstRun = _isFirstRun.asStateFlow()

    init {
        viewModelScope.launch {
            _isFirstRun.value = userPreferencesRepository.isFirstRun.first()
        }
    }

    fun onWelcomeShown() {
        viewModelScope.launch {
            userPreferencesRepository.setFirstRun(false)
        }
    }
}
