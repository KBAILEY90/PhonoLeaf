    package com.phonoleaf.app

    import android.app.ActivityManager
    import android.content.Context
    import android.content.Intent
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
    import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
    import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
    import java.io.BufferedOutputStream
    import java.io.File
    import java.io.FileNotFoundException
    import java.io.FileOutputStream
    import java.io.IOException
    import java.io.InputStream
    import java.io.InterruptedIOException
    import java.io.OutputStream
    import java.net.HttpURLConnection
    import java.net.URL
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
     * Model files: NOTHING is bundled in the APK any more (unbundled
     * 2026-08-04) — every voice model, including the US default, is a
     * downloadable pack fetched straight into filesDir on demand via
     * downloadPack(). See VOICE_PACKS for the catalog and for why bundling was
     * dropped (it stored the US model twice on device, ~157 MB total, with only
     * the filesDir half ever reclaimable). ensureReady() throws a
     * distinguishable PackNotDownloadedException until a pack has been
     * downloaded; packStatus() lets the UI check that state up front, and the
     * onboarding flow prompts for a first download right after folder setup.
     *
     * Models must live on the real filesystem rather than being read from the
     * APK directly: espeak-ng-data (and dict/lexicon for some models) are
     * opened by native code with ordinary file I/O, which cannot go through
     * AssetManager. That constraint is what made bundling cost double.
     */
    @CapacitorPlugin(name = "PhonoLeafTts")
    class PhonoLeafTtsPlugin : Plugin() {

        companion object {
            // Weak so this never keeps a destroyed plugin/Activity alive — PlaybackService
            // (a separate Android component, not directly wired to the Capacitor bridge)
            // uses this to reach back into JS when a lock-screen media button is pressed.
            private var instanceRef: java.lang.ref.WeakReference<PhonoLeafTtsPlugin>? = null

            /** action: "pause" | "play" — forwarded to JS as a "mediaButton" event
             *  (see TTS._mediaSetup in index.html). Silently does nothing if the
             *  plugin isn't currently loaded (e.g. app fully backgrounded/killed —
             *  there's no JS to notify in that case anyway). */
            fun notifyMediaButton(action: String) {
                val plugin = instanceRef?.get() ?: return
                try {
                    val data = JSObject()
                    data.put("action", action)
                    plugin.notifyListeners("mediaButton", data)
                } catch (_: Throwable) { /* best-effort — never crash the service over this */ }
            }
        }

        override fun load() {
            super.load()
            instanceRef = java.lang.ref.WeakReference(this)
        }

        private val ASSET_DIR = "kokoro"
        // Serializes generation off the main thread; prefetch + on-demand calls
        // queue here instead of overlapping (which would spike memory/CPU).
        private val genExecutor = Executors.newSingleThreadExecutor()
        // Ring of reused WAV filenames in cacheDir. Prefetch keeps at most ~2 files
        // live at once; a ring of 8 means a slot is never reused while still playing.
        private var fileCounter = 0
        private val RING = 8
        // Per-model loudness calibration (see writeWav's resolveGain) — only ever
        // touched from genExecutor's single-thread queue, so no synchronization
        // needed despite being shared mutable state.
        private class GainCalib { var count = 0; var peakSum = 0f; var locked: Float? = null }
        private val gainCalib = HashMap<String, GainCalib>()
        private val GAIN_CALIB_CLIPS = 4
        // Per-model version tag for the ".ready-$tag" marker file. MUST be
        // per-model, not a single shared string — a single shared constant was
        // the actual bug behind "both French/Spanish voices sound identical"
        // (owner-reported 2026-08-05): fr/es were switched from single-speaker
        // models (siwis/davefx) to genuine 2-speaker ones (upmc/sharvard), but
        // since the marker didn't encode WHICH model was downloaded, a device
        // that already had the old pack saw its marker still present and
        // never re-fetched — so it kept using the stale single-speaker file,
        // where a second "voice" is just the same lone speaker again. Bump
        // ONLY the entry for a model whose underlying file actually changes;
        // us/gb/de are unchanged and must keep their original tag, or every
        // install would needlessly re-download ~78 MB it already has (the
        // exact thing keeping the "kokoro" folder name constant was meant to
        // avoid — see VOICE_PACKS's "us" comment).
        // NOTE: the Spanish pack (es_ES-sharvard) was REMOVED 2026-08-31 on a
        // licence finding, not a technical one: its model card claims CC BY 3.0
        // but also says it was fine-tuned from lessac, which carries the
        // Blizzard licence excluding commercial voice synthesis products. Do not
        // re-add it without a replacement whose licence is clean for production.
        private val MODEL_VERSIONS = mapOf(
            "us" to "piper-libritts-r-medium",
            "gb" to "piper-libritts-r-medium",
            "fr" to "piper-fr-upmc-medium-2spk",
            "de" to "piper-libritts-r-medium",
            "kokoro" to "kokoro-int8-en-v0_19",
        )
        private fun modelVersion(model: String) = MODEL_VERSIONS[model] ?: "piper-libritts-r-medium"
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
    
        // filesDir subfolder for a model key. Every model is downloadable now, so
        // this always resolves from VOICE_PACKS; ASSET_DIR remains only as a
        // defensive fallback for an unknown key.
        private fun folderFor(model: String) = VOICE_PACKS[model]?.folder ?: ASSET_DIR

        // ALL voice packs are fetched on demand into filesDir — nothing ships in
        // the APK's assets any more (unbundled 2026-08-04). Previously the US
        // model was bundled, which meant it existed TWICE on device: once in the
        // APK's assets (undeletable — part of the install package) and once as
        // the filesDir copy the native engine actually needs (espeak-ng uses
        // ordinary file I/O and can't read through AssetManager). That put a
        // US-only install at ~157 MB and made "Remove" able to reclaim only half
        // of it. Downloading it like every other pack leaves exactly ONE copy,
        // fully deletable, and drops the store download to app code only.
        // Source is the same public sherpa-onnx GitHub release for all of them
        // (TESTING.md §3.6) — no separate hosting to maintain. Sizes are the
        // exact `.tar.bz2` asset sizes from that release (checked 2026-08-04 via
        // `gh release view tts-models --repo k2-fsa/sherpa-onnx`), not estimates.
        private data class VoicePackInfo(val folder: String, val url: String, val approxBytes: Long)
        private val VOICE_PACKS = mapOf(
            // Folder stays "kokoro" (NOT "kokoro-us") deliberately: an existing
            // install that already has the old asset-copied model in
            // filesDir/kokoro keeps its valid `.ready-${modelVersion("us")}`
            // marker (unchanged tag — see MODEL_VERSIONS), so packStatus
            // reports it as already downloaded and nobody has to re-fetch
            // ~80 MB just because it stopped being bundled.
            "us" to VoicePackInfo(
                folder = ASSET_DIR,
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-libritts_r-medium.tar.bz2",
                approxBytes = 82038311L,
            ),
            "gb" to VoicePackInfo(
                folder = "kokoro-gb",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-vctk-medium.tar.bz2",
                approxBytes = 80488085L,
            ),
            // fr/es switched 2026-08-05 to genuine 2-speaker Piper models
            // (confirmed via each model's own config.json speaker_id_map, not
            // assumed from file size) so each language gets a real male +
            // female voice from ONE download, matching US/GB — see the
            // PIPER_VOICES comment in index.html for the exact speaker_id_map
            // and the resulting sid→voice assignment.
            "fr" to VoicePackInfo(
                folder = "kokoro-fr",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fr_FR-upmc-medium.tar.bz2",
                approxBytes = 80422639L,
            ),
            // German still single-speaker (thorsten) — no matching-quality
            // multi-speaker or second single-speaker female voice was found in
            // the catalog; see the PIPER_VOICES comment in index.html.
            "de" to VoicePackInfo(
                folder = "kokoro-de",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-de_DE-thorsten-medium.tar.bz2",
                approxBytes = 67214254L,
            ),
            // English, Kokoro-82M v0.19, int8 — the ONLY Kokoro release that
            // fits this app: the "multi-lang" Kokoro releases add Chinese, not
            // French/German/Spanish, so there is no Kokoro coverage for those
            // languages at all (confirmed by downloading and inspecting the
            // actual release assets, 2026-08-08 — the multi-lang model's own
            // embedded speaker metadata lists 3 English speakers against 100
            // Mandarin ones). This model covers BOTH US and UK English (11
            // speakers total — see KOKORO_VOICES in index.html, sids verified
            // against this exact model's onnx metadata) from one download, so
            // it replaces both the "us" and "gb" Piper packs when a device
            // qualifies for it (see VoicePacks' English gating in index.html).
            // Folder deliberately distinct from "us"/"gb" so a device that
            // falls back to Piper can keep both installed side by side without
            // collision (harmless — the gate only offers one at a time in the
            // UI, but nothing stops both existing on disk).
            "kokoro" to VoicePackInfo(
                folder = "kokoro-en-hq",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2",
                approxBytes = 103248205L,
            ),
        )

        // Thrown when a downloadable model's files aren't on disk yet. Message is
        // prefixed so the JS layer can detect this SPECIFIC condition (prompt to
        // download) rather than treating it like any other synth failure (silent
        // strike-out to the device voice) — a user who explicitly picked a GB
        // voice should be told why it's not speaking, not silently hear the wrong
        // one. Extends FileNotFoundException so it's still caught by every
        // existing `catch (e: Throwable)` site unchanged.
        private class PackNotDownloadedException(model: String) :
            FileNotFoundException("PACK_NOT_DOWNLOADED:$model")

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
                val marker = File(dest, ".ready-${modelVersion(model)}")
                if (!marker.exists()) {
                    // Nothing ships in assets any more — every model, including
                    // "us", has to come through downloadPack() first. Throwing
                    // the distinguishable exception lets the JS layer prompt for
                    // the download instead of treating this like an engine
                    // failure and striking out the natural voice entirely.
                    throw PackNotDownloadedException(model)
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
                val threads = inferenceThreads()
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
    
        // Thread count for ONNX inference, shared by ensureReady() and
        // deviceBench() so the benchmark exercises exactly the parallelism the
        // real engine will get.
        // big.LITTLE tuning (measured on the owner's 8-core phone):
        //   cores-1 (7 threads) → ratio ~2.4x realtime (little cores drag)
        //   2 threads           → ratio ~1.6x
        // Modern 8-core phones have ~4 fast cores (prime+performance) + ~4
        // efficiency cores. Use up to 4 threads to fill the fast cores WITHOUT
        // spilling onto the slow ones — ~2x the compute of 2 threads, aiming
        // for ratio < 1 (gapless). Capped at 4 so bigger phones don't start
        // using little cores.
        private fun inferenceThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return maxOf(2, minOf(4, cores - 4))
        }

        // Kept only so the JIT can't eliminate the benchmark's inner loop as
        // dead code. The value is meaningless; races on it are harmless.
        @Volatile private var benchSink = 0f

        /** deviceBench() -> { gflops, ms, threads, cores }
         *
         *  A pure-CPU matrix-multiply benchmark, used to decide BEFORE
         *  downloading anything whether this device can likely sustain the
         *  heavier Kokoro model in realtime (see VoicePacks.screenDevice in
         *  index.html). Nothing is fetched and no model is needed — it's ~0.5s
         *  of arithmetic, so onboarding can screen the device behind a brief
         *  loading screen instead of downloading a model just to time it.
         *
         *  WHY SYNTHETIC RATHER THAN A LOOKUP TABLE OR AN OS API: Android's own
         *  performance-class API is OEM-declared (frequently absent) and its
         *  criteria are media-pipeline oriented, not CPU inference — the Pixel 7
         *  rates highly there yet cannot run Kokoro in realtime, so it would
         *  give the wrong answer on the one device we have real data for.
         *  Public benchmark databases have no runtime API and would mean
         *  maintaining a chipset table forever, missing exactly the new phones
         *  most likely to qualify. Measuring the actual CPU is the only signal
         *  that stays correct on hardware that doesn't exist yet.
         *
         *  CAVEAT: scalar JVM float math doesn't use the hand-tuned NEON kernels
         *  onnxruntime does, so this correlates with real inference throughput
         *  without matching it. It is deliberately only a SCREEN — anything it
         *  green-lights is still verified by a real Kokoro synthesis before
         *  being kept (TTS._benchKokoroGate), and the JS threshold errs toward
         *  Piper because a wrong "yes" costs stuttering audio while a wrong
         *  "no" only costs a missed upgrade.
         */
        @PluginMethod
        fun deviceBench(call: PluginCall) {
            genExecutor.execute {
                try {
                    val threads = inferenceThreads()
                    val n = 160
                    // Warm up: let the JIT compile the inner loop and let the
                    // scheduler migrate these threads onto the big cores before
                    // anything is timed.
                    repeat(2) { matMulParallel(n, threads, 1) }
                    // Best-of-3: any single run can land on a scheduling hiccup
                    // or a thermal dip; the fastest is the most representative
                    // of what the device can actually sustain.
                    var bestMs = Long.MAX_VALUE
                    val reps = 4
                    repeat(3) {
                        val t0 = System.nanoTime()
                        matMulParallel(n, threads, reps)
                        val ms = (System.nanoTime() - t0) / 1_000_000
                        if (ms < bestMs) bestMs = ms
                    }
                    // Each worker does `reps` independent n x n multiplies;
                    // one is 2*n^3 flops (a multiply and an add per term).
                    val flops = 2.0 * n * n * n * reps * threads
                    val gflops = flops / (maxOf(1L, bestMs) * 1e6)
                    Log.i("PhonoLeafTts", "deviceBench threads=$threads ms=$bestMs gflops=${"%.2f".format(gflops)}")
                    val ret = JSObject()
                    ret.put("gflops", gflops)
                    ret.put("ms", bestMs)
                    ret.put("threads", threads)
                    ret.put("cores", Runtime.getRuntime().availableProcessors())
                    call.resolve(ret)
                } catch (e: Throwable) {
                    call.reject(e.message ?: "deviceBench failed", e as? Exception ?: RuntimeException(e))
                }
            }
        }

        // `threads` workers each run `repsPerThread` independent n x n float
        // matrix multiplies in parallel, measuring aggregate throughput across
        // the same cores inference would use. ikj loop order keeps the inner
        // loop walking contiguous memory (the cache-friendly ordering).
        private fun matMulParallel(n: Int, threads: Int, repsPerThread: Int) {
            val workers = (0 until threads).map { w ->
                Thread {
                    val a = FloatArray(n * n) { ((it * 31 + w) % 17) * 0.06f }
                    val b = FloatArray(n * n) { ((it * 17 + w) % 13) * 0.08f }
                    val c = FloatArray(n * n)
                    repeat(repsPerThread) {
                        for (i in 0 until n) {
                            val iN = i * n
                            for (k in 0 until n) {
                                val aik = a[iN + k]
                                val kN = k * n
                                for (j in 0 until n) c[iN + j] += aik * b[kN + j]
                            }
                        }
                    }
                    benchSink = c[(w * 7) % (n * n)]
                }
            }
            workers.forEach { it.start() }
            workers.forEach { it.join() }
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

        // PER-MODEL epoch, bumped by cancelDownload(model) or a fresh
        // downloadPack(model) call for the SAME model, so an in-flight
        // download's loop notices and unwinds instead of racing a second call
        // for that model or continuing after the caller gave up. Keyed by
        // model — MUST NOT be a single shared counter: an earlier bug used one
        // global epoch, so starting pack B's download bumped it and instantly
        // looked like a cancel to pack A's still-running loop, even though
        // nothing about A was cancelled (owner-reported: downloading two packs
        // at once froze both, then the first in line self-cancelled).
        private val downloadEpochs = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
        private fun bumpDownloadEpoch(model: String) =
            downloadEpochs.computeIfAbsent(model) { java.util.concurrent.atomic.AtomicInteger(0) }.incrementAndGet()
        private fun currentDownloadEpoch(model: String) = downloadEpochs[model]?.get() ?: 0

        // Dedicated queue for pack downloads, separate from genExecutor (which
        // runs TTS synthesis). Two reasons: (1) the owner asked for downloads
        // to queue rather than race each other — a single-thread executor does
        // exactly that, serializing them in request order; (2) sharing
        // genExecutor would mean a multi-minute pack download blocks every
        // synthesize() call behind it, freezing audio playback while a
        // download is in flight, which is unrelated but would be an easy new
        // bug to introduce while fixing this one.
        // Being on a SEPARATE thread from genExecutor isn't enough on its own,
        // though — the two threads still compete for the same CPU cores, and
        // bzip2 decompression is genuinely CPU-heavy. Owner-reported: voices
        // "mumbling" (garbled, not just slow) while a pack downloads in the
        // background. Fixed by running this thread at Android's own
        // THREAD_PRIORITY_BACKGROUND (a real scheduler nice-value hint, not
        // just the JVM's Thread.priority, which Android's CFS scheduler
        // mostly ignores) — set once, the first (and only, it's a
        // single-thread pool) time this thread runs, so the OS consistently
        // favors TTS inference and audio playback over pack downloads
        // whenever both want CPU at once.
        private val downloadExecutor: java.util.concurrent.ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                    runnable.run()
                }
            }

        /** packStatus({model}) -> {downloaded, approxBytes}. Uniform for every
         *  model now that nothing is bundled — the `.ready-${modelVersion(model)}`
         *  marker in the pack's filesDir folder is the single source of truth,
         *  so nothing has to be mirrored into JS storage. An unknown key
         *  (shouldn't happen; the JS catalog only asks about real entries)
         *  reports not-downloaded with no size rather than pretending it's
         *  present. */
        @PluginMethod
        fun packStatus(call: PluginCall) {
            val model = call.getString("model")
            if (model.isNullOrEmpty()) { call.reject("model required"); return }
            val info = VOICE_PACKS[model]
            val dest = File(context.filesDir, folderFor(model))
            val ret = JSObject()
            ret.put("downloaded", info != null && File(dest, ".ready-${modelVersion(model)}").exists())
            ret.put("approxBytes", info?.approxBytes ?: 0)
            call.resolve(ret)
        }

        /** downloadPack({model}) — streams the model's .tar.bz2 straight into
         *  filesDir, extracting as it goes (no separate on-disk archive copy).
         *  Emits "packProgress" events ({model, downloaded, total, pct}) at most
         *  5x/sec while it runs; resolves once the pack is ready to use, rejects
         *  on any network/disk/cancel failure (nothing partial is left usable —
         *  see the tmp-dir swap below). */
        @PluginMethod
        fun downloadPack(call: PluginCall) {
            val model = call.getString("model")
            if (model.isNullOrEmpty()) { call.reject("model required"); return }
            val info = VOICE_PACKS[model]
            if (info == null) { call.reject("no such voice pack: $model"); return }
            val stamp = bumpDownloadEpoch(model)
            downloadExecutor.execute {
                val folder = folderFor(model)
                val dest = File(context.filesDir, folder)
                // Extract into a scratch dir first, swap in only on full success —
                // a failed/cancelled download must never leave a half-written
                // folder that ensureReady() then treats as ready.
                val tmp = File(context.filesDir, "$folder-tmp")
                var conn: HttpURLConnection? = null
                // Tracks whether THIS task told PackDownloadService it started, so
                // the finally block only calls finish() for a task that actually
                // called start() — a task cancelled while still queued (below)
                // never touches the service at all, so it must not decrement a
                // counter it never incremented.
                var serviceStarted = false
                try {
                    // Cancelled while still QUEUED behind another download —
                    // bail before opening a connection. Without this the task
                    // happily downloaded the whole archive anyway and only
                    // noticed at the first tar-entry check.
                    if (stamp != currentDownloadEpoch(model)) throw InterruptedIOException("cancelled")
                    // Keep the download alive with the screen off — without this
                    // the download thread and network access get suspended by
                    // Doze/App Standby the moment the screen locks, and the
                    // transfer stalls and never completes (owner-reported
                    // 2026-08-08). Mirrors why PlaybackService exists for TTS
                    // playback; see PackDownloadService.kt.
                    PackDownloadService.start(context, model)
                    serviceStarted = true
                    // Fires the MOMENT this task actually starts running — i.e. once
                    // downloadExecutor has dequeued it, not when downloadPack() was
                    // called. A download queued behind another gets NO packProgress
                    // event until its turn comes, so the JS side can tell "queued,
                    // hasn't started" (no event yet) apart from "started, 0 bytes so
                    // far" (this event) — otherwise both looked identical as
                    // "Downloading… 0%", which read as frozen while queued.
                    val started = JSObject()
                    started.put("model", model); started.put("downloaded", 0L)
                    started.put("total", info.approxBytes); started.put("pct", 0)
                    notifyListeners("packProgress", started)
                    tmp.deleteRecursively(); tmp.mkdirs()
                    conn = (URL(info.url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                        connect()
                    }
                    if (conn.responseCode !in 200..299)
                        throw IOException("download failed: HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong.let { if (it > 0) it else info.approxBytes }
                    var downloaded = 0L
                    var lastEmit = 0L
                    val progressIn = ProgressInputStream(
                        conn.inputStream,
                        // Checked on EVERY buffer read, which is what actually
                        // makes Cancel responsive. The per-tar-entry check below
                        // is far too coarse on its own: model.onnx is a single
                        // ~70 MB entry, so a cancel during it wasn't noticed
                        // until the whole archive had finished streaming — and
                        // because downloads share one queue, every pack behind
                        // it stayed stuck on "Queued…" for minutes
                        // (owner-reported).
                        isCancelled = { stamp != currentDownloadEpoch(model) }
                    ) { n ->
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 200) {
                            lastEmit = now
                            val pct = if (total > 0) minOf(99, (downloaded * 100 / total).toInt()) else 0
                            val p = JSObject()
                            p.put("model", model); p.put("downloaded", downloaded); p.put("total", total)
                            p.put("pct", pct)
                            notifyListeners("packProgress", p)
                            PackDownloadService.progress(context, model, pct)
                        }
                    }
                    BZip2CompressorInputStream(progressIn).use { bz ->
                        TarArchiveInputStream(bz).use { tar ->
                            var entry = tar.nextTarEntry
                            while (entry != null) {
                                if (stamp != currentDownloadEpoch(model)) throw InterruptedIOException("cancelled")
                                // The archive wraps everything in one top-level dir
                                // (e.g. "vits-piper-en_GB-vctk-medium/model.onnx") —
                                // strip it so files land straight under `dest`,
                                // matching where ensureReady() expects them.
                                val rel = entry.name.substringAfter('/', "")
                                if (!entry.isDirectory && rel.isNotEmpty()) {
                                    val out = File(tmp, rel)
                                    out.parentFile?.mkdirs()
                                    out.outputStream().use { os -> tar.copyTo(os) }
                                }
                                entry = tar.nextTarEntry
                            }
                        }
                    }
                    if (stamp != currentDownloadEpoch(model)) throw InterruptedIOException("cancelled")
                    synchronized(lock) {
                        // Drop a loaded instance of the model we just replaced —
                        // ensureReady() will re-copy/reload from the fresh files.
                        if (loadedModel == model) { tts = null; loadedModel = null }
                        dest.deleteRecursively()
                        if (!tmp.renameTo(dest)) throw IOException("could not install pack")
                    }
                    File(dest, ".ready-${modelVersion(model)}").createNewFile()
                    val p = JSObject(); p.put("model", model); p.put("downloaded", downloaded); p.put("total", downloaded); p.put("pct", 100)
                    notifyListeners("packProgress", p)
                    call.resolve()
                } catch (e: Throwable) {
                    tmp.deleteRecursively()
                    call.reject(e.message ?: "download failed", e as? Exception ?: RuntimeException(e))
                } finally {
                    conn?.disconnect()
                    if (serviceStarted) PackDownloadService.finish(context)
                }
            }
        }

        /** cancelDownload({model}) — abort an in-flight downloadPack() call for
         *  ONE model (e.g. the user taps Cancel on that pack). Scoped to the
         *  given model only — it must NOT affect any other model's in-flight
         *  download. The download loop notices on its next chunk/progress
         *  check and rejects; any partial scratch dir is cleaned up there. */
        @PluginMethod
        fun cancelDownload(call: PluginCall) {
            val model = call.getString("model")
            if (model.isNullOrEmpty()) { call.reject("model required"); return }
            bumpDownloadEpoch(model)
            call.resolve()
        }

        /** deletePack({model}) — reclaim the space a pack uses. Every model is a
         *  real download now (including "us"), so there is exactly ONE copy on
         *  disk and deleting it frees the full ~65-80 MB. No special cases, and
         *  no "removed" sentinel needed: ensureReady() has nothing to fall back
         *  to, so a removed pack simply stays removed until downloadPack() runs
         *  again. */
        @PluginMethod
        fun deletePack(call: PluginCall) {
            val model = call.getString("model")
            if (model.isNullOrEmpty()) { call.reject("model required"); return }
            if (!VOICE_PACKS.containsKey(model)) { call.reject("not a removable pack: $model"); return }
            synchronized(lock) {
                if (loadedModel == model) { tts = null; loadedModel = null }
            }
            File(context.filesDir, folderFor(model)).deleteRecursively()
            call.resolve()
        }

        // Thin InputStream wrapper that (a) reports bytes read as they're
        // consumed, driving progress events without buffering the whole
        // response in memory, and (b) aborts the read as soon as the download
        // is cancelled.
        //
        // (b) is the load-bearing half: cancellation used to be checked only
        // between tar ENTRIES, and one entry (model.onnx) is most of the
        // archive, so a cancel during it did nothing until the entire download
        // had finished — blocking every queued pack behind it on the shared
        // single-thread executor. Throwing from here stops within one buffer
        // read (a few KB) instead.
        private class ProgressInputStream(
            private val wrapped: InputStream,
            private val isCancelled: () -> Boolean,
            private val onBytes: (Int) -> Unit,
        ) : InputStream() {
            private fun abortIfCancelled() {
                if (isCancelled()) throw InterruptedIOException("cancelled")
            }
            override fun read(): Int {
                abortIfCancelled()
                val b = wrapped.read()
                if (b >= 0) onBytes(1)
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                abortIfCancelled()
                val n = wrapped.read(b, off, len)
                if (n > 0) onBytes(n)
                return n
            }
            override fun close() = wrapped.close()
        }

        /** Start the media-playback foreground service so the WebView's <audio>
         *  chain keeps running with the screen off / app backgrounded — and,
         *  once it's running, update its notification/MediaSession state.
         *  startPlaybackService({ title, text, playing }) */
        @PluginMethod
        fun startPlaybackService(call: PluginCall) {
            try {
                // CRITICAL: only COLD-START the foreground service while the app
                // is genuinely in the foreground. On Android 12+ starting a
                // mediaPlayback FGS from a non-foreground state is disallowed —
                // startForeground() then throws and Android force-crashes us with
                // ForegroundServiceDidNotStartInTimeException (observed on device
                // when pressing play right as the app resumes from the lock
                // screen, mid keyguard transition). Skipping the start there
                // costs only background playback for that press; foreground
                // reading is unaffected and the app stays up.
                //
                // But once the service IS running this same call is just an
                // UPDATE (new chapter text, or play/pause flipping the
                // notification's button) — and those legitimately originate
                // while backgrounded: pressing pause on the lock screen is
                // exactly that case. Gating updates on appInForeground() dropped
                // them silently, so the notification kept showing "Pause" and the
                // session kept saying STATE_PLAYING — the system then sent
                // another PAUSE on the next press and reading could never resume
                // (owner-reported). An app with a running foreground service is
                // NOT "in the foreground" by this check either: its importance is
                // IMPORTANCE_FOREGROUND_SERVICE (125), above IMPORTANCE_FOREGROUND
                // (100), so the guard rejected precisely the case we need.
                // startService() to an already-running service is allowed from the
                // background (Android counts an app with an active FGS as
                // foreground for the background-start restriction), and the outer
                // catch turns any surprise into a reject rather than a crash.
                if (!appInForeground() && !PlaybackService.isRunning) {
                    Log.w("PhonoLeafPlayback", "not foreground and service not running — skipping FGS start to avoid crash")
                    call.resolve()
                    return
                }
                val i = Intent(context, PlaybackService::class.java)
                i.putExtra(PlaybackService.EXTRA_TITLE, call.getString("title") ?: "PhonoLeaf")
                i.putExtra(PlaybackService.EXTRA_TEXT, call.getString("text") ?: "Reading aloud")
                // Play vs pause. MUST be forwarded — the service defaults this to
                // true, so omitting it (as this method originally did) made every
                // pause look like a play: notification stuck on a "Pause" button
                // with nothing to resume from.
                i.putExtra(PlaybackService.EXTRA_PLAYING, call.getBoolean("playing", true) ?: true)
                i.putExtra(PlaybackService.EXTRA_PAGE, call.getString("page") ?: "")
                // Book cover -> artwork behind the lock-screen controls. Sent by
                // JS only when the book changes (it's ~50 KB of base64 even after
                // downscaling), and handed to the service through a static rather
                // than the Intent — extras are parceled through binder even for a
                // same-process service, and a bitmap would exceed the ~1 MB
                // transaction limit. Failure is cosmetic: keep the old artwork.
                val cover = call.getString("coverB64")
                if (!cover.isNullOrEmpty()) {
                    try {
                        val bytes = android.util.Base64.decode(cover, android.util.Base64.DEFAULT)
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) PlaybackService.setArtwork(bmp)
                    } catch (e: Throwable) {
                        Log.w("PhonoLeafPlayback", "cover decode failed: ${e.message}")
                    }
                }
                // Use startService, NOT startForegroundService. We're either
                // foreground or updating an already-running FGS (guarded above),
                // so startService is allowed — and crucially
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
                    val f = writeWavFile(model, audio.samples, audio.sampleRate)
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
    
        // Write a mono 16-bit PCM WAV into cacheDir and return the file. Filenames
        // are reused round-robin (RING slots) so the cache never grows unbounded;
        // with prefetch only ~2 files are live, so a slot is free long before reuse.
        private fun writeWavFile(model: String, samples: FloatArray, sampleRate: Int): File {
            val dir = File(context.cacheDir, "tts").apply { mkdirs() }
            val f = File(dir, "s${fileCounter++ % RING}.wav")
            BufferedOutputStream(FileOutputStream(f)).use { out ->
                writeWav(model, out, samples, sampleRate)
            }
            return f
        }

        // Owner-reported 2026-08-20, then AGAIN 2026-08-21 ("only lasts about one
        // sentence then goes back") after the first fix (a lower gain cap) only
        // reduced the symptom instead of removing its cause. The cause was always
        // that gain was recomputed PER CLIP (one clip = one sentence/paragraph):
        // real speech has genuine dynamic range, so a quiet or trailing-off
        // sentence has a much lower natural peak than its neighbors, and
        // correcting each clip independently to the same target erases that
        // range — exactly matching "one sentence, then back to normal." Fixed
        // for real this time by calibrating gain ONCE per model per session
        // (average peak of the first few clips), then reusing that fixed value
        // for every later clip from that model. This preserves the model's own
        // natural sentence-to-sentence dynamics (a quiet line stays quieter than
        // an emphatic one, as synthesized) while still correcting the real
        // cross-model gap this existed for (vctk quieter than libritts) — once,
        // at calibration time, not every sentence. Only the first few clips of
        // a session (or after switching models) can still show the old
        // per-clip behavior, during the brief calibration window itself. Not
        // device-verified — no JDK/Android SDK in this environment.
        private fun resolveGain(model: String, peak: Float): Float {
            val c = gainCalib.getOrPut(model) { GainCalib() }
            c.locked?.let { return it }
            if (peak <= 0.001f) return 1f
            c.count++
            c.peakSum += peak
            val provisional = minOf(2f, 0.95f / peak)
            if (c.count >= GAIN_CALIB_CLIPS) {
                val g = minOf(2f, 0.95f / (c.peakSum / c.count))
                c.locked = g
                return g
            }
            return provisional
        }

        private fun writeWav(model: String, out: OutputStream, samples: FloatArray, sampleRate: Int) {
            val n = samples.size
            var peak = 0f
            for (s in samples) { val a = if (s < 0f) -s else s; if (a > peak) peak = a }
            val gain = resolveGain(model, peak)
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
