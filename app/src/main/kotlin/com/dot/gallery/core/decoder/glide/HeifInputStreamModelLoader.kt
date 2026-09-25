package com.dot.gallery.core.decoder.glide

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.IOException
import java.io.InputStream

/**
 * #1244: supplies the raw file stream for MediaStore image URIs that declare >10-bit depth.
 *
 * Glide resolves `Uri -> InputStream` through its built-in loaders first: at grid sizes
 * `MediaStoreThumbFetcher` re-encodes the platform thumbnail (which MediaProvider generates by
 * platform-decoding the file), and at larger sizes `UriLoader` streams the raw bytes into
 * `StreamBitmapDecoder` (platform decode). Both corrupt >10-bit HEIF/AVIF rather than failing.
 *
 * This loader is prepended ahead of those built-ins: when [HeifUriProbe] reports a depth the
 * platform can't decode safely, it returns the untouched stream so the depth-gated
 * [HeifBitmapDecoder] claims it for a software decode. Every other URI fails fast and falls
 * through to the built-in loaders unchanged.
 */
class HeifInputStreamModelLoader(
    private val contentResolver: ContentResolver
) : ModelLoader<Uri, InputStream> {

    private val depthCache = LruCache<String, Boolean>(512)

    override fun handles(model: Uri): Boolean {
        return model.scheme == ContentResolver.SCHEME_CONTENT &&
                model.authority == MediaStore.AUTHORITY
    }

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream> {
        return ModelLoader.LoadData(
            ObjectKey("heif-deep-stream:$model"),
            HeifInputStreamFetcher(contentResolver, model, ::needsSoftwareDecode)
        )
    }

    private fun needsSoftwareDecode(uri: Uri): Boolean {
        val key = uri.toString()
        depthCache.get(key)?.let { return it }
        val deep = runCatching {
            HeifUriProbe.needsSoftwareDecode(contentResolver, uri)
        }.getOrDefault(false)
        depthCache.put(key, deep)
        return deep
    }

    private class HeifInputStreamFetcher(
        private val contentResolver: ContentResolver,
        private val uri: Uri,
        private val needsSoftwareDecode: (Uri) -> Boolean
    ) : DataFetcher<InputStream> {

        private var stream: InputStream? = null

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
            if (!needsSoftwareDecode(uri)) {
                callback.onLoadFailed(IOException("Not a >10-bit HEIF/AVIF: $uri"))
                return
            }
            val s = contentResolver.openInputStream(uri)
            if (s == null) {
                callback.onLoadFailed(IOException("Null InputStream for $uri"))
                return
            }
            stream = s
            callback.onDataReady(s)
        }

        override fun cleanup() { stream?.close() }
        override fun cancel() { /* no-op */ }
        override fun getDataClass(): Class<InputStream> = InputStream::class.java
        override fun getDataSource(): DataSource = DataSource.LOCAL
    }

    class Factory(private val context: Context) : ModelLoaderFactory<Uri, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Uri, InputStream> =
            HeifInputStreamModelLoader(context.applicationContext.contentResolver)

        override fun teardown() {}
    }
}
