package com.alertnotes.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App-lifetime coroutine scope for work that must outlive any single screen —
 * broadcast-receiver processing, rescheduling sweeps, and similar.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                // Alert delivery must survive a bad collector: without a
                // handler, one uncaught exception in any app-scope coroutine
                // crashes the whole process.
                CoroutineExceptionHandler { _, throwable ->
                    Log.e("ApplicationScope", "Uncaught exception in app-scope coroutine", throwable)
                },
        )
}
