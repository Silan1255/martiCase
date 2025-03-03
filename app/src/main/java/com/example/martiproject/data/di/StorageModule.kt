package com.example.martiproject.data.di

import android.content.Context
import com.example.martiproject.data.storage.RouteSharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): RouteSharedPreferences {
        return RouteSharedPreferences(context)
    }
}