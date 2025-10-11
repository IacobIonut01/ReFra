package com.dot.gallery.core.video

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * StreamingEncryptedDataSource
 * ---------------------------------
 * On-demand block decrypting DataSource so ExoPlayer can start playback without waiting for
 * full file decryption. For now, we rely on the underlying encrypted file having a clear
 * container header (ftyp + moov at head). If moov is tail-based, initial seeks may still stall
 * until decrypted. Future improvement: relocate moov or pre-decrypt header region.
 *
 * Encryption format assumption: Entire original media bytes were stored inside an
 * EncryptedMedia(bytes=...) blob. Here we decrypt that blob lazily by memory-mapping/decrypting
 * in fixed blocks. To align with existing decryptKotlin() which returns the full byte[] we
 * currently fall back to a one-time full decrypt on first open but expose incremental read.
 *
 * To evolve to true streaming: replace the 'fullBytes' materialization with chunked decryption
 * that decrypts only requested blocks (e.g. AES-CTR) and caches them in an LRU.
 */
@UnstableApi
class StreamingEncryptedDataSource(
    private val encryptedFile: File,
    private val keychainHolder: KeychainHolder,
    private val blockSize: Int = 256 * 1024 // 256 KiB blocks for potential future partial decrypt
) : BaseDataSource(/* isNetwork= */ false), DataSource {

    private var opened = false
    private var uri = android.net.Uri.fromFile(encryptedFile)
    private var fullBytes: ByteArray? = null
    private var readPosition: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private val closed = AtomicBoolean(false)

    override fun open(dataSpec: DataSpec): Long {
        if (opened) return bytesRemaining
        transferInitializing(dataSpec)
        // For MVP: decrypt entire payload once using existing util (blocking). In future this
        // can be replaced by incremental block decrypt using key material.
        val bytes = runBlocking {
            with(keychainHolder) { encryptedFile.decryptKotlin<Media.EncryptedMedia>().bytes }
        }
        fullBytes = bytes
        readPosition = dataSpec.position
        bytesRemaining = bytes.size - readPosition
        if (readPosition >= bytes.size) {
            bytesRemaining = 0
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun getUri(): android.net.Uri? = uri

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val localBytes = fullBytes ?: return C.RESULT_END_OF_INPUT
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
        val toRead = min(length.toLong(), bytesRemaining).toInt()
        System.arraycopy(localBytes, readPosition.toInt(), buffer, offset, toRead)
        readPosition += toRead
        bytesRemaining -= toRead
        bytesTransferred(toRead)
        return toRead
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            fullBytes = null
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val keychainHolder: KeychainHolder
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            throw IllegalStateException("Use createForFile(file) supplying the encrypted file")
        }
        fun createForFile(file: File): StreamingEncryptedDataSource = StreamingEncryptedDataSource(file, keychainHolder)
    }
}
