package com.lesmangeursdurouleau.app.di

import android.content.Context // Importez Context
import com.lesmangeursdurouleau.app.utils.ErrorMessageConverter // Importez votre nouvelle classe
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext // Importez ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideErrorMessageConverter(@ApplicationContext context: Context): ErrorMessageConverter {
        return ErrorMessageConverter(context)
    }
}