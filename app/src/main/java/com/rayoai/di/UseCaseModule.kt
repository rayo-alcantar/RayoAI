package com.rayoai.di

import com.rayoai.data.local.db.CaptureDao
import com.rayoai.domain.repository.VisionRepository
import com.rayoai.domain.usecase.ContinueChatUseCase
import com.rayoai.domain.usecase.DeleteAllCapturesUseCase
import com.rayoai.domain.usecase.DeleteCaptureUseCase
import com.rayoai.domain.usecase.DescribeImageUseCase
import com.rayoai.domain.usecase.GetHistoryUseCase
import com.rayoai.domain.usecase.SaveCaptureUseCase
import com.rayoai.domain.usecase.UpdateChatHiddenStateUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    @Provides
    @ViewModelScoped
    fun provideGetHistoryUseCase(visionRepository: VisionRepository): GetHistoryUseCase {
        return GetHistoryUseCase(visionRepository)
    }

    @Provides
    @ViewModelScoped
    fun provideDeleteAllCapturesUseCase(visionRepository: VisionRepository): DeleteAllCapturesUseCase {
        return DeleteAllCapturesUseCase(visionRepository)
    }

    @Provides
    @ViewModelScoped
    fun provideDeleteCaptureUseCase(visionRepository: VisionRepository): DeleteCaptureUseCase {
        return DeleteCaptureUseCase(visionRepository)
    }

    @Provides
    @ViewModelScoped
    fun provideSaveCaptureUseCase(captureDao: CaptureDao): SaveCaptureUseCase {
        return SaveCaptureUseCase(captureDao)
    }

    @Provides
    @ViewModelScoped
    fun provideDescribeImageUseCase(visionRepository: VisionRepository): DescribeImageUseCase {
        return DescribeImageUseCase(visionRepository)
    }

    @Provides
    @ViewModelScoped
    fun provideContinueChatUseCase(visionRepository: VisionRepository): ContinueChatUseCase {
        return ContinueChatUseCase(visionRepository)
    }

    @Provides
    @ViewModelScoped
    fun provideUpdateChatHiddenStateUseCase(visionRepository: VisionRepository): UpdateChatHiddenStateUseCase {
        return UpdateChatHiddenStateUseCase(visionRepository)
    }
}
