/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.owncloud.di

import android.content.Context
import com.dot.gallery.cloud.core.MediaCapabilityProvider
import com.dot.gallery.cloud.core.ProviderInstanceFactory
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.dao.CloudMediaDao
import com.dot.gallery.data.dao.CloudServerConfigDao
import com.dot.gallery.data.dao.CloudUploadPrefDao
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.cloud.owncloud.OwnCloudDialect
import com.dot.gallery.cloud.webdav.WebDavMediaProvider

/**
 * kotlin-inject contribution surface for the OwnCloud flavour. The `noowncloud` variant of this file declares an empty
 * interface so the component compiles when the flavour is off.
 */
interface OwnCloudProviderComponent {

    @Singleton
    @IntoSet
    @Provides
    fun provideOwnCloudProviderFactory(
        context: Context,
        cloudMediaDao: CloudMediaDao,
        configDao: CloudServerConfigDao,
        uploadPrefDao: CloudUploadPrefDao
    ): ProviderInstanceFactory = object : ProviderInstanceFactory {
        override val providerType = ProviderType.OWNCLOUD
        override fun create(): MediaCapabilityProvider =
            WebDavMediaProvider(context, cloudMediaDao, configDao, uploadPrefDao, OwnCloudDialect())
    }
}

