/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.di

import android.content.Context
import com.dot.gallery.cloud.core.ProviderInstanceFactory
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.cloud.core.auth.CloudInteractiveAuthHandler
import com.dot.gallery.cloud.data.dao.CloudAlbumSyncDao
import com.dot.gallery.cloud.data.dao.CloudDeleteLocalPrefDao
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.CloudOfflinePinDao
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.dao.CloudTagDao
import com.dot.gallery.cloud.data.dao.CloudUploadPrefDao
import com.dot.gallery.cloud.data.dao.DetectedFaceDao
import com.dot.gallery.cloud.data.dao.PersonDao
import com.dot.gallery.cloud.data.dao.SyncStateDao
import com.dot.gallery.cloud.data.repository.CloudRepository
import com.dot.gallery.cloud.data.repository.CloudRepositoryImpl
import com.dot.gallery.cloud.network.ServerUrlResolver
import com.dot.gallery.core.sandbox.IsolatedMetadataParser
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudModule {

    @Multibinds
    abstract fun bindProviderFactorySet(): Set<ProviderInstanceFactory>

    @Multibinds
    abstract fun bindInteractiveAuthHandlerSet(): Set<CloudInteractiveAuthHandler>

    companion object {
        @Provides
        @Singleton
        fun provideCloudMediaDao(database: InternalDatabase): CloudMediaDao =
            database.getCloudMediaDao()

        @Provides
        @Singleton
        fun provideCloudServerConfigDao(database: InternalDatabase): CloudServerConfigDao =
            database.getCloudServerConfigDao()

        @Provides
        @Singleton
        fun providePersonDao(database: InternalDatabase): PersonDao =
            database.getPersonDao()

        @Provides
        @Singleton
        fun provideDetectedFaceDao(database: InternalDatabase): DetectedFaceDao =
            database.getDetectedFaceDao()

        @Provides
        @Singleton
        fun provideSyncStateDao(database: InternalDatabase): SyncStateDao =
            database.getSyncStateDao()

        @Provides
        @Singleton
        fun provideCloudAlbumSyncDao(database: InternalDatabase): CloudAlbumSyncDao =
            database.getCloudAlbumSyncDao()

        @Provides
        @Singleton
        fun provideCloudUploadPrefDao(database: InternalDatabase): CloudUploadPrefDao =
            database.getCloudUploadPrefDao()

        @Provides
        @Singleton
        fun provideCloudDeleteLocalPrefDao(database: InternalDatabase): CloudDeleteLocalPrefDao =
            database.getCloudDeleteLocalPrefDao()

        @Provides
        @Singleton
        fun provideCloudOfflinePinDao(database: InternalDatabase): CloudOfflinePinDao =
            database.getCloudOfflinePinDao()

        @Provides
        @Singleton
        fun provideCloudTagDao(database: InternalDatabase): CloudTagDao =
            database.getCloudTagDao()

        @Provides
        @Singleton
        fun provideCloudRepository(
            registry: ProviderRegistry,
            cloudMediaDao: CloudMediaDao,
            cloudTagDao: CloudTagDao,
            urlResolver: ServerUrlResolver,
            configDao: CloudServerConfigDao,
            syncStateDao: SyncStateDao,
            @ApplicationContext context: Context,
            isolatedMetadataParser: IsolatedMetadataParser
        ): CloudRepository = CloudRepositoryImpl(
            registry, cloudMediaDao, cloudTagDao, urlResolver,
            configDao, syncStateDao, context, isolatedMetadataParser
        )
    }
}
