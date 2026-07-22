    package com.phonoleaf.app

    import android.app.ActivityManager
    import android.content.Context
    import android.content.Intent
    import android.content.res.AssetManager
    import android.util.Log
    import com.getcapacitor.JSObject
    import com.getcapacitor.Plugin
    import com.getcapacitor.PluginCall
    import com.getcapacitor.PluginMethod
    import com.getcapacitor.annotation.CapacitorPlugin
    import com.k2fsa.sherpa.onnx.OfflineTts
    import com.k2fsa.sherpa.onnx.OfflineTtsConfig
    import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
    import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
    import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
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
        // (was a real gotcha swapping kokoro-multi-lang-v1_1 → kokoro-en-v0_19 →
        // kokoro-int8-en-v0_19).
        private val MODEL_VERSION = "piper-libritts-r-medium"
        @Volatile private var tts: OfflineTts? = null
        // Which model key is currently loaded ("us"|"gb"). Voices from a different
        // accent live in a separate model folder; switching reloads (one model at
        // a time to keep RAM low — two Piper mediums at once risks OOM on low-end).
        @Volatile private var loadedModel: String? = null
        private val lock = Any()
        // Bumped by cancel() (called when the web layer stops/leaves the reader).
        // Queued-but-not-yet-started synths whose stamp is stale are skipped, so
        // leaving the reader doesn't leave 30s of dead inference pegging the CPU.
        @Volatile private var epoch = 0
        // Which onnxruntime execution provider actually loaded (currently "cpu").
        @Volatile private var activeProvider = "?"
        // Model family in use ("kokoro" or "vits"/Piper), auto-detected from the
        // placed files. Surfaced to the readout so we can confirm which engine ran.
        @Volatile private var activeModelType = "?"
    
        // Asset/filesDir subfolder for a model key. "gb" → the British model;
        // anything else ("us"/default) → the primary `kokoro` folder.
        private fun folderFor(model: String) = if (model == "gb") "kokoro-gb" else ASSET_DIR

        private fun ensureReady(model: String): OfflineTts {
            tts?.let { if (loadedModel == model) return it }
            synchronized(lock) {
                tts?.let { if (loadedModel == model) return it }
                // Switching models — drop the previous instance first so both
                // aren't resident at once (keeps peak RAM to one model).
                tts = null; loadedModel = null
                val ctx = context
                val folder = folderFor(model)
                val dest = File(ctx.filesDir, folder)
                val marker = File(dest, ".ready-$MODEL_VERSION")
                if (!marker.exists()) {
                    dest.deleteRecursively() // clear any older model copy
                    copyAssetDir(ctx.assets, folder, dest)
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
                // int8 models name the ONNX file model.int8.onnx (fp32 = model.onnx).
                // Pointing at a missing file crashes the native loader HARD (the app
                // just closes — NOT a catchable exception), so resolve the real name:
                // prefer the exact known names, else any *.onnx present. If none, we
                // throw a *catchable* error → the web layer falls back to the device
                // voice instead of the app crashing.
                val modelFile = when {
                    File(dest, "model.onnx").exists() -> "model.onnx"
                    File(dest, "model.int8.onnx").exists() -> "model.int8.onnx"
                    else -> dest.listFiles { f -> f.name.endsWith(".onnx") }?.firstOrNull()?.name
                        ?: throw java.io.FileNotFoundException(
                            "No *.onnx in $base — is the $folder model placed? (TESTING.md 3.6)")
                }
                val lexicon = listOf("lexicon-us-en.txt", "lexicon-gb-en.txt", "lexicon-zh.txt")
                    .map { File(dest, it) }.filter { it.exists() }
                    .joinToString(",") { it.absolutePath }
                val cores = Runtime.getRuntime().availableProcessors()
                // big.LITTLE tuning (measured on the owner's 8-core phone):
                //   cores-1 (7 threads) → ratio ~2.4x realtime (little cores drag)
                //   2 threads           → ratio ~1.6x
                // Modern 8-core phones have ~4 fast cores (prime+performance) + ~4
                // efficiency cores. Use up to 4 threads to fill the fast cores
                // WITHOUT spilling onto the slow ones — ~2x the compute of 2
                // threads, aiming for ratio < 1 (gapless). Capped at 4 so bigger
                // phones don't start using little cores.
                val threads = maxOf(2, minOf(4, cores - 4))
                // Auto-detect the model FAMILY from the files present, so the same
                // plugin runs either engine (Piper baseline now; Kokoro later as a
                // premium voice on capable devices — just swap the model files):
                //   voices.bin present → Kokoro (separate speaker-embedding file)
                //   otherwise          → VITS / Piper (espeak-based, no voices.bin)
                // NNAPI was dropped: it engaged on the Pixel but didn't accelerate
                // the TTS model (~1.45x, no better than CPU) — CPU only now.
                val hasVoices = File(dest, "voices.bin").exists()
                activeModelType = if (hasVoices) "kokoro" else "vits"
                activeProvider = "cpu"
                val modelCfg = if (hasVoices) {
                    OfflineTtsModelConfig(
                        kokoro = OfflineTtsKokoroModelConfig(
                            model = "$base/$modelFile",
                            voices = "$base/voices.bin",
                            tokens = "$base/tokens.txt",
                            dataDir = ifExists("espeak-ng-data"),
                            dictDir = ifExists("dict"),
                            lexicon = lexicon,
                        ),
                        numThreads = threads,
                        provider = "cpu",
                    )
                } else {
                    OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = "$base/$modelFile",
                            tokens = "$base/tokens.txt",
                            dataDir = ifExists("espeak-ng-data"), // Piper is espeak-based
                        ),
                            numThreads = threads,
                        provider = "cpu",
                    )
                }
                Log.i("PhonoLeafTts", "init model=$model folder=$folder file=$modelFile type=$activeModelType threads=$threads")
                val t = OfflineTts(assetManager = null, config = OfflineTtsConfig(model = modelCfg))
                tts = t
                loadedModel = model
                return t
            }
        }
    
        /** Warm the model (copy + load) ahead of first playback. Resolves with the
         *  detected model family so the web picker shows the right voice catalog
         *  before the first synth. */
        @PluginMethod
        fun prepare(call: PluginCall) {
            try {
                ensureReady(call.getString("model") ?: "us")
                val ret = JSObject()
                ret.put("modelType", activeModelType)
                call.resolve(ret)
            } catch (e: Throwable) {
                // Catch Throwable (a big model load can OOM = an Error, not an
                // Exception), but reject() only takes Exception — wrap when needed.
                call.reject(e.message ?: "prepare failed", e as? Exception ?: RuntimeException(e))
            }
        }
    
        /** Skip any queued synths (stop / leaving the reader) so we don't burn CPU
         *  finishing audio nobody will hear. Can't interrupt an in-flight generate,
         *  but clears everything still waiting. */
        @PluginMethod
        fun cancel(call: PluginCall) {
            epoch++
            call.resolve()
        }

        /** Start the media-playback foreground service so the WebView's <audio>
         *  chain keeps running with the screen off / app backgrounded.
         *  startPlaybackService({ title, text }) */
        @PluginMethod
        fun startPlaybackService(call: PluginCall) {
            try {
                // CRITICAL: only start the foreground service while the app is
                // genuinely in the foreground. On Android 12+ starting a
                // mediaPlayback FGS from a non-foreground state is disallowed —
                // startForeground() then throws and Android force-crashes us with
                // ForegroundServiceDidNotStartInTimeException (observed on device
                // when pressing play right as the app resumes from the lock
                // screen, mid keyguard transition). Skipping the start there
                // costs only background playback for that press; foreground
                // reading is unaffected and the app stays up. Once the service is
                // running (started from a proper foreground press) it survives the
                // screen turning off — which is the case we actually need.
                if (!appInForeground()) {
                    Log.w("PhonoLeafPlayback", "not foreground — skipping FGS start to avoid crash")
                    call.resolve()
                    return
                }
                val i = Intent(context, PlaybackService::class.java)
                i.putExtra(PlaybackService.EXTRA_TITLE, call.getString("title") ?: "PhonoLeaf")
                i.putExtra(PlaybackService.EXTRA_TEXT, call.getString("text") ?: "Reading aloud")
                // Use startService, NOT startForegroundService. We're foreground
                // here (guarded above), so startService is allowed — and crucially
                // it does NOT arm Android's 5s "must call startForeground()"
                // watchdog. startForegroundService armed that watchdog, and when
                // the main thread was busy at play time (resync page-turns + model
                // load) the service's onStartCommand couldn't call startForeground()
                // within 5s, so Android force-crashed us with
                // ForegroundServiceDidNotStartInTimeException (uncatchable, fires
                // system-side — the exact device crash, twice). The service still
                // calls startForeground() in onStartCommand to become a real FGS
                // that survives the screen turning off; without the watchdog, a
                // late startForeground() is fine instead of fatal.
                context.startService(i)
                call.resolve()
            } catch (e: Throwable) {
                // Reject rather than crash — the web layer just loses background
                // playback, foreground reading is unaffected.
                call.reject(e.message ?: "startPlaybackService failed", e as? Exception ?: RuntimeException(e))
            }
        }

        /** Is this app's process at least foreground/visible right now? Used to
         *  gate the FGS start (see startPlaybackService). runningAppProcesses only
         *  returns our own process on modern Android, so this is self-scoped. */
        private fun appInForeground(): Boolean {
            return try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mine = am.runningAppProcesses ?: return false
                val myPid = android.os.Process.myPid()
                mine.any {
                    it.pid == myPid &&
                    it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                }
            } catch (e: Throwable) {
                false // if we can't tell, err on NOT starting — a missed background
                      // session beats a crash
            }
        }

        /** Release the foreground service (playback stopped / left the reader). */
        @PluginMethod
        fun stopPlaybackService(call: PluginCall) {
            try {
                context.stopService(Intent(context, PlaybackService::class.java))
                call.resolve()
            } catch (e: Throwable) {
                call.reject(e.message ?: "stopPlaybackService failed", e as? Exception ?: RuntimeException(e))
            }
        }
    
        /** synthesize({ text, sid, speed, model }) -> { path, durationMs } */
        @PluginMethod
        fun synthesize(call: PluginCall) {
            val text = call.getString("text")
            if (text.isNullOrBlank()) { call.reject("no text"); return }
            val sid = call.getInt("sid", 0) ?: 0
            val speed = call.getFloat("speed", 1.0f) ?: 1.0f
            val model = call.getString("model") ?: "us"
            val stamp = epoch
            // Off the main thread + serialized: the single-thread executor runs one
            // generation at a time, so a prefetch never overlaps the current synth.
            genExecutor.execute {
                try {
                    // A cancel() since this was queued means the page/session moved
                    // on — skip the (potentially multi-second) generation entirely.
                    if (stamp != epoch) { call.reject("cancelled"); return@execute }
                    val engine = ensureReady(model)
                    val t0 = System.currentTimeMillis()
                    val audio = engine.generate(text, sid, speed)
                    val genMs = System.currentTimeMillis() - t0
                    val durationMs = (audio.samples.size.toLong() * 1000 /
                        maxOf(1, audio.sampleRate)).toInt()
                    // Pure native generation timing — readable in Android Studio's
                    // Logcat (filter tag "PhonoLeafTts"). ratio<1 = faster than
                    // realtime (gaps aren't generation speed); ratio>1 = too slow.
                    val ratio = genMs.toFloat() / maxOf(1, durationMs)
                    Log.i("PhonoLeafTts",
                        "gen=${genMs}ms audio=${durationMs}ms ratio=${"%.2f".format(ratio)} chars=${text.length}")
                    val f = writeWavFile(audio.samples, audio.sampleRate)
                    val ret = JSObject()
                    ret.put("path", f.absolutePath)
                    ret.put("durationMs", durationMs)
                    ret.put("provider", activeProvider)
                    ret.put("modelType", activeModelType)
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
            // Peak-normalize each clip to a consistent level so different models/
            // voices match in loudness (the vctk/UK model is quieter than the
            // libritts/US one). Gain capped so near-silent clips aren't blown up.
            var peak = 0f
            for (s in samples) { val a = if (s < 0f) -s else s; if (a > peak) peak = a }
            val gain = if (peak > 0.001f) minOf(6f, 0.95f / peak) else 1f
            fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
            fun i32(v: Int) { out.write(v and 0xff); out.write((v ushr 8) and 0xff); out.write((v ushr 16) and 0xff); out.write((v ushr 24) and 0xff) }
            fun i16(v: Int) { out.write(v and 0xff); out.write((v ushr 8) and 0xff) }
            str("RIFF"); i32(36 + n * 2); str("WAVE")
            str("fmt "); i32(16); i16(1); i16(1); i32(sampleRate); i32(sampleRate * 2); i16(2); i16(16)
            str("data"); i32(n * 2)
            for (s in samples) {
                val g = s * gain
                val clamped = if (g > 1f) 1f else if (g < -1f) -1f else g
                i16((clamped * 32767f).toInt())
            }
        }
    }
