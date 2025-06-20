package com.lesmangeursdurouleau.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.data.repository.ChatRepository
import com.lesmangeursdurouleau.app.data.repository.ChatRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepository
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepositoryImpl

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // SUPPRESSION: Cette méthode est maintenant obsolète et doit être supprimée.
    /*
    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: FirebaseFirestore,
        firebaseAuth: FirebaseAuth,
        firebaseStorageService: FirebaseStorageService
    ): UserRepository {
        return UserRepositoryImpl(firestore, firebaseAuth, firebaseStorageService)
    }
    */

    @Provides
    @Singleton
    fun provideChatRepository(
        firestore: FirebaseFirestore,
        firebaseAuth: FirebaseAuth
    ): ChatRepository {
        return ChatRepositoryImpl(firestore, firebaseAuth)
    }

    @Provides
    @Singleton
    fun provideMonthlyReadingRepository(
        firestore: FirebaseFirestore
    ): MonthlyReadingRepository {
        return MonthlyReadingRepositoryImpl(firestore)
    }

    @Provides
    @Singleton
    fun provideAppConfigRepository(
        firestore: FirebaseFirestore
    ): AppConfigRepository {
        return AppConfigRepositoryImpl(firestore)
    }
}