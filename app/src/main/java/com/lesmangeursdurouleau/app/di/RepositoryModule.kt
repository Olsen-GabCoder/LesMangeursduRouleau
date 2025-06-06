package com.lesmangeursdurouleau.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.data.repository.ChatRepository
import com.lesmangeursdurouleau.app.data.repository.ChatRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.data.repository.UserRepositoryImpl
// L'import de BookRepository (interface) et BookRepositoryImpl (implémentation) ne sont plus nécessaires ici
// car leur binding sera fait dans RepositoryBindingsModule

// NOUVEAU: Importez le nouveau dépôt
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepository
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepositoryImpl

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule { // C'est un object pour les @Provides statiques

    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: FirebaseFirestore,
        firebaseAuth: FirebaseAuth,
        firebaseStorageService: FirebaseStorageService
    ): UserRepository {
        return UserRepositoryImpl(firestore, firebaseAuth, firebaseStorageService)
    }

    // SUPPRIMEZ la méthode provideBookRepository d'ici !
    // Elle sera remplacée par @Binds dans RepositoryBindingsModule.

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

    // NOUVEAU: Fourniture du AppConfigRepository
    @Provides
    @Singleton
    fun provideAppConfigRepository(
        firestore: FirebaseFirestore
    ): AppConfigRepository { // Note: ici, on fournit l'interface AppConfigRepository, et l'implémentation est AppConfigRepositoryImpl
        return AppConfigRepositoryImpl(firestore)
    }
}