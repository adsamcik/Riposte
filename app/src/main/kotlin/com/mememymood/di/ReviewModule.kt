package com.mememymood.di

import com.mememymood.core.common.review.UserActionTracker
import com.mememymood.review.InAppReviewManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun bindUserActionTracker(
        impl: InAppReviewManager,
    ): UserActionTracker
}
