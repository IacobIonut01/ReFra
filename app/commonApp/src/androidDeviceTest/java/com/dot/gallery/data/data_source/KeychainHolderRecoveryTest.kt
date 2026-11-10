/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.data_source

import android.content.Context
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.domain.model.Vault
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

/**
 * Vault data is keyed by Android Keystore keys, which a backup never carries. Vault folders
 * therefore live outside backed-up storage, and a restored EncryptedFile keyset, which made
 * every later vault operation fail with AEADBadTagException, is recovered from.
 */
@RunWith(AndroidJUnit4::class)
class KeychainHolderRecoveryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker: File get() = File(context.noBackupFilesDir, KeychainHolder.KEYSET_VERIFIED_MARKER)
    private val content = byteArrayOf(1, 2, 3, 4, 5)

    @Before
    fun setUp() = reset()

    @After
    fun tearDown() = reset()

    @Test
    fun vaultFoldersInFilesDirMoveToNoBackupStorage() {
        val vault = Vault(name = "legacy location")
        val old = File(context.filesDir, vault.uuid.toString()).apply { mkdirs() }
        File(old, KeychainHolder.VAULT_INFO_FILE_NAME).writeText("info")
        File(old, "7.enc").writeBytes(content)

        val holder = KeychainHolder(context).apply { prepareStorage() }

        assertFalse(old.exists())
        val moved = holder.vaultFolder(vault)
        assertTrue(moved.path.startsWith(context.noBackupFilesDir.path))
        assertArrayEquals(content, File(moved, "7.enc").readBytes())
        assertTrue(File(moved, KeychainHolder.VAULT_INFO_FILE_NAME).exists())
    }

    @Test
    fun restoredKeysetIsClearedAndPortableVaultReopensWithItsExportedKey() {
        val portable = Vault(name = "portable")
        val deviceLocked = Vault(name = "device locked")
        val before = KeychainHolder(context)
        before.writeVaultInfo(portable, transferable = true)
        before.writeVaultInfo(deviceLocked)
        val exported = before.exportDataKeyBase64(portable)!!
        with(before) { portable.mediaFile(1).writeBytes(encryptPortableContent(portable, content)) }
        // Restore onto a reinstall: the keyset is back, the Keystore master key is not.
        deleteMasterKey()
        marker.delete()

        val after = KeychainHolder(context).apply { prepareStorage() }

        assertFalse(after.vaultFolder(deviceLocked).exists())
        assertTrue(with(after) { portable.mediaFile(1).exists() })
        assertNull(after.loadDataKey(portable))
        assertTrue(after.importPortableVault(portable, exported))
        val encrypted = with(after) { portable.mediaFile(1).readBytes() }
        assertArrayEquals(content, after.decryptPortableContent(portable, encrypted))
        val created = Vault(name = "created after restore")
        after.writeVaultInfo(created, transferable = true)
        assertNotNull(after.loadDataKey(created))
        assertTrue(marker.exists())
    }

    @Test
    fun healthyKeysetIsLeftAlone() {
        val portable = Vault(name = "portable")
        val deviceLocked = Vault(name = "device locked")
        val holder = KeychainHolder(context)
        holder.writeVaultInfo(portable, transferable = true)
        holder.writeVaultInfo(deviceLocked)
        val key = holder.loadDataKey(portable)
        marker.delete()

        holder.prepareStorage()

        assertTrue(holder.vaultFolder(deviceLocked).exists())
        assertArrayEquals(key, holder.loadDataKey(portable))
        assertTrue(marker.exists())
    }

    @Test
    fun importReplacesAnUnreadableDataKey() {
        val portable = Vault(name = "portable")
        val holder = KeychainHolder(context)
        holder.writeVaultInfo(portable, transferable = true)
        val exported = holder.exportDataKeyBase64(portable)!!
        val key = holder.loadDataKey(portable)
        File(holder.vaultFolder(portable), KeychainHolder.DATA_KEY_FILE_NAME).writeBytes(ByteArray(64))

        assertTrue(holder.importPortableVault(portable, exported))

        assertArrayEquals(key, holder.loadDataKey(portable))
    }

    private fun deleteMasterKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
    }

    private fun reset() {
        File(context.noBackupFilesDir, KeychainHolder.VAULT_ROOT_DIR).deleteRecursively()
        marker.delete()
        context.getSharedPreferences(KeychainHolder.KEYSET_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
