package com.dot.gallery.core.decoder.glide

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * ModelLoader producing a streaming EncryptedMediaSource. This coexists with legacy byte-array path
 * until all decoders migrate.
 */
class EncryptedStreamingFileLoader(
    private val context: Context
) : ModelLoader<File, EncryptedMediaSource> {

    override fun handles(model: File): Boolean = isEncryptedVaultFile(model)

    override fun buildLoadData(
        model: File,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<EncryptedMediaSource>? {
        if (!handles(model)) return null
        return ModelLoader.LoadData(
            StreamVaultKey(model.path),
            EncryptedSourceFetcher(model, context)
        )
    }

    class Factory(private val context: Context) : ModelLoaderFactory<File, EncryptedMediaSource> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<File, EncryptedMediaSource> =
            EncryptedStreamingFileLoader(context.applicationContext)

        override fun teardown() = Unit
    }
}

class EncryptedStreamingUriLoader(
    private val context: Context
) : ModelLoader<Uri, EncryptedMediaSource> {
    override fun handles(model: Uri): Boolean {
        val file = if (model.scheme == "file") model.path?.let { File(it) } else null
        return file?.let { isEncryptedVaultFile(it) } == true
    }

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<EncryptedMediaSource>? {
        val file = model.path?.let { File(it) } ?: return null
        if (!handles(model)) return null
        return ModelLoader.LoadData(
            StreamVaultKey(file.path),
            EncryptedSourceFetcher(file, context)
        )
    }

    class Factory(private val context: Context) : ModelLoaderFactory<Uri, EncryptedMediaSource> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Uri, EncryptedMediaSource> =
            EncryptedStreamingUriLoader(context.applicationContext)
        override fun teardown() = Unit
    }
}

/* Disk cache key */
data class StreamVaultKey(private val path: String) : com.bumptech.glide.load.Key {
    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(path.toByteArray(Charsets.UTF_8))
    }
}

/* Fetcher returning EncryptedMediaSource */
private class EncryptedSourceFetcher(
    private val file: File,
    private val context: Context
) : DataFetcher<EncryptedMediaSource> {
    private var data: EncryptedMediaSource? = null
    private var cancelled = false

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in EncryptedMediaSource>) {
        if (cancelled) return
        try {
            val src = createEncryptedMediaSource(context, file)
            data = src
            callback.onDataReady(src)
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() { data = null }
    override fun cancel() { cancelled = true }
    override fun getDataClass(): Class<EncryptedMediaSource> = EncryptedMediaSource::class.java
    override fun getDataSource(): DataSource = DataSource.LOCAL
}

/**
 * Adapter loader converting EncryptedMediaSource -> InputStream so Glide's normal decoders can run.
 */
class EncryptedSourceToStreamLoader : ModelLoader<EncryptedMediaSource, InputStream> {
    override fun handles(model: EncryptedMediaSource): Boolean = !model.isVideo

    override fun buildLoadData(
        model: EncryptedMediaSource,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? = ModelLoader.LoadData(
        object : com.bumptech.glide.load.Key { // ephemeral key includes path & target size
            override fun updateDiskCacheKey(messageDigest: MessageDigest) {
                messageDigest.update((model.file.path + "#" + width + "x" + height).toByteArray())
            }
        },
        object : DataFetcher<InputStream> {
            private var stream: InputStream? = null
            private var cancelled = false
            override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
                if (cancelled) return
                try {
                    stream = model.openStream()
                    callback.onDataReady(stream)
                } catch (e: Exception) {
                    callback.onLoadFailed(e)
                }
            }
            override fun cleanup() { stream?.close(); stream = null }
            override fun cancel() { cancelled = true }
            override fun getDataClass(): Class<InputStream> = InputStream::class.java
            override fun getDataSource(): DataSource = DataSource.LOCAL
        }
    )

    class Factory : ModelLoaderFactory<EncryptedMediaSource, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<EncryptedMediaSource, InputStream> = EncryptedSourceToStreamLoader()
        override fun teardown() = Unit
    }
}
