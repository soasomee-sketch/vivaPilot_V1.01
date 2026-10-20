package com.iptvplayer.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Zaszyfrowany rejestr zgód (VivaPilot 1.12, punkt 7 promptu): lista [ConsentEvent] zapisana
 * jako JSON, zaszyfrowana AES-256-GCM kluczem z Android Keystore (klucz jest w sprzętowym
 * lub systemowym magazynie kluczy i nie da się go odczytać z appki ani z pliku), w pliku w
 * PRYWATNEJ pamięci appki (filesDir — nie cache, nie pamięć publiczna).
 *
 * - Zwykłe "wyczyść pamięć podręczną" (cache) nie dotyka filesDir, więc rejestr to przeżywa.
 * - GCM zapewnia też integralność: każda zmiana pliku z zewnątrz = błąd odszyfrowania, czyli
 *   rejestr nie da się cichaczem podmienić (w razie błędu traktowany jest jak pusty).
 * - Tylko dopisywanie na końcu ([append]); nie ma funkcji edycji ani usuwania zdarzeń (jedyny
 *   wyjątek to [clearForTesting] — wołane WYŁĄCZNIE z panelu testowego buildu debug).
 * - Plik jest wyłączony z automatycznej kopii Androida (res/xml/backup_rules.xml i
 *   data_extraction_rules.xml): klucz Keystore nie przechodzi na inne urządzenie, a rejestr
 *   nie może być nigdzie wysyłany.
 *
 * Format pliku: [wersja formatu: 1 bajt][długość IV: 1 bajt][IV][szyfrogram + tag GCM].
 * Stan jest dodatkowo trzymany w pamięci procesu (cache), żeby nie odszyfrowywać pliku przy
 * każdym zapytaniu "czy Użytkownik zaakceptował".
 */
class ConsentVault(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    /** Wszystkie zdarzenia w kolejności zapisu. Pusta lista, jeśli rejestru nie ma albo nie da się go odszyfrować. */
    fun readAll(): List<ConsentEvent> = synchronized(LOCK) {
        cache?.let { return it }
        val loaded = loadFromDisk()
        cache = loaded
        loaded
    }

    /**
     * Dopisuje zdarzenie na końcu i zapisuje cały rejestr (odszyfruj → dopisz → zaszyfruj →
     * zapis atomowy). Zwraca false, jeśli zapis się nie udał (np. uszkodzony Keystore) —
     * wtedy zdarzenie zostaje przynajmniej w pamięci procesu (appka działa do zamknięcia).
     */
    fun append(event: ConsentEvent): Boolean = synchronized(LOCK) {
        val updated = readAll() + event
        cache = updated
        try {
            writeToDisk(updated)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Panel testowy (debug): kasuje cały rejestr. Produkt celowo nie ma takiej funkcji dla Użytkownika. */
    fun clearForTesting(): Boolean = synchronized(LOCK) {
        cache = emptyList()
        runCatching { file.delete() }.getOrDefault(false) || !file.exists()
    }

    private fun loadFromDisk(): List<ConsentEvent> {
        if (!file.exists()) return emptyList()
        return try {
            val raw = AtomicFile(file).readFully()
            if (raw.size < 3 || raw[0] != FORMAT_VERSION) return emptyList()
            val ivLen = raw[1].toInt() and 0xFF
            if (raw.size < 2 + ivLen + 16) return emptyList()
            val iv = raw.copyOfRange(2, 2 + ivLen)
            val ct = raw.copyOfRange(2 + ivLen, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            val plain = cipher.doFinal(ct)
            ConsentEvent.listFromJson(JSONArray(String(plain, Charsets.UTF_8)))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeToDisk(events: List<ConsentEvent>) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val iv = cipher.iv
        val ct = cipher.doFinal(ConsentEvent.listToJson(events).toString().toByteArray(Charsets.UTF_8))
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(byteArrayOf(FORMAT_VERSION, iv.size.toByte()))
            out.write(iv)
            out.write(ct)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        /** Nazwa pliku musi zgadzać się z res/xml/backup_rules.xml i data_extraction_rules.xml. */
        const val FILE_NAME = "consent_vault.bin"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "vivapilot_consent_vault_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private val AAD = "VivaPilot-consent-vault-v1".toByteArray(Charsets.UTF_8)
        private val LOCK = Any()
        @Volatile private var cache: List<ConsentEvent>? = null
    }
}
