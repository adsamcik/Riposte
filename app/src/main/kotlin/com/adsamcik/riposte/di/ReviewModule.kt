package com.adsamcik.riposte.di

import com.adsamcik.riposte.core.common.review.UserActionTracker
import com.adsamcik.riposte.review.InAppReviewManager
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
    abstract fun bindUserActionTracker(impl: InAppReviewManager): UserActionTracker
}
