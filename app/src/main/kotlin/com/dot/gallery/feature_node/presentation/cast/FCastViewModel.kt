/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.cast

import android.content.Context
import androidx.lifecycle.ViewModel
import com.dot.gallery.feature_node.data.data_source.KeychainHolder
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isEncrypted
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class FCastViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val session: FCastSession
) : ViewModel() {

    val state: StateFlow<CastSessionState> = session.state

    val isConnected: Boolean get() = session.isConnected
    val isCasting: Boolean get() = session.isCasting

    fun isCastAvailable(): Boolean = session.isCastAvailable()
    fun checkPermissions(): List<CastPermission> = session.checkPermissions()
    fun hasAllPermissions(): Boolean = session.hasAllPermissions()

    fun startDiscovery() = session.startDiscovery()
    fun stopDiscovery() = session.stopDiscovery()

    fun connect(device: FCastDevice) {
        session.stopDiscovery()
        session.connect(device)
    }

    fun disconnect() = session.disconnect()

    fun <T : Media> castMedia(media: T) {
        if (media.isEncrypted) {
            castEncryptedMedia(media)
        } else {
            session.castMedia(media)
        }
    }

    private fun <T : Media> castEncryptedMedia(media: T) {
        val keychainHolder = KeychainHolder(appContext)
        try {
            val encryptedFile = File(media.getUri().path!!)
            val decrypted = keychainHolder.decryptVaultMedia(encryptedFile)
            val tempFile = File.createTempFile("fcast_${media.id}", null, appContext.cacheDir)
            decrypted.openStream().use { input ->
                FileOutputStream(tempFile).use { out ->
                    input.copyTo(out)
                }
            }
            session.castFile(
                file = tempFile,
                mimeType = media.mimeType,
                label = media.label,
                mediaId = media.id
            )
        } catch (e: Exception) {
            // Fallback: try as regular media
            session.castMedia(media)
        }
    }

    fun togglePlayPause() = session.togglePlayPause()
    fun pause() = session.pause()
    fun resume() = session.resume()
    fun seek(timeSeconds: Double) = session.seek(timeSeconds)
    fun setVolume(volume: Double) = session.setVolume(volume)
    fun setSpeed(speed: Double) = session.setSpeed(speed)
    fun stopCasting() = session.stop()
}
