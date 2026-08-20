package com.phonoleaf.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.documentfile.provider.DocumentFile
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONArray
import org.json.JSONObject

/**
 * Connect-a-folder for LocalBooks (extends the one-shot device import that
 * already ships via a plain <input type=file> — Capacitor's own bridge wires
 * that to a file picker with no plugin needed, but picking a FOLDER needs
 * Android's Storage Access Framework, which nothing in Capacitor's bridge
 * handles). Wraps ACTION_OPEN_DOCUMENT_TREE: pick a folder tree once, take a
 * PERSISTABLE permission on it so it survives app restarts, then list/read
 * EPUBs from it on demand. No background watching — refresh is entirely
 * caller-triggered from JS (LocalBooks.refreshFolder), matching the "connect
 * once, refresh manually" scope this was built for, not full sync.
 *
 * This is the first plugin in this app that launches an activity and needs a
 * RESULT back (every other plugin here just fires an intent and forgets).
 * startActivityForResult + @ActivityCallback is Capacitor's own mechanism for
 * that — verified directly against the installed Capacitor 8.4.2 core source
 * rather than assumed, since no plugin already in this project uses it.
 */
@CapacitorPlugin(name = "LocalFolder")
class LocalFolderPlugin : Plugin() {

    @PluginMethod
    fun pickFolder(call: PluginCall) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(call, intent, "onFolderPicked")
    }

    @ActivityCallback
    private fun onFolderPicked(call: PluginCall, result: ActivityResult) {
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data?.data == null) {
            // User backed out of the picker — a clean "nothing chosen" signal
            // for JS to treat as a quiet no-op, not an error.
            val ret = JSObject()
            ret.put("uri", JSONObject.NULL)
            call.resolve(ret)
            return
        }
        val uri = data.data!!
        try {
            // PERSISTABLE: without this the grant is gone the moment the
            // process dies, so "connect once, refresh later" would silently
            // break on next app launch.
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val doc = DocumentFile.fromTreeUri(context, uri)
            val ret = JSObject()
            ret.put("uri", uri.toString())
            ret.put("name", doc?.name ?: uri.lastPathSegment ?: "")
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject(e.message ?: "could not persist folder permission", e)
        }
    }

    @PluginMethod
    fun listFolder(call: PluginCall) {
        val uriStr = call.getString("uri")
        if (uriStr == null) { call.reject("uri is required"); return }
        try {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
                ?: throw IllegalStateException("folder no longer accessible")
            val files = JSONArray()
            for (f in dir.listFiles()) {
                if (!f.isFile) continue
                val name = f.name ?: continue
                val isEpub = name.endsWith(".epub", ignoreCase = true) ||
                    f.type == "application/epub+zip"
                if (!isEpub) continue
                val row = JSObject()
                row.put("name", name)
                row.put("uri", f.uri.toString())
                row.put("size", f.length())
                files.put(row)
            }
            val ret = JSObject()
            ret.put("files", files)
            call.resolve(ret)
        } catch (e: SecurityException) {
            // Permission was revoked (user cleared it in Android's own
            // settings, or the OS reclaimed it) — JS treats any rejection
            // from this call the same way: prompt to reconnect.
            call.reject("permission revoked: " + (e.message ?: ""), e)
        } catch (e: Exception) {
            call.reject(e.message ?: "could not list folder", e)
        }
    }

    @PluginMethod
    fun readFile(call: PluginCall) {
        val uriStr = call.getString("uri")
        if (uriStr == null) { call.reject("uri is required"); return }
        try {
            val bytes = context.contentResolver.openInputStream(Uri.parse(uriStr))
                ?.use { it.readBytes() }
                ?: throw IllegalStateException("could not open file")
            // Matches @capacitor/filesystem's readFile response shape
            // ({data: base64}) on purpose — JS decodes with the SAME
            // BookCache._b64ToBuf(res.data) helper already written for the
            // Filesystem plugin, no second decoder needed.
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val ret = JSObject()
            ret.put("data", b64)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject(e.message ?: "could not read file", e)
        }
    }
}
