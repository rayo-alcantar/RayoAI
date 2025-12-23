package com.rayoai.data.remote

import com.rayoai.data.remote.dto.FileMetadata
import com.rayoai.data.remote.dto.FileUploadResponse
import com.rayoai.data.remote.dto.UploadedFile
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Servicio de API para la Files API de Google Gemini.
 * Permite subir archivos grandes (videos, audios) usando protocolo de upload resumable.
 */
interface GeminiFilesApiService {

    /**
     * Paso 1: Inicia un upload resumable y obtiene la URL de upload.
     * La URL se encuentra en el header "x-goog-upload-url" de la respuesta.
     */
    @POST("upload/v1beta/files")
    @Headers(
        "X-Goog-Upload-Protocol: resumable",
        "X-Goog-Upload-Command: start"
    )
    suspend fun startResumableUpload(
        @Header("X-Goog-Upload-Header-Content-Length") contentLength: Long,
        @Header("X-Goog-Upload-Header-Content-Type") contentType: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body metadata: FileMetadata
    ): Response<Unit>

    /**
     * Paso 2: Sube los bytes del archivo a la URL obtenida en el paso 1.
     */
    @PUT
    @Headers("X-Goog-Upload-Command: upload, finalize")
    suspend fun uploadFileBytes(
        @Url uploadUrl: String,
        @Header("Content-Length") contentLength: Long,
        @Header("X-Goog-Upload-Offset") offset: Long,
        @Body file: RequestBody
    ): FileUploadResponse

    /**
     * Obtiene el estado actual de un archivo subido.
     * Útil para verificar cuando el estado cambia de "PROCESSING" a "ACTIVE".
     */
    @GET("v1beta/{name}")
    suspend fun getFile(
        @Path("name", encoded = true) fileName: String,
        @Header("x-goog-api-key") apiKey: String
    ): UploadedFile

    /**
     * Elimina un archivo subido.
     */
    @DELETE("v1beta/{name}")
    suspend fun deleteFile(
        @Path("name", encoded = true) fileName: String,
        @Header("x-goog-api-key") apiKey: String
    ): Response<Unit>
}
