package com.phonoleaf.app

import android.content.res.AssetManager
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * Native neural TTS for PhonoLeaf: Kokoro-82M via sherpa-onnx, exposed to the
 * web layer as synthesize(text, sid, speed) -> { path, durationMs }.
 *
 * The web app (TTS._synthNative) prefers this plugin when present; the same
 * prefetch pipeline that hides latency for the browser-WASM path applies here.
 * If the plugin is absent (web build) or a call fails (model not placed yet),
 * the web layer falls back automatically.
 *
 * Audio is written to a WAV FILE in cacheDir and returned as a path — the web
 * side loads it via Capacitor.convertFileSrc(). We deliberately do NOT return
 * base64: a ~1 MB base64 string per sentence crossing the bridge and being
 * decoded into a data: URL froze the WebView's main thread (the reader UI
 * stopped responding — even the back button). File + local-server streaming
 * keeps the main thread free.
 *
 * Generation runs on a private single-thread executor (serialized, off the
 * main thread) and sherpa's internal thread count is capped so ONNX inference
 * can't starve the UI/render threads.
 *
 * Model files: the owner drops the extracted Kokoro model into
 *   android/app/src/main/assets/kokoro/   (gitignored — see TESTING.md).
 * On first use we copy that folder to filesDir once (espeak-ng-data / dict /
 * lexicon files must be opened by native code via real filesystem paths, not
 * through the AssetManager), then load from disk.
 */
@CapacitorPlugin(name = "PhonoLeafTts")
class PhonoLeafTtsPlugin : Plugin() {

    private val ASSET_DIR = "kokoro"
    // Serializes generation off the main thread; prefetch + on-demand calls
    // queue here instead of overlapping (which would spike memory/CPU).
    private val genExecutor = Executors.newSingleThreadExecutor()
    // Ring of reused WAV filenames in cacheDir. Prefetch keeps at most ~2 files
    // live at once; a ring of 8 means a slot is never reused while still playing.
    private var fileCounter = 0
    private val RING = 8
    // Bump when the bundled model changes — the copied filesDir/kokoro is cached
    // behind this marker, so a new asset model won't be picked up otherwise
    // (was a real gotcha swapping kokoro-multi-lang-v1_1 → kokoro-en-v0_19).
    private val MODEL_VERSION = "en-v0_19"
    @Volatile private var tts: OfflineTts? = null
    private val lock = Any()

    private fun ensureReady(): OfflineTts {
        tts?.let { return it }
        synchronized(lock) {
            tts?.let { return it }
            val ctx = context
            val dest = File(ctx.filesDir, ASSET_DIR)
            val marker = File(dest, ".ready-$MODEL_VERSION")
            if (!marker.exists()) {
                dest.deleteRecursively() // clear any older model copy
                copyAssetDir(ctx.assets, ASSET_DIR, dest)
                marker.createNewFile()
            }
            val base = dest.absolutePath
            // Only set optional paths that actually exist — the English-only
            // model (kokoro-en-v0_19) ships espeak-ng-data but no dict/ or
            // lexicon files (those are for the Chinese multi-lang models), and
            // sherpa rejects paths that point at nothing.
            fun ifExists(rel: String): String {
                return if (File(dest, rel).exists()) "$base/$rel" else ""
            }
            val lexicon = listOf("lexicon-us-en.txt", "lexicon-gb-en.txt", "lexicon-zh.txt")
                .map { File(dest, it) }.filter { it.exists() }
                .joinToString(",") { it.absolutePath }
            val cores = Runtime.getRuntime().availableProcessors()
            val cfg = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = "$base/model.onnx",
                        voices = "$base/voices.bin",
                        tokens = "$base/tokens.txt",
                        dataDir = ifExists("espeak-ng-data"),
                        dictDir = ifExists("dict"),
                        lexicon = lexicon,
                    ),
                    // Cap inference threads so ONNX can't saturate every core
                    // and starve the UI/render thread (cores-1 made the reader
                    // unresponsive). Half the cores, clamped to 2..4.
                    numThreads = maxOf(2, minOf(4, cores / 2)),
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
            // Catch Throwable (a big model load can OOM = an Error, not an
            // Exception), but reject() only takes Exception — wrap when needed.
            call.reject(e.message ?: "prepare failed", e as? Exception ?: RuntimeException(e))
        }
    }

    /** synthesize({ text, sid, speed }) -> { path, durationMs } */
    @PluginMethod
    fun synthesize(call: PluginCall) {
        val text = call.getString("text")
        if (text.isNullOrBlank()) { call.reject("no text"); return }
        val sid = call.getInt("sid", 0) ?: 0
        val speed = call.getFloat("speed", 1.0f) ?: 1.0f
        // Off the main thread + serialized: the single-thread executor runs one
        // generation at a time, so a prefetch never overlaps the current synth.
        genExecutor.execute {
            try {
                val engine = ensureReady()
                val audio = engine.generate(text, sid, speed)
                val durationMs = (audio.samples.size.toLong() * 1000 /
                    maxOf(1, audio.sampleRate)).toInt()
                val f = writeWavFile(audio.samples, audio.sampleRate)
                val ret = JSObject()
                ret.put("path", f.absolutePath)
                ret.put("durationMs", durationMs)
                call.resolve(ret)
            } catch (e: Throwable) {
                call.reject(e.message ?: "synth failed", e as? Exception ?: RuntimeException(e))
            }
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

    // Write a mono 16-bit PCM WAV into cacheDir and return the file. Filenames
    // are reused round-robin (RING slots) so the cache never grows unbounded;
    // with prefetch only ~2 files are live, so a slot is free long before reuse.
    private fun writeWavFile(samples: FloatArray, sampleRate: Int): File {
        val dir = File(context.cacheDir, "tts").apply { mkdirs() }
        val f = File(dir, "s${fileCounter++ % RING}.wav")
        BufferedOutputStream(FileOutputStream(f)).use { out ->
            writeWav(out, samples, sampleRate)
        }
        return f
    }

    private fun writeWav(out: OutputStream, samples: FloatArray, sampleRate: Int) {
        val n = samples.size
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
    }
}
