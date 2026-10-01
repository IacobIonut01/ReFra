/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.immich.data.api

import com.dot.gallery.cloud.immich.data.dto.ImmichAlbumDto
import com.dot.gallery.cloud.immich.data.dto.ImmichAssetDto
import com.dot.gallery.cloud.immich.data.dto.ImmichAssetMediaResponseDto
import com.dot.gallery.cloud.immich.data.dto.ImmichBulkIdResponseDto
import com.dot.gallery.cloud.immich.data.dto.ImmichBulkUploadCheckDto
import com.dot.gallery.cloud.immich.data.dto.ImmichBulkUploadCheckResultDto
import com.dot.gallery.cloud.immich.data.dto.ImmichDeltaSyncResponseDto
import com.dot.gallery.cloud.immich.data.dto.ImmichLoginDto
import com.dot.gallery.cloud.immich.data.dto.ImmichLoginResponseDto
import com.dot.gallery.cloud.immich.data.dto.ImmichMapMarkerDto
import com.dot.gallery.cloud.immich.data.dto.ImmichMemoryDto
import com.dot.gallery.cloud.immich.data.dto.ImmichPersonDto
import com.dot.gallery.cloud.immich.data.dto.ImmichSearchDto
import com.dot.gallery.cloud.immich.data.dto.ImmichSearchResponseDto
import com.dot.gallery.cloud.immich.data.dto.ImmichServerAboutDto
import com.dot.gallery.cloud.immich.data.dto.ImmichServerStorageDto
import com.dot.gallery.cloud.immich.data.dto.ImmichSharedLinkCreateDto
import com.dot.gallery.cloud.immich.data.dto.ImmichSharedLinkDto
import com.dot.gallery.cloud.immich.data.dto.ImmichTagDto
import com.dot.gallery.cloud.immich.data.dto.ImmichUserDto
import com.dot.gallery.cloud.immich.data.dto.ImmichValidateTokenDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ImmichApiService {

    // Server
    @GET("api/server/about")
    suspend fun getServerAbout(): Response<ImmichServerAboutDto>

    @GET("api/server/storage")
    suspend fun getServerStorage(): Response<ImmichServerStorageDto>

    // Auth
    @POST("api/auth/login")
    suspend fun login(@Body loginDto: ImmichLoginDto): Response<ImmichLoginResponseDto>

    @POST("api/auth/validateToken")
    suspend fun validateToken(): Response<ImmichValidateTokenDto>

    // User
    @GET("api/users/me")
    suspend fun getCurrentUser(): Response<ImmichUserDto>

    // Assets
    @POST("api/search/metadata")
    suspend fun searchAssets(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ImmichSearchResponseDto>

    /**
     * Immich asset delta channel (`AssetDeltaSyncDto`/`AssetDeltaSyncResponseDto`):
     * returns assets added/updated since `updatedAfter` plus the ids deleted since
     * then — the only endpoint that can report remote deletions without a full scan.
     * Absent on old servers (pre sync-v1) and superseded by `sync/stream` on the newest
     * ones; non-2xx answers must fall back to a metadata-search delta.
     */
    @POST("api/sync/delta-sync")
    suspend fun deltaSync(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ImmichDeltaSyncResponseDto>

    @GET("api/assets/{id}")
    suspend fun getAssetById(@Path("id") id: String): Response<ImmichAssetDto>

    @HTTP(method = "DELETE", path = "api/assets", hasBody = true)
    suspend fun deleteAssets(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Unit>

    @PUT("api/assets/{id}")
    suspend fun updateAsset(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ImmichAssetDto>

    @Multipart
    @POST("api/assets")
    suspend fun uploadAsset(
        @Part file: MultipartBody.Part,
        @Part("deviceAssetId") deviceAssetId: RequestBody,
        @Part("deviceId") deviceId: RequestBody,
        @Part("fileCreatedAt") fileCreatedAt: RequestBody,
        @Part("fileModifiedAt") fileModifiedAt: RequestBody,
        @Header("x-immich-checksum") checksum: String? = null
    ): Response<ImmichAssetMediaResponseDto>

    @POST("api/assets/bulk-upload-check")
    suspend fun bulkUploadCheck(
        @Body body: ImmichBulkUploadCheckDto
    ): Response<ImmichBulkUploadCheckResultDto>

    @POST("api/trash/restore/assets")
    suspend fun restoreAssets(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Unit>

    // Albums
    @GET("api/albums")
    suspend fun getAlbums(
        @Query("shared") shared: Boolean? = null
    ): Response<List<ImmichAlbumDto>>

    @GET("api/albums/{id}")
    suspend fun getAlbumById(@Path("id") id: String): Response<ImmichAlbumDto>

    @POST("api/albums")
    suspend fun createAlbum(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<ImmichAlbumDto>

    @PATCH("api/albums/{id}")
    suspend fun updateAlbum(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ImmichAlbumDto>

    @DELETE("api/albums/{id}")
    suspend fun deleteAlbum(@Path("id") id: String): Response<Unit>

    @PUT("api/albums/{id}/users")
    suspend fun updateAlbumUsers(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ImmichAlbumDto>

    @PUT("api/albums/{id}/assets")
    suspend fun addAssetsToAlbum(
        @Path("id") albumId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<ImmichBulkIdResponseDto>>

    @HTTP(method = "DELETE", path = "api/albums/{id}/assets", hasBody = true)
    suspend fun removeAssetsFromAlbum(
        @Path("id") albumId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<ImmichBulkIdResponseDto>>

    // People
    @GET("api/people")
    suspend fun getPeople(
        @Query("withHidden") withHidden: Boolean = false
    ): Response<ImmichPeopleResponse>

    @GET("api/people/{id}/assets")
    suspend fun getPersonAssets(@Path("id") personId: String): Response<List<ImmichAssetDto>>

    // Map
    @GET("api/map/markers")
    suspend fun getMapMarkers(
        @Query("isArchived") isArchived: Boolean = false,
        @Query("isFavorite") isFavorite: Boolean? = null,
        @Query("fileCreatedAfter") fileCreatedAfter: String? = null
    ): Response<List<ImmichMapMarkerDto>>

    // Search
    @POST("api/search/smart")
    suspend fun smartSearch(@Body searchDto: ImmichSearchDto): Response<ImmichSearchResponseDto>

    // Tags
    @GET("api/tags")
    suspend fun getTags(): Response<List<ImmichTagDto>>

    @POST("api/search/metadata")
    suspend fun metadataSearch(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<ImmichSearchResponseDto>

    // Shared Links
    @POST("api/shared-links")
    suspend fun createSharedLink(
        @Body body: ImmichSharedLinkCreateDto
    ): Response<ImmichSharedLinkDto>

    @GET("api/shared-links")
    suspend fun getSharedLinks(): Response<List<ImmichSharedLinkDto>>

    @GET("api/shared-links/{id}")
    suspend fun getSharedLinkById(@Path("id") id: String): Response<ImmichSharedLinkDto>

    @retrofit2.http.PATCH("api/shared-links/{id}")
    suspend fun updateSharedLink(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ImmichSharedLinkDto>

    @DELETE("api/shared-links/{id}")
    suspend fun deleteSharedLink(@Path("id") id: String): Response<Unit>

    // People – update
    @PUT("api/people/{id}")
    suspend fun updatePerson(
        @Path("id") personId: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ImmichPersonDto>

    // Trash – bulk operations
    @POST("api/trash/empty")
    suspend fun emptyTrash(): Response<Unit>

    @POST("api/trash/restore")
    suspend fun restoreAllTrash(): Response<Unit>

    // Memories
    @GET("api/memories")
    suspend fun getMemories(): Response<List<ImmichMemoryDto>>
}

data class ImmichPeopleResponse(
    val total: Int = 0,
    val visible: Int = 0,
    val people: List<ImmichPersonDto> = emptyList()
)
