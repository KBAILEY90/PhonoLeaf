package com.phonoleaf.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed storage for the native OAuth refresh token (`pl_rtoken`).
 *
 * WHAT THIS PROTECTS, and what it does not. WebView localStorage is sandboxed
 * to this app but is NOT encrypted at rest, so a device-level compromise (root,
 * physical extraction, a full backup) can read it as plain text. The value here
 * is encrypted with a key held in the Android Keystore, which never leaves
 * secure hardware or OS-protected storage, so the stored bytes are useless
 * without this device.
 *
 * REWRITTEN 2026-09-02, off androidx.security-crypto onto the platform APIs.
 * Google deprecated every API in that library as of 1.1.0-beta01 (June 2025)
 * and recommends exactly this: the Keystore directly. The old library is kept
 * as a DEPENDENCY only so the one-time migration below can still read what it
 * wrote; nothing new is stored through it. Once no installed device can still
 * be carrying a legacy value, `legacyValue()` and the dependency can both go.
 *
 * Design notes, none of them arbitrary:
 *
 *  - AES-256/GCM. GCM authenticates as well as encrypts, so a tampered value
 *    fails to decrypt rather than returning altered bytes.
 *  - A fresh random IV per write, stored in front of the ciphertext. GCM is
 *    catastrophically weak if an IV is ever reused with the same key, so the
 *    IV is taken from the Cipher itself rather than being chosen here.
 *  - `setUserAuthenticationRequired(false)`. The token is read while the app
 *    resumes in the background to keep reading aloud with the screen locked;
 *    requiring an unlock would break background playback, which is a core
 *    feature. The protection sought here is against an extracted disk image,
 *    not against someone holding the unlocked phone.
 *  - Every failure path returns null rather than throwing. A decryption
 *    failure means the token is unusable, and the app already treats a missing
 *    token as "sign in again" — which is a recoverable prompt, where a crash
 *    on launch is not. This matters after a device restore or a Keystore reset,
 *    where the key is genuinely gone and no amount of retrying helps.
 */
@CapacitorPlugin(name = "SecureStorage")
class SecureStoragePlugin : Plugin() {

    companion object {
        private const val TAG = "PhonoLeafSecure"
        private const val PREFS = "phonoleaf_secure_v2"
        /** Written by the pre-2026-09-02 EncryptedSharedPreferences build. */
        private const val LEGACY_PREFS = "phonoleaf_secure"
        private const val KEY_ALIAS = "phonoleaf_secure_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val KEYSTORE = "AndroidKeyStore"
    }

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The Keystore key, created on first use and reused thereafter. */
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // See the class comment: background playback must be able to
                // read the token while the phone is locked.
                .setUserAuthenticationRequired(false)
                .build()
        )
        return gen.generateKey()
    }

    /** base64(iv || ciphertext+tag). */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv                       // chosen by the provider, never by us
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = try {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        // GCM IVs from the AndroidKeyStore provider are 12 bytes.
        val iv = raw.copyOfRange(0, 12)
        val body = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    } catch (t: Throwable) {
        // Key gone (device restore, Keystore reset) or the value was tampered
        // with. Either way it is unusable; the app re-authenticates.
        Log.w(TAG, "could not decrypt stored value: ${t.javaClass.simpleName}")
        null
    }

    /**
     * One-time read of whatever the old EncryptedSharedPreferences build wrote.
     * Returns null once nothing is left, which is the normal case forever after
     * the first launch on an upgraded install.
     *
     * Deliberately quiet on failure: a device whose legacy keyset is
     * unreadable should sign in again, not crash on launch.
     */
    private fun legacyValue(k: String): String? = try {
        val master = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
        val legacy = androidx.security.crypto.EncryptedSharedPreferences.create(
            context, LEGACY_PREFS, master,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val v = legacy.getString(k, null)
        if (v != null) legacy.edit().remove(k).apply()   // migrate, don't duplicate
        v
    } catch (t: Throwable) {
        Log.w(TAG, "legacy store unreadable: ${t.javaClass.simpleName}")
        null
    }

    @PluginMethod
    fun set(call: PluginCall) {
        val k = call.getString("key")
        val v = call.getString("value")
        if (k == null || v == null) { call.reject("key and value are required"); return }
        try {
            prefs().edit().putString(k, encrypt(v)).apply()
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "SecureStorage.set failed", e)
        }
    }

    /** Resolves { value: string|null } — null (not a rejection) when unset. */
    @PluginMethod
    fun get(call: PluginCall) {
        val k = call.getString("key")
        if (k == null) { call.reject("key is required"); return }
        try {
            var out = prefs().getString(k, null)?.let { decrypt(it) }
            if (out == null) {
                // Nothing here yet: an install upgrading from the old library
                // still has its token in the legacy store. Move it across once.
                legacyValue(k)?.let {
                    Log.i(TAG, "migrated '$k' from the legacy encrypted store")
                    prefs().edit().putString(k, encrypt(it)).apply()
                    out = it
                }
            }
            val ret = JSObject()
            ret.put("value", out ?: JSONObject.NULL)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject(e.message ?: "SecureStorage.get failed", e)
        }
    }

    @PluginMethod
    fun remove(call: PluginCall) {
        val k = call.getString("key")
        if (k == null) { call.reject("key is required"); return }
        try {
            prefs().edit().remove(k).apply()
            legacyValue(k)   // clear any legacy copy too, so a sign-out is complete
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "SecureStorage.remove failed", e)
        }
    }
}
