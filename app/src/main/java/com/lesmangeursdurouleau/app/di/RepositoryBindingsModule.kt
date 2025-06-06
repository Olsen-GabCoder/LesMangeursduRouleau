package com.lesmangeursdurouleau.app.di

import com.lesmangeursdurouleau.app.data.repository.BookRepository // MODIFIED: Changed to data.repository
import com.lesmangeursdurouleau.app.data.repository.BookRepositoryImpl // MODIFIED: Changed to data.repository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule { // Doit être une classe abstraite

    @Binds // Utilise @Binds pour lier l'interface à l'implémentation
    @Singleton // Le scope doit correspondre au module dans lequel il est installé
    abstract fun bindBookRepository(
        bookRepositoryImpl: BookRepositoryImpl // La méthode prend l'implémentation comme paramètre
    ): BookRepository // Et retourne l'interface qu'elle fournit (qui est maintenant dans le package data.repository)
}