package com.rayoai.presentation.ui.screens.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.DescribeImageUseCase
import com.rayoai.domain.usecase.ContinueChatUseCase
import com.rayoai.domain.usecase.SaveCaptureUseCase
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.data.local.db.CaptureDao
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: HomeViewModel
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    private val describeImageUseCase: DescribeImageUseCase = mockk(relaxed = true)
    private val continueChatUseCase: ContinueChatUseCase = mockk(relaxed = true)
    private val saveCaptureUseCase: SaveCaptureUseCase = mockk(relaxed = true)
    private val imageStorageManager: ImageStorageManager = mockk(relaxed = true)
    private val captureDao: CaptureDao = mockk(relaxed = true)

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel = HomeViewModel(
            describeImageUseCase,
            continueChatUseCase,
            userPreferencesRepository,
            saveCaptureUseCase,
            imageStorageManager,
            captureDao,
            mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `showRatingBanner should be true when user has not rated and last prompt was more than 72 hours ago`() = runTest {
        // Given
        val currentTime = System.currentTimeMillis()
        val lastPromptTime = currentTime - (73 * 60 * 60 * 1000) // 73 hours ago
        userPreferencesRepository.setHasRated(false)
        userPreferencesRepository.setLastPromptTime(lastPromptTime)

        // When
        viewModel = HomeViewModel(
            describeImageUseCase,
            continueChatUseCase,
            userPreferencesRepository,
            saveCaptureUseCase,
            imageStorageManager,
            captureDao,
            mockk(relaxed = true)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(viewModel.uiState.value.showRatingBanner)
    }

    @Test
    fun `showRatingBanner should be false when user has already rated`() = runTest {
        // Given
        userPreferencesRepository.setHasRated(true)

        // When
        viewModel = HomeViewModel(
            describeImageUseCase,
            continueChatUseCase,
            userPreferencesRepository,
            saveCaptureUseCase,
            imageStorageManager,
            captureDao,
            mockk(relaxed = true)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertFalse(viewModel.uiState.value.showRatingBanner)
    }

    @Test
    fun `showRatingBanner should be false when last prompt was less than 72 hours ago`() = runTest {
        // Given
        val currentTime = System.currentTimeMillis()
        val lastPromptTime = currentTime - (71 * 60 * 60 * 1000) // 71 hours ago
        userPreferencesRepository.setHasRated(false)
        userPreferencesRepository.setLastPromptTime(lastPromptTime)

        // When
        viewModel = HomeViewModel(
            describeImageUseCase,
            continueChatUseCase,
            userPreferencesRepository,
            saveCaptureUseCase,
            imageStorageManager,
            captureDao,
            mockk(relaxed = true)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertFalse(viewModel.uiState.value.showRatingBanner)
    }

    @Test
    fun `onRateNowClicked should set hasRated to true and hide banner`() = runTest {
        // When
        viewModel.onRateNowClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(userPreferencesRepository.hasRated.first())
        assertFalse(viewModel.uiState.value.showRatingBanner)
    }

    @Test
    fun `onRateLaterClicked should save prompt time and hide banner`() = runTest {
        // When
        viewModel.onRateLaterClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(userPreferencesRepository.lastPromptTime.first() > 0)
        assertFalse(viewModel.uiState.value.showRatingBanner)
    }
}

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val _apiKey = MutableStateFlow<String?>(null)
    override val apiKey = _apiKey

    private val _themeMode = MutableStateFlow(com.rayoai.domain.repository.ThemeMode.SYSTEM)
    override val themeMode = _themeMode

    private val _textScale = MutableStateFlow(1.0f)
    override val textScale = _textScale

    private val _autoDescribeOnShare = MutableStateFlow(false)
    override val autoDescribeOnShare = _autoDescribeOnShare

    private val _isFirstRun = MutableStateFlow(true)
    override val isFirstRun = _isFirstRun

    private val _hasShownApiUsageWarning = MutableStateFlow(false)
    override val hasShownApiUsageWarning = _hasShownApiUsageWarning

    private val _hasRated = MutableStateFlow(false)
    override val hasRated: kotlinx.coroutines.flow.Flow<Boolean> = _hasRated

    private val _lastPromptTime = MutableStateFlow(0L)
    override val lastPromptTime: kotlinx.coroutines.flow.Flow<Long> = _lastPromptTime

    override suspend fun saveApiKey(apiKey: String) {
        _apiKey.value = apiKey
    }

    override suspend fun saveThemeMode(mode: com.rayoai.domain.repository.ThemeMode) {
        _themeMode.value = mode
    }

    override suspend fun saveTextScale(scale: Float) {
        _textScale.value = scale
    }

    override suspend fun saveAutoDescribeOnShare(enabled: Boolean) {
        _autoDescribeOnShare.value = enabled
    }

    override suspend fun setFirstRun(isFirstRun: Boolean) {
        _isFirstRun.value = isFirstRun
    }

    override suspend fun setHasShownApiUsageWarning(shown: Boolean) {
        _hasShownApiUsageWarning.value = shown
    }

    override suspend fun saveHasRated(hasRated: Boolean) {
        _hasRated.value = hasRated
    }

    override suspend fun saveLastPromptTime(time: Long) {
        _lastPromptTime.value = time
    }

    fun setHasRated(value: Boolean) {
        _hasRated.value = value
    }

    fun setLastPromptTime(value: Long) {
        _lastPromptTime.value = value
    }
}