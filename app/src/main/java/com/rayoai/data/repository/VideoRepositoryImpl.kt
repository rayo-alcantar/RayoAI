package com.rayoai.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.JsonParser
import com.rayoai.R
import com.rayoai.core.ResultWrapper
import com.rayoai.data.remote.GeminiApiService
import com.rayoai.data.remote.GeminiFilesApiService
import com.rayoai.data.remote.dto.ContentDto
import com.rayoai.data.remote.dto.FileDataDto
import com.rayoai.data.remote.dto.FileInfo
import com.rayoai.data.remote.dto.FileMetadata
import com.rayoai.data.remote.dto.GeminiRequest
import com.rayoai.data.remote.dto.GenerationConfigDto
import com.rayoai.data.remote.dto.PartDto
import com.rayoai.data.remote.dto.SystemInstruction
import com.rayoai.data.remote.dto.ThinkingConfigDto
import com.rayoai.data.remote.dto.UploadedFile
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.VideoAnalysisResult
import com.rayoai.domain.model.VideoLinkValidator
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import java.io.File
import java.text.Normalizer
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val geminiFilesApiService: GeminiFilesApiService,
    private val geminiApiService: GeminiApiService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val httpClient: OkHttpClient
) : VideoRepository {

    override suspend fun uploadAndAnalyzeVideo(
        uri: Uri,
        context: Context,
        systemPrompt: String
    ): ResultWrapper<String> = withContext(Dispatchers.IO) {
        val apiKey = userPreferencesRepository.apiKey.firstOrNull()
            ?: return@withContext ResultWrapper.Error(context.getString(R.string.video_upload_missing_api_key))
        val (fileName, sizeBytes, mimeType) = getVideoInfo(context, uri)
        if (sizeBytes > MAX_VIDEO_BYTES) {
            return@withContext ResultWrapper.Error(context.getString(R.string.scan_video_too_large))
        }
        val body = contentUriRequestBody(context, uri, mimeType)
            ?: return@withContext ResultWrapper.Error(context.getString(R.string.video_upload_read_failed))
        uploadAnalyzeAndCleanup(
            apiKey = apiKey,
            context = context,
            displayName = fileName,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            body = body,
            systemPrompt = systemPrompt
        )
    }

    override suspend fun analyzeVideoFromUrl(
        url: String,
        context: Context,
        systemPrompt: String,
        onStatus: (String) -> Unit
    ): ResultWrapper<VideoAnalysisResult> = withContext(Dispatchers.IO) {
        val supportedUrl = VideoLinkValidator.extractSupportedUrl(url)
            ?: return@withContext ResultWrapper.Error(context.getString(R.string.video_link_unsupported))
        val apiKey = userPreferencesRepository.apiKey.firstOrNull()
            ?: return@withContext ResultWrapper.Error(context.getString(R.string.video_upload_missing_api_key))

        if (VideoLinkValidator.isYouTube(supportedUrl)) {
            onStatus(context.getString(R.string.video_link_youtube_direct))
            val result = generateWithFallback(
                apiKey = apiKey,
                context = context,
                systemPrompt = systemPrompt,
                fileData = FileDataDto(fileUri = supportedUrl)
            )
            return@withContext when (result) {
                is ResultWrapper.Success -> ResultWrapper.Success(
                    VideoAnalysisResult(
                        displayName = context.getString(R.string.video_link_youtube_name),
                        sourceUri = supportedUrl,
                        description = result.data,
                        durationSeconds = 0,
                        sizeBytes = 0L
                    )
                )
                is ResultWrapper.Error -> result
                ResultWrapper.Loading -> ResultWrapper.Loading
            }
        }

        var tempFile: File? = null
        try {
            onStatus(context.getString(R.string.video_link_resolving))
            val directUrl = resolveDirectVideoUrl(supportedUrl, context)
                ?: return@withContext ResultWrapper.Error(context.getString(R.string.video_link_resolve_failed))

            onStatus(context.getString(R.string.video_link_downloading))
            tempFile = downloadVideoToCache(directUrl, context)
            if (tempFile.length() > MAX_VIDEO_BYTES) {
                return@withContext ResultWrapper.Error(context.getString(R.string.scan_video_too_large))
            }

            onStatus(context.getString(R.string.scan_video_uploading))
            val analysis = uploadAnalyzeAndCleanup(
                apiKey = apiKey,
                context = context,
                displayName = tempFile.name,
                sizeBytes = tempFile.length(),
                mimeType = "video/mp4",
                body = tempFile.asRequestBody("video/mp4".toMediaTypeOrNull()),
                systemPrompt = systemPrompt,
                onStatus = onStatus
            )

            when (analysis) {
                is ResultWrapper.Success -> ResultWrapper.Success(
                    VideoAnalysisResult(
                        displayName = platformDisplayName(supportedUrl, context),
                        sourceUri = supportedUrl,
                        description = analysis.data,
                        durationSeconds = 0,
                        sizeBytes = tempFile.length()
                    )
                )
                is ResultWrapper.Error -> analysis
                ResultWrapper.Loading -> ResultWrapper.Loading
            }
        } catch (e: Exception) {
            ResultWrapper.Error(context.getString(R.string.video_process_error, e.message))
        } finally {
            tempFile?.delete()
        }
    }

    override fun isSupportedVideoUrl(url: String): Boolean = VideoLinkValidator.isSupportedUrl(url)

    private suspend fun uploadAnalyzeAndCleanup(
        apiKey: String,
        context: Context,
        displayName: String,
        sizeBytes: Long,
        mimeType: String,
        body: RequestBody,
        systemPrompt: String,
        onStatus: (String) -> Unit = {}
    ): ResultWrapper<String> {
        var uploadedFile: UploadedFile? = null
        return try {
            val uploadResponse = geminiFilesApiService.startResumableUpload(
                contentLength = sizeBytes,
                contentType = mimeType,
                apiKey = apiKey,
                metadata = FileMetadata(FileInfo(displayName.toAsciiSafeName()))
            )
            val uploadUrl = uploadResponse.headers()["x-goog-upload-url"]
                ?: return ResultWrapper.Error(context.getString(R.string.video_upload_url_missing))

            val uploadResult = geminiFilesApiService.uploadFileBytes(
                uploadUrl = uploadUrl,
                contentLength = sizeBytes,
                offset = 0,
                file = body
            )
            uploadedFile = uploadResult.file
            onStatus(context.getString(R.string.scan_video_processing))

            val activeFile = waitForFileReadyOrSpeculate(
                apiKey = apiKey,
                context = context,
                uploadedFile = uploadResult.file,
                systemPrompt = systemPrompt,
                mimeType = mimeType
            )
            when (activeFile) {
                is WaitResult.Active -> generateWithFallback(
                    apiKey = apiKey,
                    context = context,
                    systemPrompt = systemPrompt,
                    fileData = FileDataDto(mimeType = mimeType, fileUri = activeFile.file.uri)
                )
                is WaitResult.Generated -> ResultWrapper.Success(activeFile.description)
                is WaitResult.Error -> ResultWrapper.Error(activeFile.message)
            }
        } catch (e: Exception) {
            ResultWrapper.Error(context.getString(R.string.video_process_error, e.message))
        } finally {
            uploadedFile?.let { file ->
                runCatching { geminiFilesApiService.deleteFile(file.name, apiKey) }
            }
        }
    }

    private suspend fun waitForFileReadyOrSpeculate(
        apiKey: String,
        context: Context,
        uploadedFile: UploadedFile,
        systemPrompt: String,
        mimeType: String
    ): WaitResult {
        var fileState = uploadedFile
        repeat(MAX_PROCESSING_ATTEMPTS) { attempt ->
            if (fileState.state == "ACTIVE") return WaitResult.Active(fileState)
            if (fileState.state == "FAILED") {
                return WaitResult.Error(context.getString(R.string.video_processing_failed))
            }
            if (attempt >= SPECULATIVE_ATTEMPT && attempt % 15 == 0) {
                val speculative = generateWithFallback(
                    apiKey = apiKey,
                    context = context,
                    systemPrompt = systemPrompt,
                    fileData = FileDataDto(mimeType = mimeType, fileUri = fileState.uri)
                )
                if (speculative is ResultWrapper.Success) {
                    return WaitResult.Generated(speculative.data)
                }
            }
            delay(1000)
            fileState = runCatching {
                geminiFilesApiService.getFile(uploadedFile.name, apiKey)
            }.getOrDefault(fileState)
        }
        return WaitResult.Error(context.getString(R.string.video_processing_timeout))
    }

    private suspend fun generateWithFallback(
        apiKey: String,
        context: Context,
        systemPrompt: String,
        fileData: FileDataDto
    ): ResultWrapper<String> {
        val preferredModel = userPreferencesRepository.defaultModel.firstOrNull()
            ?.ifBlank { GeminiModelConfig.DEFAULT_MODEL }
            ?: GeminiModelConfig.DEFAULT_MODEL
        val models = (VIDEO_MODEL_FALLBACK_ORDER + preferredModel)
            .filter { it.isNotBlank() }
            .distinct()
        var lastError = context.getString(R.string.scan_pdf_unknown_error)

        for (model in models) {
            val request = GeminiRequest(
                systemInstruction = SystemInstruction(parts = listOf(PartDto(text = systemPrompt))),
                contents = listOf(
                    ContentDto(
                        role = "user",
                        parts = listOf(
                            PartDto(text = context.getString(R.string.video_user_prompt)),
                            PartDto(fileData = fileData)
                        )
                    )
                ),
                generationConfig = GenerationConfigDto(
                    thinkingConfig = ThinkingConfigDto(includeThoughts = true, thinkingLevel = "MINIMAL")
                )
            )
            val response = try {
                geminiApiService.generateContent(apiKey = apiKey, model = model, request = request)
            } catch (error: Exception) {
                lastError = error.message ?: lastError
                continue
            }
            if (!response.isSuccessful || response.body() == null) {
                lastError = context.getString(R.string.video_api_error, response.code(), response.message())
                continue
            }
            val candidate = response.body()!!.candidates?.firstOrNull()
            val description = candidate?.content?.parts
                ?.filter { it.thought != true }
                ?.mapNotNull { it.text }
                ?.joinToString("")
            if (!description.isNullOrBlank()) return ResultWrapper.Success(description)
            lastError = context.getString(
                R.string.video_model_empty_response,
                candidate?.finishReason ?: "UNKNOWN"
            )
        }
        return ResultWrapper.Error(lastError)
    }

    private fun resolveDirectVideoUrl(url: String, context: Context): String? {
        return when {
            url.contains("tiktok.com", ignoreCase = true) -> resolveTikTok(url)
            url.contains("instagram.com", ignoreCase = true) -> resolveInstagram(url)
            url.contains("x.com", ignoreCase = true) || url.contains("twitter.com", ignoreCase = true) -> resolveX(url)
            else -> null
        } ?: throw IllegalStateException(context.getString(R.string.video_link_resolve_failed))
    }

    private fun resolveTikTok(url: String): String? {
        val body = FormBody.Builder()
            .add("url", url)
            .add("hd", "1")
            .build()
        val request = Request.Builder()
            .url("https://www.tikwm.com/api/")
            .browserHeaders()
            .post(body)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            val data = json.getAsJsonObject("data") ?: return@use null
            data.get("hdplay")?.asString?.takeIf { it.isNotBlank() }
                ?: data.get("play")?.asString?.takeIf { it.isNotBlank() }
        }
    }

    private fun resolveInstagram(url: String): String? {
        val landingRequest = Request.Builder().url("https://indown.io/en1").browserHeaders().build()
        val token = httpClient.newCall(landingRequest).execute().use { response ->
            if (!response.isSuccessful) return@use null
            TOKEN_REGEX.find(response.body?.string().orEmpty())?.groupValues?.getOrNull(1)
        } ?: return null

        val body = FormBody.Builder()
            .add("_token", token)
            .add("link", url)
            .add("locale", "en")
            .add("p", "i")
            .build()
        val request = Request.Builder()
            .url("https://indown.io/download")
            .browserHeaders("https://indown.io/en1")
            .post(body)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            MP4_REGEX.find(response.body?.string().orEmpty())?.firstCapturedGroup()?.decodeHtml()
        }
    }

    private fun resolveX(url: String): String? {
        val body = FormBody.Builder()
            .add("q", url)
            .add("lang", "en")
            .build()
        val request = Request.Builder()
            .url("https://savetwitter.net/api/ajaxSearch")
            .browserHeaders("https://savetwitter.net/")
            .post(body)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val raw = response.body?.string().orEmpty()
            val html = runCatching {
                val json = JsonParser.parseString(raw).asJsonObject
                json.get("data")?.asString ?: raw
            }.getOrDefault(raw)
            MP4_REGEX.find(html)?.firstCapturedGroup()?.decodeHtml()
                ?: ESCAPED_MP4_REGEX.find(raw)?.firstCapturedGroup()?.decodeHtml()
        }
    }

    private fun downloadVideoToCache(url: String, context: Context): File {
        val request = Request.Builder()
            .url(url)
            .browserHeaders()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.video_link_download_failed))
            val body = response.body ?: error(context.getString(R.string.video_link_download_failed))
            val contentType = body.contentType()?.toString().orEmpty()
            if (contentType.contains("text/html", ignoreCase = true)) {
                error(context.getString(R.string.video_link_download_failed))
            }
            if (body.contentLength() > MAX_VIDEO_BYTES) error(context.getString(R.string.scan_video_too_large))
            val file = File.createTempFile("rayoai_video_", ".mp4", context.cacheDir)
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MAX_VIDEO_BYTES) error(context.getString(R.string.scan_video_too_large))
                        output.write(buffer, 0, read)
                    }
                }
            }
            file
        }
    }

    private fun contentUriRequestBody(context: Context, uri: Uri, mimeType: String): RequestBody? {
        return object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun writeTo(sink: BufferedSink) {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException(context.getString(R.string.video_upload_read_failed))
                input.use { stream -> stream.copyTo(sink.outputStream()) }
            }
        }
    }

    private fun getVideoInfo(context: Context, uri: Uri): Triple<String, Long, String> {
        var fileName = "video.mp4"
        var sizeBytes = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
            }
        }
        return Triple(fileName, sizeBytes, context.contentResolver.getType(uri) ?: "video/mp4")
    }

    private fun platformDisplayName(url: String, context: Context): String {
        return when {
            url.contains("tiktok.com", ignoreCase = true) -> context.getString(R.string.video_link_tiktok_name)
            url.contains("instagram.com", ignoreCase = true) -> context.getString(R.string.video_link_instagram_name)
            else -> context.getString(R.string.video_link_x_name)
        }
    }

    private fun String.toAsciiSafeName(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
        return normalized.ifBlank { "video.mp4" }
    }

    private fun String.decodeHtml(): String {
        return replace("&amp;", "&")
            .replace("\\/", "/")
            .replace("&quot;", "\"")
            .replace("\\u0026", "&")
    }

    private fun MatchResult.firstCapturedGroup(): String? {
        return groupValues.drop(1).firstOrNull { it.isNotBlank() }
    }

    private fun Request.Builder.browserHeaders(referer: String? = null): Request.Builder {
        header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
        )
        header("Accept", "*/*")
        header("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
        referer?.let { header("Referer", it) }
        return this
    }

    private sealed class WaitResult {
        data class Active(val file: UploadedFile) : WaitResult()
        data class Generated(val description: String) : WaitResult()
        data class Error(val message: String) : WaitResult()
    }

    companion object {
        private const val MAX_VIDEO_BYTES = 550L * 1024L * 1024L
        private const val MAX_PROCESSING_ATTEMPTS = 240
        private const val SPECULATIVE_ATTEMPT = 120
        private val VIDEO_MODEL_FALLBACK_ORDER = listOf(
            "gemini-3.1-pro-preview",
            "gemini-2.5-pro",
            "gemini-3-flash-preview",
            "gemini-2.5-flash",
            GeminiModelConfig.DEFAULT_MODEL
        )
        private val TOKEN_REGEX = Regex("""name=["']_token["']\s+value=["']([^"']+)["']""")
        private val MP4_REGEX = Regex("""href=["']([^"']+\.mp4[^"']*)["']|["'](https?://[^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE)
        private val ESCAPED_MP4_REGEX = Regex("""(https?:\\?/\\?/[^"'\\]+\.mp4[^"']*)""", RegexOption.IGNORE_CASE)
    }
}
