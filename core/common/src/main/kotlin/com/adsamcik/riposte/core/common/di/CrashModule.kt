package com.adsamcik.riposte.core.common.di

import android.content.Context
import com.adsamcik.riposte.core.common.crash.CrashLogDir
import com.adsamcik.riposte.core.common.crash.CrashLogManager
import com.adsamcik.riposte.core.common.crash.CrashReportWriter
import com.adsamcik.riposte.core.common.crash.DefaultCrashLogManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CrashModule {
    @Binds
    abstract fun bindCrashLogManager(impl: DefaultCrashLogManager): CrashLogManager

    companion object {
        @Provides
        @Singleton
        @CrashLogDir
        fun provideCrashLogDir(
            @ApplicationContext context: Context,
        ): File = File(context.filesDir, CrashReportWriter.CRASH_DIR_NAME)
    }
}
