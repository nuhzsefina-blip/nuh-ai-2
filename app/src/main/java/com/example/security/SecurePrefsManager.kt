package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.util.CryptoUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.SecureRandom

data class AppSecuritySettings(
    val hasCustomApiKey: Boolean = false,
    val ocrModelName: String = DEFAULT_OCR_MODEL,
    val draftingModelName: String = DEFAULT_DRAFT_MODEL,
    val flagSecureEnabled: Boolean = false,
    val piiMaskingEnabled: Boolean = true,
    val archiveLockEnabled: Boolean = true,
    val biometricEnabled: Boolean = true,
    val hasPinSet: Boolean = false,
    val autoLockMinutes: Int = 5,
    val driveTreeUri: String? = null,
    val lastSyncTimestamp: Long = 0L,
    val darkThemeOverride: Boolean? = null,
    val googleAccountName: String? = null,
    val googleAccountEmail: String? = null,
    val googleAccountPhotoUrl: String? = null,
    val driveApiAuthorized: Boolean = false,
    val pinnedStyleKeys: Set<String> = emptySet(),
    val subjectTags: Map<String, String> = emptyMap(),
    val adjournmentDates: Map<String, String> = emptyMap(),
    val notificationsEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_OCR_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_DRAFT_MODEL = "gemini-3.8-flash"
    }
}

@Suppress("DEPRECATION")
class SecurePrefsManager(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefsWithRecovery()
    }

    private fun createEncryptedPrefsWithRecovery(): SharedPreferences {
        return try {
            buildEncryptedPrefs()
        } catch (_: Exception) {
            // Recover cleanly if Keystore key was invalidated across emulator/backup resets
            appContext.deleteSharedPreferences(VAULT_FILE_NAME)
            runCatching { buildEncryptedPrefs() }.getOrElse {
                appContext.getSharedPreferences("${VAULT_FILE_NAME}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            VAULT_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSecuritySettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSecuritySettings {
        val darkOverride = when (prefs.getInt(KEY_DARK_THEME, -1)) {
            1 -> true
            0 -> false
            else -> null
        }
        return AppSecuritySettings(
            hasCustomApiKey = !prefs.getString(KEY_API_KEY, null).isNullOrBlank(),
            ocrModelName = prefs.getString(KEY_OCR_MODEL, AppSecuritySettings.DEFAULT_OCR_MODEL)
                ?: AppSecuritySettings.DEFAULT_OCR_MODEL,
            draftingModelName = prefs.getString(KEY_DRAFT_MODEL, AppSecuritySettings.DEFAULT_DRAFT_MODEL)
                ?: AppSecuritySettings.DEFAULT_DRAFT_MODEL,
            flagSecureEnabled = prefs.getBoolean(KEY_FLAG_SECURE, false),
            piiMaskingEnabled = prefs.getBoolean(KEY_PII_MASKING, true),
            archiveLockEnabled = prefs.getBoolean(KEY_ARCHIVE_LOCK, true),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true),
            hasPinSet = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank(),
            autoLockMinutes = prefs.getInt(KEY_AUTO_LOCK_MIN, 5).coerceIn(1, 10),
            driveTreeUri = prefs.getString(KEY_DRIVE_TREE_URI, null),
            lastSyncTimestamp = prefs.getLong(KEY_LAST_SYNC_TS, 0L),
            darkThemeOverride = darkOverride,
            googleAccountName = prefs.getString(KEY_GOOGLE_NAME, null),
            googleAccountEmail = prefs.getString(KEY_GOOGLE_EMAIL, null),
            googleAccountPhotoUrl = prefs.getString(KEY_GOOGLE_PHOTO, null),
            driveApiAuthorized = prefs.getBoolean(KEY_DRIVE_API_AUTH, false),
            pinnedStyleKeys = prefs.getStringSet(KEY_PINNED_STYLES, emptySet())?.toSet() ?: emptySet(),
            subjectTags = decodeStringMap(prefs.getString(KEY_SUBJECT_TAGS, null)),
            adjournmentDates = decodeStringMap(prefs.getString(KEY_ADJOURNMENT_DATES, null)),
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
        )
    }

    fun getCustomApiKey(): String? = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setCustomApiKey(apiKey: String?) {
        val cleaned = apiKey?.trim().orEmpty()
        prefs.edit {
            if (cleaned.isEmpty()) remove(KEY_API_KEY) else putString(KEY_API_KEY, cleaned)
        }
        _settings.update { it.copy(hasCustomApiKey = cleaned.isNotEmpty()) }
    }

    fun setModels(ocrModel: String, draftingModel: String) {
        val ocr = ocrModel.trim().ifEmpty { AppSecuritySettings.DEFAULT_OCR_MODEL }
        val draft = draftingModel.trim().ifEmpty { AppSecuritySettings.DEFAULT_DRAFT_MODEL }
        prefs.edit {
            putString(KEY_OCR_MODEL, ocr)
            putString(KEY_DRAFT_MODEL, draft)
        }
        _settings.update { it.copy(ocrModelName = ocr, draftingModelName = draft) }
    }

    fun setFlagSecure(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FLAG_SECURE, enabled) }
        _settings.update { it.copy(flagSecureEnabled = enabled) }
    }

    fun setPiiMasking(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PII_MASKING, enabled) }
        _settings.update { it.copy(piiMaskingEnabled = enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled) }
        _settings.update { it.copy(notificationsEnabled = enabled) }
    }

    fun setArchiveLock(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ARCHIVE_LOCK, enabled) }
        _settings.update { it.copy(archiveLockEnabled = enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
        _settings.update { it.copy(biometricEnabled = enabled) }
    }

    fun setAutoLockMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 10)
        prefs.edit { putInt(KEY_AUTO_LOCK_MIN, clamped) }
        _settings.update { it.copy(autoLockMinutes = clamped) }
    }

    fun setDarkThemeOverride(dark: Boolean?) {
        val code = when (dark) {
            true -> 1
            false -> 0
            null -> -1
        }
        prefs.edit { putInt(KEY_DARK_THEME, code) }
        _settings.update { it.copy(darkThemeOverride = dark) }
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hash = CryptoUtil.sha256Hex("$saltB64:$pin")
        prefs.edit {
            putString(KEY_PIN_SALT, saltB64)
            putString(KEY_PIN_HASH, hash)
        }
        _settings.update { it.copy(hasPinSet = true) }
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val computed = CryptoUtil.sha256Hex("$salt:$pin")
        return computed == storedHash
    }

    fun setDriveTreeUri(uriString: String?) {
        val normalized = uriString?.takeIf { it.isNotBlank() }
        prefs.edit {
            if (normalized == null) remove(KEY_DRIVE_TREE_URI)
            else putString(KEY_DRIVE_TREE_URI, normalized)
        }
        _settings.update { it.copy(driveTreeUri = normalized) }
    }

    fun updateLastSyncTimestamp(timestamp: Long) {
        prefs.edit { putLong(KEY_LAST_SYNC_TS, timestamp) }
        _settings.update { it.copy(lastSyncTimestamp = timestamp) }
    }

    fun setGoogleAccount(name: String?, email: String?, photoUrl: String?) {
        val cur = _settings.value
        val nName = name?.takeIf { it.isNotBlank() }
        val nEmail = email?.takeIf { it.isNotBlank() }
        val nPhoto = photoUrl?.takeIf { it.isNotBlank() }
        if (cur.googleAccountName == nName && cur.googleAccountEmail == nEmail && cur.googleAccountPhotoUrl == nPhoto) {
            return
        }
        prefs.edit {
            if (nName == null) remove(KEY_GOOGLE_NAME) else putString(KEY_GOOGLE_NAME, nName)
            if (nEmail == null) remove(KEY_GOOGLE_EMAIL) else putString(KEY_GOOGLE_EMAIL, nEmail)
            if (nPhoto == null) remove(KEY_GOOGLE_PHOTO) else putString(KEY_GOOGLE_PHOTO, nPhoto)
        }
        _settings.update {
            it.copy(
                googleAccountName = nName,
                googleAccountEmail = nEmail,
                googleAccountPhotoUrl = nPhoto,
            )
        }
    }

    fun setDriveApiAuthorized(authorized: Boolean) {
        if (_settings.value.driveApiAuthorized == authorized) return
        prefs.edit { putBoolean(KEY_DRIVE_API_AUTH, authorized) }
        _settings.update { it.copy(driveApiAuthorized = authorized) }
    }

    fun clearGoogleAccountSession() {
        prefs.edit {
            remove(KEY_GOOGLE_NAME)
            remove(KEY_GOOGLE_EMAIL)
            remove(KEY_GOOGLE_PHOTO)
            putBoolean(KEY_DRIVE_API_AUTH, false)
        }
        _settings.update {
            it.copy(
                googleAccountName = null,
                googleAccountEmail = null,
                googleAccountPhotoUrl = null,
                driveApiAuthorized = false,
            )
        }
    }

    fun togglePinnedStyleKey(keys: List<String>): Boolean {
        val current = _settings.value.pinnedStyleKeys.toMutableSet()
        val wasPinned = keys.any { it in current }
        if (wasPinned) {
            current.removeAll(keys.toSet())
        } else {
            keys.firstOrNull()?.let { current.add(it) }
        }
        prefs.edit { putStringSet(KEY_PINNED_STYLES, current) }
        _settings.update { it.copy(pinnedStyleKeys = current.toSet()) }
        return !wasPinned
    }

    fun setSubjectTag(keys: List<String>, tag: String?) {
        val map = _settings.value.subjectTags.toMutableMap()
        keys.forEach { map.remove(it) }
        val primary = keys.firstOrNull()
        if (!tag.isNullOrBlank() && primary != null) {
            map[primary] = tag.trim()
        }
        prefs.edit { putString(KEY_SUBJECT_TAGS, encodeStringMap(map)) }
        _settings.update { it.copy(subjectTags = map.toMap()) }
    }

    fun setAdjournmentDate(keys: List<String>, ethiopianDate: String?) {
        val map = _settings.value.adjournmentDates.toMutableMap()
        keys.forEach { map.remove(it) }
        val primary = keys.firstOrNull()
        if (!ethiopianDate.isNullOrBlank() && primary != null) {
            map[primary] = ethiopianDate.trim()
        }
        prefs.edit { putString(KEY_ADJOURNMENT_DATES, encodeStringMap(map)) }
        _settings.update { it.copy(adjournmentDates = map.toMap()) }
    }

    fun clearCaseExtras(keys: List<String>) {
        if (keys.isEmpty()) return
        val keySet = keys.toSet()
        val cur = _settings.value
        val pinned = cur.pinnedStyleKeys.toMutableSet().apply { removeAll(keySet) }
        val tags = cur.subjectTags.toMutableMap()
        val dates = cur.adjournmentDates.toMutableMap()
        keys.forEach {
            tags.remove(it)
            dates.remove(it)
        }
        prefs.edit {
            putStringSet(KEY_PINNED_STYLES, pinned)
            putString(KEY_SUBJECT_TAGS, encodeStringMap(tags))
            putString(KEY_ADJOURNMENT_DATES, encodeStringMap(dates))
        }
        _settings.update {
            it.copy(
                pinnedStyleKeys = pinned.toSet(),
                subjectTags = tags.toMap(),
                adjournmentDates = dates.toMap(),
            )
        }
    }

    private fun decodeStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = org.json.JSONObject(raw)
            buildMap {
                val iter = json.keys()
                while (iter.hasNext()) {
                    val k = iter.next()
                    val v = json.optString(k)
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeStringMap(map: Map<String, String>): String {
        val json = org.json.JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    fun getOrCreateSqlCipherKey(): ByteArray {
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (!existing.isNullOrBlank()) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KEY_DB_PASSPHRASE, Base64.encodeToString(key, Base64.NO_WRAP))
        }
        return key
    }

    fun getOrCreatePdfOwnerPassword(): String {
        val existing = prefs.getString(KEY_PDF_OWNER_PW, null)
        if (!existing.isNullOrBlank()) return existing
        val randomBytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val pw = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
        prefs.edit { putString(KEY_PDF_OWNER_PW, pw) }
        return pw
    }

    fun getOrCreateBackupPassphrase(): ByteArray {
        val existing = prefs.getString(KEY_BACKUP_KEY, null)
        if (!existing.isNullOrBlank()) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KEY_BACKUP_KEY, Base64.encodeToString(key, Base64.NO_WRAP))
        }
        return key
    }

    companion object {
        @Volatile
        private var INSTANCE: SecurePrefsManager? = null

        fun getInstance(context: Context): SecurePrefsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePrefsManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val VAULT_FILE_NAME = "redat_dagna_secure_vault"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_OCR_MODEL = "ocr_model_name"
        private const val KEY_DRAFT_MODEL = "draft_model_name"
        private const val KEY_FLAG_SECURE = "flag_secure_enabled"
        private const val KEY_PII_MASKING = "pii_masking_enabled"
        private const val KEY_ARCHIVE_LOCK = "archive_lock_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_AUTO_LOCK_MIN = "auto_lock_minutes"
        private const val KEY_DRIVE_TREE_URI = "drive_tree_uri"
        private const val KEY_LAST_SYNC_TS = "last_sync_ts"
        private const val KEY_DB_PASSPHRASE = "sqlcipher_key_b64"
        private const val KEY_PDF_OWNER_PW = "pdf_owner_pw"
        private const val KEY_BACKUP_KEY = "backup_aes_key_b64"
        private const val KEY_DARK_THEME = "dark_theme_override"
        private const val KEY_GOOGLE_NAME = "google_account_name"
        private const val KEY_GOOGLE_EMAIL = "google_account_email"
        private const val KEY_GOOGLE_PHOTO = "google_account_photo"
        private const val KEY_DRIVE_API_AUTH = "google_drive_api_authorized"
        private const val KEY_PINNED_STYLES = "pinned_style_keys"
        private const val KEY_SUBJECT_TAGS = "case_subject_tags_json"
        private const val KEY_ADJOURNMENT_DATES = "case_adjournment_dates_json"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    }
}
