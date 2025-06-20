package com.lesmangeursdurouleau.app.di

import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.BookRepositoryImpl
// AJOUT: Imports pour les nouveaux repositories
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.data.repository.ReadingRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.data.repository.SocialRepositoryImpl
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule { // Doit être une classe abstraite

    @Binds
    @Singleton
    abstract fun bindBookRepository(
        bookRepositoryImpl: BookRepositoryImpl
    ): BookRepository

    // --- NOUVELLES LIAISONS POUR LES REPOSITORIES REFACTORISÉS ---

    @Binds
    @Singleton
    abstract fun bindPrivateChatRepository(
        privateChatRepositoryImpl: PrivateChatRepositoryImpl
    ): PrivateChatRepository

    @Binds
    @Singleton
    abstract fun bindSocialRepository(
        socialRepositoryImpl: SocialRepositoryImpl
    ): SocialRepository

    @Binds
    @Singleton
    abstract fun bindReadingRepository(
        readingRepositoryImpl: ReadingRepositoryImpl
    ): ReadingRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(
        userProfileRepositoryImpl: UserProfileRepositoryImpl
    ): UserProfileRepository
}