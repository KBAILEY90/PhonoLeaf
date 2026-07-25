package com.phonoleaf.app

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject

/**
 * Android Keystore-backed encrypted storage — used to move the native OAuth
 * refresh token (pl_rtoken) out of plain WebView localStorage. localStorage
 * is sandboxed to the app (not exposed to other apps) but not encrypted at
 * rest; a device-level compromise (root, physical extraction) could read it
 * as plain text. EncryptedSharedPreferences backs the encryption key with the
 * Android Keystore, so the key itself never leaves secure hardware/OS storage.
 *
 * Deliberately tiny: get/set/remove(key) only, nothing else needs this yet.
 * The master key is created lazily on first use (AES256_GCM, the current
 * androidx.security recommended scheme — MasterKeys/KeyGenParameterSpec is
 * the older, now-deprecated API this superseded).
 */
@CapacitorPlugin(name = "SecureStorage")
class SecureStoragePlugin : Plugin() {
    private val PREFS_NAME = "phonoleaf_secure"

    private fun prefs() = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @PluginMethod
    fun set(call: PluginCall) {
        val key = call.getString("key")
        val value = call.getString("value")
        if (key == null || value == null) { call.reject("key and value are required"); return }
        try {
            prefs().edit().putString(key, value).apply()
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "SecureStorage.set failed", e)
        }
    }

    /** Resolves { value: string|null } — null (not a rejection) when the key isn't set.
     *  Explicit JSONObject.NULL rather than a bare Kotlin null: JSObject.put's
     *  null-handling isn't something to assume without a device to verify on. */
    @PluginMethod
    fun get(call: PluginCall) {
        val key = call.getString("key")
        if (key == null) { call.reject("key is required"); return }
        try {
            val v = prefs().getString(key, null)
            val ret = JSObject()
            ret.put("value", v ?: JSONObject.NULL)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject(e.message ?: "SecureStorage.get failed", e)
        }
    }

    @PluginMethod
    fun remove(call: PluginCall) {
        val key = call.getString("key")
        if (key == null) { call.reject("key is required"); return }
        try {
            prefs().edit().remove(key).apply()
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "SecureStorage.remove failed", e)
        }
    }
}
