package com.phonoleaf.app

import android.content.res.AssetManager
import android.util.Base64
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Native neural TTS for PhonoLeaf: Kokoro-82M via sherpa-onnx, exposed to the
 * web layer as synthesize(text, sid, speed) -> WAV data URL.
 *
 * The web app (TTS._synthNative) prefers this plugin when present; the same
 * prefetch pipeline that hides latency for the browser-WASM path applies here,
 * and native generation is faster than realtime on phones, so playback is
 * gapless. If the plugin is absent (web build) or a call fails (model not
 * placed yet), the web layer falls back automatically.
 *
 * Model files: the owner drops the extracted Kokoro model into
 *   android/app/src/main/assets/kokoro/   (gitignored — see TESTING.md).
 * On first use we copy that folder to filesDir once (espeak-ng-data, dict and
 * the lexicon files must be opened by native code via real filesystem paths,
 * not through the AssetManager), then load from disk.
 */
@CapacitorPlugin(name = "PhonoLeafTts")
class PhonoLeafTtsPlugin : Plugin() {

    private val ASSET_DIR = "kokoro"
    @Volatile private var tts: OfflineTts? = null
    private val lock = Any()

    private fun ensureReady(): OfflineTts {
        tts?.let { return it }
        synchronized(lock) {
            tts?.let { return it }
            val ctx = context
            val dest = File(ctx.filesDir, ASSET_DIR)
            val marker = File(dest, ".ready")
            if (!marker.exists()) {
                copyAssetDir(ctx.assets, ASSET_DIR, dest)
                marker.createNewFile()
            }
            val base = dest.absolutePath
            val cores = Runtime.getRuntime().availableProcessors()
            val cfg = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = "$base/model.onnx",
                        voices = "$base/voices.bin",
                        tokens = "$base/tokens.txt",
                        dataDir = "$base/espeak-ng-data",
                        dictDir = "$base/dict",
                        lexicon = "$base/lexicon-us-en.txt,$base/lexicon-gb-en.txt,$base/lexicon-zh.txt",
                    ),
                    // Leave one core for the UI/prefetch; never below 2.
                    numThreads = maxOf(2, cores - 1),
                    provider = "cpu",
                ),
            )
            val t = OfflineTts(assetManager = null, config = cfg)
            tts = t
            return t
        }
    }

    /** Warm the model (copy + load) ahead of first playback. */
    @PluginMethod
    fun prepare(call: PluginCall) {
        try {
            ensureReady()
            call.resolve()
        } catch (e: Throwable) {
            call.reject(e.message ?: "prepare failed", e)
        }
    }

    /** synthesize({ text, sid, speed }) -> { audio: "data:audio/wav;base64,..." } */
    @PluginMethod
    fun synthesize(call: PluginCall) {
        val text = call.getString("text")
        if (text.isNullOrBlank()) { call.reject("no text"); return }
        val sid = call.getInt("sid", 0) ?: 0
        val speed = call.getFloat("speed", 1.0f) ?: 1.0f
        try {
            val engine = ensureReady()
            // Serialize generation — cheap insurance against a prefetch call
            // overlapping the current one and spiking memory.
            val audio = synchronized(lock) { engine.generate(text, sid, speed) }
            val b64 = pcmToWavBase64(audio.samples, audio.sampleRate)
            val ret = JSObject()
            ret.put("audio", "data:audio/wav;base64,$b64")
            call.resolve(ret)
        } catch (e: Throwable) {
            call.reject(e.message ?: "synth failed", e)
        }
    }

    // Recursively copy an assets subtree to a filesystem dir. AssetManager.list
    // returns an empty array for a leaf file, a non-empty one for a directory.
    private fun copyAssetDir(am: AssetManager, src: String, dst: File) {
        val children = am.list(src) ?: emptyArray()
        if (children.isEmpty()) {
            dst.parentFile?.mkdirs()
            am.open(src).use { input -> dst.outputStream().use { out -> input.copyTo(out) } }
            return
        }
        dst.mkdirs()
        for (name in children) copyAssetDir(am, "$src/$name", File(dst, name))
    }

    // Mono 16-bit PCM WAV, base64 (NO_WRAP) — consumed as an <audio> data URL.
    private fun pcmToWavBase64(samples: FloatArray, sampleRate: Int): String {
        val n = samples.size
        val out = ByteArrayOutputStream(44 + n * 2)
        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) { out.write(v and 0xff); out.write((v ushr 8) and 0xff); out.write((v ushr 16) and 0xff); out.write((v ushr 24) and 0xff) }
        fun i16(v: Int) { out.write(v and 0xff); out.write((v ushr 8) and 0xff) }
        str("RIFF"); i32(36 + n * 2); str("WAVE")
        str("fmt "); i32(16); i16(1); i16(1); i32(sampleRate); i32(sampleRate * 2); i16(2); i16(16)
        str("data"); i32(n * 2)
        for (s in samples) {
            val clamped = if (s > 1f) 1f else if (s < -1f) -1f else s
            i16((clamped * 32767f).toInt())
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
