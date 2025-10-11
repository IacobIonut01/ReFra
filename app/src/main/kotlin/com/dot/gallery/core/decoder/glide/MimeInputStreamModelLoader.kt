package com.dot.gallery.core.decoder.glide

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.data.DataFetcher.DataCallback
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.IOException
import java.io.InputStream

/**
 * Supplies a MimeInputStream (stream + MIME) for a given content Uri. This allows decoders
 * to make format decisions from MIME, avoiding fragile header sniffing.
 */
class MimeInputStreamModelLoader(
    private val resolver: ContentResolver
) : ModelLoader<Uri, MimeInputStream> {

    override fun handles(model: Uri): Boolean = "content" == model.scheme

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: Options
    ): LoadData<MimeInputStream>? {
        return LoadData(ObjectKey(model), MimeInputStreamFetcher(resolver, model))
    }

    private class MimeInputStreamFetcher(
        private val resolver: ContentResolver,
        private val uri: Uri
    ) : DataFetcher<MimeInputStream> {
        private var stream: InputStream? = null

        override fun loadData(priority: com.bumptech.glide.Priority, callback: DataCallback<in MimeInputStream>) {
            try {
                val mime = resolver.getType(uri)
                stream = resolver.openInputStream(uri)
                val s = stream
                if (s == null) {
                    callback.onLoadFailed(IOException("Null InputStream for $uri"))
                } else {
                    callback.onDataReady(MimeInputStream(s, mime))
                }
            } catch (e: Exception) {
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() { stream?.close() }
        override fun cancel() { /* no-op */ }
        override fun getDataClass(): Class<MimeInputStream> = MimeInputStream::class.java
        override fun getDataSource(): com.bumptech.glide.load.DataSource = com.bumptech.glide.load.DataSource.LOCAL
    }

    class Factory(private val context: Context): ModelLoaderFactory<Uri, MimeInputStream> {
        override fun build(multiFactory: com.bumptech.glide.load.model.MultiModelLoaderFactory): ModelLoader<Uri, MimeInputStream> =
            MimeInputStreamModelLoader(context.contentResolver)
        override fun teardown() {}
    }
}
