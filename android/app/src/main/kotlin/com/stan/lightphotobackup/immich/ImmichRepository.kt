package com.stan.lightphotobackup.immich

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.stan.lightphotobackup.backup.BackupCredential
import com.stan.lightphotobackup.backup.BackupUploader
import com.stan.lightphotobackup.database.BackupRecord
import com.stan.lightphotobackup.google.UploadException
import com.stan.lightphotobackup.pairing.AuthorizationExpiredException
import com.stan.lightphotobackup.pairing.SecureCredentialStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.time.Instant

data class ImmichCredentials(val baseUrl: String, val apiKey: String)

class ImmichRepository(private val api: ImmichApi, private val store: SecureCredentialStore) {
    fun connected() = store.getImmich() != null

    suspend fun configure(baseUrl: String, apiKey: String) {
        val credentials = ImmichCredentials(normalizeUrl(baseUrl), apiKey.trim())
        require(credentials.apiKey.isNotEmpty()) { "API key is required" }
        withContext(Dispatchers.IO) { api.validate(credentials) }
        store.saveImmich(credentials)
    }

    fun apiKey() = store.getImmich()?.apiKey ?: throw AuthorizationExpiredException()
    fun backupCredential() = store.getImmich()?.let { BackupCredential.Immich(it.baseUrl, it.apiKey, store.immichDeviceId()) } ?: throw AuthorizationExpiredException()
    fun disconnect() = store.clearImmich()

    companion object {
        fun normalizeUrl(value: String): String {
            val input = value.trim().trimEnd('/')
            val url = if (input.contains("://")) input else "https://$input"
            require(url.startsWith("https://")) { "Use an https:// server URL" }
            return url
        }
    }
}

@Serializable private data class ImmichAssetResponse(val id: String, val status: String)

class ImmichApi(private val resolver: ContentResolver, private val client: OkHttpClient) : BackupUploader {
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(credentials: ImmichCredentials) {
        execute(credentials, Request.Builder().url(url(credentials.baseUrl, "/api/users/me")).header("x-api-key", credentials.apiKey).get().build()) { Unit }
    }

    override fun uploadBytes(credential: BackupCredential, item: BackupRecord): String {
        val credentials = credential as? BackupCredential.Immich
            ?: error("Immich uploader requires an Immich credential")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("assetData", item.displayName ?: "LightPhone-${item.id}", streamBody(Uri.parse(item.contentUri), item.mimeType, item.sizeBytes))
            .addFormDataPart("deviceId", credentials.deviceId)
            .addFormDataPart("deviceAssetId", "${item.mediaStoreVolume}-${item.mediaStoreId}")
            .addFormDataPart("fileCreatedAt", timestamp(item.dateTakenMillis ?: item.dateAddedSeconds?.times(1000)))
            .addFormDataPart("fileModifiedAt", timestamp(item.dateModifiedSeconds?.times(1000) ?: item.dateTakenMillis ?: item.dateAddedSeconds?.times(1000)))
            .build()
        return execute(ImmichCredentials(credentials.baseUrl, credentials.apiKey), Request.Builder().url(url(credentials.baseUrl, "/api/assets")).header("x-api-key", credentials.apiKey).post(body).build()) {
            json.decodeFromString<ImmichAssetResponse>(it).id
        }
    }

    override fun create(credential: BackupCredential, uploadToken: String, name: String) = uploadToken

    private fun streamBody(uri: Uri, mime: String, size: Long) = object : RequestBody() {
        override fun contentType() = mime.toMediaType()
        override fun contentLength() = if (size > 0) size else -1L
        override fun writeTo(sink: BufferedSink) {
            resolver.openInputStream(uri)?.use { sink.writeAll(it.source()) } ?: throw java.io.FileNotFoundException("Local photo unavailable")
        }
    }

    private fun timestamp(value: Long?) = Instant.ofEpochMilli(value ?: System.currentTimeMillis()).toString()
    private fun url(base: String, path: String) = base.trimEnd('/') + path
    private fun <T> execute(credentials: ImmichCredentials, request: Request, parse: (String) -> T): T = client.newCall(request).execute().use {
        Log.i("PhotoBackupImmich", "server response HTTP ${it.code}")
        if (it.code == 401 || it.code == 403) throw AuthorizationExpiredException()
        if (!it.isSuccessful) throw UploadException("immich_http_${it.code}", "Immich returned HTTP ${it.code}", it.code == 408 || it.code == 429 || it.code >= 500, it.header("Retry-After")?.toLongOrNull()?.times(1000))
        parse(it.body?.string().orEmpty())
    }
}
