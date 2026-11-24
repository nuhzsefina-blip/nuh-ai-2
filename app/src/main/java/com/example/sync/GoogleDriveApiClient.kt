package com.example.sync

import com.example.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val sizeBytes: Long,
)

class GoogleDriveApiClient(
    private val client: OkHttpClient = NetworkUtils.createMobileResilientOkHttpClient(
        connectTimeoutSec = 35L,
        readTimeoutSec = 60L,
        writeTimeoutSec = 90L,
        callTimeoutSec = 150L,
    )
) {
    suspend fun ensureBackupFolderId(accessToken: String): String = withContext(Dispatchers.IO) {
        val query = "name = '$BACKUP_FOLDER_NAME' and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val listUrl = "$DRIVE_FILES_URL?q=$encodedQuery&spaces=drive&fields=files(id,name)"

        val listRequest = Request.Builder()
            .url(listUrl)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(listRequest).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw DriveApiException(response.code, parseErrorMessage(bodyStr, response.code))
            }
            val files = JSONObject(bodyStr).optJSONArray("files")
            if (files != null && files.length() > 0) {
                val existingId = files.getJSONObject(0).optString("id")
                if (existingId.isNotBlank()) return@withContext existingId
            }
        }

        // Create RedatDagna_Backups folder in Drive
        val folderMetadata = JSONObject()
            .put("name", BACKUP_FOLDER_NAME)
            .put("mimeType", FOLDER_MIME_TYPE)
            .toString()

        val createRequest = Request.Builder()
            .url("$DRIVE_FILES_URL?fields=id,name")
            .header("Authorization", "Bearer $accessToken")
            .post(folderMetadata.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(createRequest).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw DriveApiException(response.code, parseErrorMessage(bodyStr, response.code))
            }
            val createdId = JSONObject(bodyStr).optString("id")
            if (createdId.isBlank()) throw IOException("Google Drive አቃፊ መፍጠር አልተቻለም።")
            createdId
        }
    }

    suspend fun uploadEncryptedBackup(
        accessToken: String,
        fileName: String,
        encryptedBytes: ByteArray,
    ): DriveBackupFile = withContext(Dispatchers.IO) {
        val folderId = ensureBackupFolderId(accessToken)
        val metadataJson = JSONObject()
            .put("name", fileName)
            .put("parents", JSONArray().put(folderId))
            .put("mimeType", OCTET_STREAM_MIME)
            .toString()

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
            .addPart(encryptedBytes.toRequestBody(OCTET_STREAM_MIME.toMediaType()))
            .build()

        val uploadRequest = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id,name,modifiedTime,size")
            .header("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        client.newCall(uploadRequest).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw DriveApiException(response.code, parseErrorMessage(bodyStr, response.code))
            }
            parseBackupFile(JSONObject(bodyStr))
        }
    }

    suspend fun listEncryptedBackups(
        accessToken: String,
        limit: Int = 15,
    ): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        val folderId = ensureBackupFolderId(accessToken)
        val query = "'$folderId' in parents and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$DRIVE_FILES_URL?q=$encodedQuery&orderBy=modifiedTime%20desc&pageSize=$limit&fields=files(id,name,modifiedTime,size)"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw DriveApiException(response.code, parseErrorMessage(bodyStr, response.code))
            }
            parseBackupListJson(bodyStr)
        }
    }

    suspend fun downloadBackupBytes(accessToken: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                throw DriveApiException(response.code, parseErrorMessage(errBody, response.code))
            }
            response.body?.bytes() ?: throw IOException("ባዶ የGoogle Drive ፋይል ተመልሷል።")
        }
    }

    companion object {
        const val BACKUP_FOLDER_NAME = "RedatDagna_Backups"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val OCTET_STREAM_MIME = "application/octet-stream"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"

        fun parseBackupFile(obj: JSONObject): DriveBackupFile {
            return DriveBackupFile(
                id = obj.optString("id"),
                name = obj.optString("name"),
                modifiedTime = obj.optString("modifiedTime"),
                sizeBytes = obj.optString("size").toLongOrNull() ?: obj.optLong("size", 0L),
            )
        }

        fun parseBackupListJson(json: String): List<DriveBackupFile> {
            val root = JSONObject(json)
            val files = root.optJSONArray("files") ?: return emptyList()
            return buildList {
                for (i in 0 until files.length()) {
                    val obj = files.optJSONObject(i) ?: continue
                    val parsed = parseBackupFile(obj)
                    if (parsed.id.isNotBlank()) add(parsed)
                }
            }
        }

        private fun parseErrorMessage(body: String, code: Int): String {
            val apiMsg = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("message")
            }.getOrNull()
            return if (!apiMsg.isNullOrBlank()) {
                "Google Drive ($code): $apiMsg"
            } else {
                "Google Drive ስህተት ($code)"
            }
        }
    }
}

class DriveApiException(
    val statusCode: Int,
    override val message: String,
) : IOException(message)
