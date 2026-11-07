package com.glykos.core.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glykos.core.data.librelinkup.LibreLinkUpCredentials
import com.glykos.core.domain.announce.AnnouncementSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

enum class OutputMode { VOICE, WATCH, BOTH }

/**
 * App settings in DataStore. LibreLinkUp credentials are stored encrypted with AES-256-GCM using a
 * non-exportable Android Keystore key (EncryptedSharedPreferences is deprecated). They are never
 * logged; if the key is lost (e.g. backup restored on another device) they read as absent.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val outputMode: Flow<OutputMode> = dataStore.data.map { prefs ->
        prefs[OUTPUT_MODE]?.let { runCatching { OutputMode.valueOf(it) }.getOrNull() } ?: OutputMode.BOTH
    }.distinctUntilChanged()

    /** Always sanitized: a corrupted value can never produce an invalid config. */
    val announcementSettings: Flow<AnnouncementSettings> = dataStore.data.map { prefs ->
        val d = AnnouncementSettings()
        AnnouncementSettings(
            intervalMinutes = prefs[INTERVAL_MIN] ?: d.intervalMinutes,
            minChangeMgDl = prefs[MIN_CHANGE] ?: d.minChangeMgDl,
            veryLow = prefs[VERY_LOW] ?: d.veryLow,
            low = prefs[LOW] ?: d.low,
            high = prefs[HIGH] ?: d.high,
        ).sanitized()
    }.distinctUntilChanged()

    suspend fun setAnnouncementSettings(settings: AnnouncementSettings) {
        val s = settings.sanitized()
        dataStore.edit {
            it[INTERVAL_MIN] = s.intervalMinutes
            s.minChangeMgDl?.let { v -> it[MIN_CHANGE] = v } ?: it.remove(MIN_CHANGE)
            it[VERY_LOW] = s.veryLow
            it[LOW] = s.low
            it[HIGH] = s.high
        }
    }

    /** Email only, for display. Never expose the password to the UI. */
    val credentialsEmail: Flow<String?> = dataStore.data.map { prefs -> prefs[CREDENTIALS]?.let(::decrypt)?.email }

    suspend fun setOutputMode(mode: OutputMode) {
        dataStore.edit { it[OUTPUT_MODE] = mode.name }
    }

    suspend fun saveCredentials(email: String, password: String) {
        val plain = json.encodeToString(StoredCredentials.serializer(), StoredCredentials(email.trim(), password))
        dataStore.edit { it[CREDENTIALS] = encrypt(plain) }
    }

    suspend fun clearCredentials() {
        dataStore.edit { it.remove(CREDENTIALS) }
    }

    suspend fun credentials(): LibreLinkUpCredentials? =
        dataStore.data.first()[CREDENTIALS]?.let(::decrypt)?.let { LibreLinkUpCredentials(it.email, it.password) }

    @Serializable
    private class StoredCredentials(val email: String, val password: String)

    private fun decrypt(blob: String): StoredCredentials? = runCatching {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES))
        val plain = cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES).decodeToString()
        json.decodeFromString(StoredCredentials.serializer(), plain)
    }.getOrNull()

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        check(iv.size == GCM_IV_BYTES)
        return Base64.encodeToString(iv + cipher.doFinal(plain.encodeToByteArray()), Base64.NO_WRAP)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        val OUTPUT_MODE = stringPreferencesKey("output_mode")
        val CREDENTIALS = stringPreferencesKey("llu_credentials")
        val INTERVAL_MIN = intPreferencesKey("announce_interval_min")
        val MIN_CHANGE = intPreferencesKey("announce_min_change")
        val VERY_LOW = intPreferencesKey("threshold_very_low")
        val LOW = intPreferencesKey("threshold_low")
        val HIGH = intPreferencesKey("threshold_high")
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "glykos_llu_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
