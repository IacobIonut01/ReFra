/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.injection

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.MediaDistributorImpl
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.core.MediaHandlerImpl
import com.dot.gallery.core.MediaSelector
import com.dot.gallery.core.MediaSelectorImpl
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.data.repository.MediaRepositoryImpl
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.use_case.MediaHandleUseCase
import com.dot.gallery.feature_node.domain.util.EventHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideDatabase(app: Application): InternalDatabase =
        Room.databaseBuilder(app, InternalDatabase::class.java, InternalDatabase.NAME)
            .build()

    @Provides
    @Singleton
    fun provideKeychainHolder(@ApplicationContext context: Context): KeychainHolder =
        KeychainHolder(context)

    @Provides
    @Singleton
    fun provideMediaHandleUseCase(
        repository: MediaRepository,
        @ApplicationContext context: Context
    ): MediaHandleUseCase = MediaHandleUseCase(repository, context)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideEventHandler(): EventHandler = DefaultEventHandler()

    @Provides
    @Singleton
    fun provideMediaDistributor(
        workManager: WorkManager,
        repository: MediaRepository,
        eventHandler: EventHandler
    ): MediaDistributor = MediaDistributorImpl(repository, eventHandler, workManager)

    @Provides
    @Singleton
    fun provideMediaSelector(): MediaSelector = MediaSelectorImpl()

    @Provides
    @Singleton
    fun provideMediaHandler(
        @ApplicationContext context: Context,
        mediaRepository: MediaRepository
    ): MediaHandler = MediaHandlerImpl(mediaRepository, context)

    @Provides
    @Singleton
    fun provideMediaRepository(
        @ApplicationContext context: Context,
        workManager: WorkManager,
        database: InternalDatabase,
        keychainHolder: KeychainHolder
    ): MediaRepository = MediaRepositoryImpl(context, workManager, database, keychainHolder)

}
