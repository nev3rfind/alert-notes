package com.alertnotes.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase entry points for online mode. Both resolve against the default
 * [com.google.firebase.FirebaseApp], which the SDK initializes from
 * google-services.json before Application.onCreate.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    /** FCM arrives in a later session; the seam exists now. */
    @Provides
    @Singleton
    fun providePushNotificationService():
        com.alertnotes.domain.repository.PushNotificationService =
        com.alertnotes.domain.repository.NoOpPushNotificationService()
}
