package com.stan.lightphotobackup.backup

import com.stan.lightphotobackup.database.BackupRecord

/** A destination registered by this build of the app. */
enum class BackupProvider(val displayName: String) {
    GOOGLE_PHOTOS("Google Photos"),
    IMMICH("Immich"),
    ;

    companion object {
        val available = entries.toList()

        fun fromStoredValue(value: String?) = entries.firstOrNull { it.name == value } ?: GOOGLE_PHOTOS
    }
}

sealed interface BackupCredential {
    data class Google(val accessToken: String) : BackupCredential
    data class Immich(val baseUrl: String, val apiKey: String, val deviceId: String) : BackupCredential
}

interface BackupUploader {
    fun uploadBytes(credential: BackupCredential, item: BackupRecord): String
    fun create(credential: BackupCredential, uploadToken: String, name: String): String?
}
