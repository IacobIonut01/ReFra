package com.dot.gallery.core.decoder.glide

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.data.DataFetcher.DataCallback
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.dot.gallery.core.decoder.format.ImageFormatSniffer
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Supplies a MimeInputStream (stream + MIME) for a given content Uri. This allows decoders
 * to make format decisions from MIME, avoiding fragile header sniffing.
 */
class MimeInputStreamModelLoader(
    private val context: Context,
    private val resolver: ContentResolver
) : ModelLoader<Uri, MimeInputStream> {

    override fun handles(model: Uri): Boolean = "content" == model.scheme

    override fun buildLoadData(
        model: Uri,
        width: Int,
        height: Int,
        options: Options
    ): LoadData<MimeInputStream>? {
        return LoadData(ObjectKey(model), MimeInputStreamFetcher(context, resolver, model))
    }

    private class MimeInputStreamFetcher(
        private val context: Context,
        private val resolver: ContentResolver,
        private val uri: Uri
    ) : DataFetcher<MimeInputStream> {
        private var stream: InputStream? = null

        override fun loadData(priority: Priority, callback: DataCallback<in MimeInputStream>) {
            try {
                val mime = resolver.getType(uri)
                // Diagnostic (debug builds only): log each distinct MIME seen on the content-image
                // path once, so undecodable formats reaching the grid (e.g. RAW files MediaStore
                // tags with a generic/unexpected MIME) can be identified.
                if (mime != null && loggedMimes.add(mime)) {
                    com.dot.gallery.feature_node.presentation.util.printDebug("MimeInputStream: content MIME '$mime' (e.g. $uri)")
                }
                var rawStream = resolver.openInputStream(uri)
                val s = rawStream
                if (s == null) {
                    callback.onLoadFailed(IOException("Null InputStream for $uri"))
                    return
                }
                // #1054: MediaStore sometimes reports a RAW/TIFF MIME for a file that is actually a
                // standard JPEG/PNG/WebP/GIF/BMP (e.g. RawTherapee exports). Trusting that MIME sends
                // the file to the embedded-preview/EXIF-thumbnail extractor, so it renders as a tiny
                // blurry square. Only when the reported MIME is already suspicious do we peek the
                // header and, if the bytes are a natively-decodable image, correct the MIME
                // so the RAW/TIFF decoders decline and Glide uses its default decode path. Normal
                // images skip this entirely (zero cost on the fling hot path).
                // #1244: providers can also report HEIF/AVIF under a missing/generic MIME
                // (null, application/octet-stream) — without the header peek the dedicated HEIF
                // decoders decline and the file falls through to platform decode paths, which
                // silently corrupt >10-bit streams. An ftyp-brand sniff re-routes them.
                var effectiveMime = mime
                if (RawMime.isCameraRaw(mime) || TiffMime.isTiff(mime) || isGenericMime(mime)) {
                    val buffered = if (s.markSupported()) s else BufferedInputStream(s)
                    rawStream = buffered
                    buffered.mark(HEADER_PEEK_BYTES)
                    val head = ByteArray(HEADER_PEEK_BYTES)
                    val n = buffered.read(head)
                    buffered.reset()
                    val sniffed = if (n > 0) {
                        ImageFormatSniffer.standardMimeFor(head, n) ?: heifMimeFor(head, n)
                    } else null
                    if (sniffed != null) {
                        if (correctionLogged.add("$mime->$sniffed")) {
                            com.dot.gallery.feature_node.presentation.util.printDebug(
                                "MimeInputStream: MIME correction reported '$mime' but header is '$sniffed' (e.g. $uri)"
                            )
                        }
                        effectiveMime = sniffed
                    }
                }
                stream = rawStream
                callback.onDataReady(MimeInputStream(rawStream, effectiveMime, uri, context))
            } catch (e: Exception) {
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() { stream?.close() }
        override fun cancel() { /* no-op */ }
        override fun getDataClass(): Class<MimeInputStream> = MimeInputStream::class.java
        override fun getDataSource(): com.bumptech.glide.load.DataSource = com.bumptech.glide.load.DataSource.LOCAL

        private companion object {
            // Sized for the format sniff: 32 B covers the magic-byte checks and the canonical
            // ftyp-at-offset-4 layout; 256 B matches HeifSniffer's deeper brand window for
            // non-standard containers.
            const val HEADER_PEEK_BYTES = 256
            val loggedMimes = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
            val correctionLogged = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

            /**
             * MIME values that carry no usable format information. Providers (MediaStore, SAF,
             * OEM gallery backends) regularly report AVIF/HEIC this way — e.g. files scanned as
             * `MEDIA_TYPE_NONE` or served through a `file/` collection URI.
             */
            private val GENERIC_MIMES = setOf(
                "application/octet-stream",
                "application/binary",
                "application/x-binary",
                "application/unknown",
                "*/*",
                "image/*",
            )

            fun isGenericMime(mime: String?): Boolean =
                mime.isNullOrBlank() || mime.lowercase() in GENERIC_MIMES

            /** HEIF-family brand → the MIME our HEIF decoders expect, or null for non-HEIF bytes. */
            fun heifMimeFor(header: ByteArray, length: Int): String? =
                when (HeifSniffer.findBrand(header, length)?.lowercase()) {
                    null -> null
                    "avif", "avis" -> "image/avif"
                    else -> "image/heif"
                }
        }
    }

    class Factory(private val context: Context): ModelLoaderFactory<Uri, MimeInputStream> {
        override fun build(multiFactory: com.bumptech.glide.load.model.MultiModelLoaderFactory): ModelLoader<Uri, MimeInputStream> =
            MimeInputStreamModelLoader(context.applicationContext, context.contentResolver)
        override fun teardown() {}
    }
}
