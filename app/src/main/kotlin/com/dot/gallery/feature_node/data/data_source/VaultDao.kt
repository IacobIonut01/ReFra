package com.dot.gallery.feature_node.data.data_source

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.Vault
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface VaultDao {

    /**
     * Vault Management
     */

    @Query("SELECT * FROM vaults")
    fun getVaults(): Flow<List<Vault>>

    @Upsert
    suspend fun insertVault(vault: Vault)

    @Delete
    suspend fun deleteVaultInfo(vault: Vault)

    @Transaction
    suspend fun deleteVault(vault: Vault) {
        deleteAllMediaFromVault(vault.uuid)
        deleteVaultInfo(vault)
    }

    @Query("DELETE FROM vaults")
    suspend fun deleteAllVaults()

    /**
     * Vault Media Management
     */

    @Query("SELECT * FROM encrypted_media")
    fun getAllMedia(): Flow<List<Media.EncryptedMedia2>>

    @Query("SELECT * FROM encrypted_media WHERE uuid = :uuid")
    fun getMediaFromVault(uuid: UUID?): Flow<List<Media.EncryptedMedia2>>

    @Upsert
    suspend fun addMediaToVault(media: Media.EncryptedMedia2)

    @Query("DELETE FROM encrypted_media WHERE uuid = :uuid AND id = :id")
    suspend fun deleteMediaFromVault(uuid: UUID, id: Long): Int

    @Transaction
    suspend fun deleteMediaFromVault(media: Media.EncryptedMedia2): Boolean {
        return deleteMediaFromVault(uuid = media.uuid, id = media.id) > 0
    }

    @Query("DELETE FROM encrypted_media WHERE uuid = :uuid")
    suspend fun deleteAllMediaFromVault(uuid: UUID)

}