package com.phonoleaf.app

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File

/**
 * Opens the device's email app DIRECTLY, bypassing the generic "share to any
 * app" chooser Capacitor's own Share.share() always shows (Gmail, Messages,
 * Bluetooth, Nearby Share, WhatsApp, Drive...) — confusing when what the user
 * actually wants is "compose an email" (owner-reported: had no idea they were
 * meant to tap their mail app out of that unrelated list).
 *
 * Uses Android's own long-established convention for this instead: an
 * ACTION_SEND intent typed "message/rfc822", which only email apps register
 * an intent-filter for. The OS resolves it itself — with exactly one mail app
 * installed (the common case) it launches straight into the compose screen,
 * no picker at all; with more than one, the picker it shows is restricted to
 * just those apps, not the full share sheet.
 */
@CapacitorPlugin(name = "EmailComposer")
class EmailComposerPlugin : Plugin() {
    @PluginMethod
    fun compose(call: PluginCall) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                call.getString("to")?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
                putExtra(Intent.EXTRA_SUBJECT, call.getString("subject") ?: "")
                putExtra(Intent.EXTRA_TEXT, call.getString("body") ?: "")
                call.getString("attachmentUri")?.let { raw ->
                    // JS passes the file:// URI that Filesystem.writeFile()
                    // returned. That's fine within our own process, but
                    // Android forbids handing a raw file:// URI to ANOTHER
                    // app's process — throws FileUriExposedException on API
                    // 24+. It has to be wrapped through the FileProvider
                    // already configured in AndroidManifest.xml /
                    // file_paths.xml (originally set up for @capacitor/share,
                    // which does this same conversion internally) into a
                    // content:// URI the receiving app is actually permitted
                    // to read.
                    val file = File(raw.removePrefix("file://"))
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            // activity, NOT context: startActivity() from a non-Activity
            // context throws unless FLAG_ACTIVITY_NEW_TASK is set. The
            // Activity context needs neither the flag nor that workaround.
            activity.startActivity(intent)
            call.resolve()
        } catch (e: ActivityNotFoundException) {
            // No app registered for message/rfc822 at all — vanishingly rare
            // (every Android device ships or nudges toward Gmail), but must
            // not crash; JS falls back to a plain mailto: link on rejection.
            call.reject("No email app found", e)
        } catch (e: Exception) {
            call.reject(e.message ?: "compose failed", e)
        }
    }
}
