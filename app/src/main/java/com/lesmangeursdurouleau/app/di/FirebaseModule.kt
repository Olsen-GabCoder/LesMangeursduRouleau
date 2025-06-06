package com.lesmangeursdurouleau.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent // Pour des instances uniques dans toute l'app
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Les providers ici seront des singletons
object FirebaseModule {

    @Provides
    @Singleton // Une seule instance de FirebaseAuth pour toute l'app
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton // Une seule instance de FirebaseFirestore pour toute l'app
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton // Une seule instance de FirebaseStorage pour toute l'app
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }
}