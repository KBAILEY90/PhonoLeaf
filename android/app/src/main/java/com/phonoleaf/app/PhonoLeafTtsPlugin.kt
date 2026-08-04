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
     * Model files: the owner drops the extracted Kokoro model into
     *   android/app/src/main/assets/kokoro/   (gitignored — see TESTING.md).
     * On first use we copy that folder to filesDir once (espeak-ng-data / dict /
     * lexicon files must be opened by native code via real filesystem paths, not
     * through the AssetManager), then load from disk.
     *
     * Not every model is bundled this way. Accents beyond the store build's US
     * default (currently "gb") ship nothing in assets at all — see VOICE_PACKS —
     * and are instead fetched straight into filesDir on demand via
     * downloadPack(), to keep the store APK to one model's size (~78 MB) rather
     * than growing per language. ensureReady() throws a distinguishable
     * PackNotDownloadedException for these until downloadPack() has run once;
     * packStatus() lets the Settings UI check/show that state up front.
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
    
        // Marks that a NON-VOICE_PACKS model (i.e. "us") was explicitly removed
        // via deletePack(), so ensureReady() knows not to silently re-copy it
        // from assets — see the "explicitly removed" check there. Only
        // reinstallPack() clears this; ordinary ensureReady()/prepare() calls
        // (including the implicit one at TTS startup) must never clear it
        // themselves, or a deliberate removal would get silently undone the
        // next time the app tries to detect the model type.
        private fun removedMarkerFile(model: String) = File(context.filesDir, ".removed-$model")

        // Asset/filesDir subfolder for a model key: looked up from VOICE_PACKS
        // for anything downloadable; "us"/anything else falls back to the
        // bundled `kokoro` folder.
        private fun folderFor(model: String) = VOICE_PACKS[model]?.folder ?: ASSET_DIR

        // Voice packs NOT bundled in the APK's assets — fetched on demand via
        // downloadPack() into filesDir instead. Keeps the store build's install
        // size to just the US model (see CLAUDE.md's "MODEL SIZES" note: bundling
        // every accent/language doesn't scale, ~65-80 MB each). Source is the
        // SAME public sherpa-onnx GitHub release the US model already ships
        // from (TESTING.md §3.6) — no separate hosting to maintain. Sizes are
        // the exact `.tar.bz2` asset sizes from that release (checked
        // 2026-08-04 via `gh release view tts-models --repo k2-fsa/sherpa-onnx`),
        // not estimates.
        private data class VoicePackInfo(val folder: String, val url: String, val approxBytes: Long)
        private val VOICE_PACKS = mapOf(
            "gb" to VoicePackInfo(
                folder = "kokoro-gb",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-vctk-medium.tar.bz2",
                approxBytes = 80488085L,
            ),
            // Single-speaker models (unlike US/GB's multi-speaker libritts_r/vctk)
            // — no speaker-id audition needed, sid is always 0. Picked as the
            // most standard/well-known community Piper voice per language
            // (siwis/thorsten/davefx), NOT owner-audited for quality or gender —
            // see the PIPER_VOICES comment in index.html.
            "fr" to VoicePackInfo(
                folder = "kokoro-fr",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fr_FR-siwis-medium.tar.bz2",
                approxBytes = 67207459L,
            ),
            "de" to VoicePackInfo(
                folder = "kokoro-de",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-de_DE-thorsten-medium.tar.bz2",
                approxBytes = 67214254L,
            ),
            "es" to VoicePackInfo(
                folder = "kokoro-es",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-davefx-medium.tar.bz2",
                approxBytes = 67184952L,
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
                val marker = File(dest, ".ready-$MODEL_VERSION")
                if (!marker.exists()) {
                    if (VOICE_PACKS.containsKey(model)) {
                        // Not shipped in assets — must go through downloadPack()
                        // first. Don't fall through to copyAssetDir: there's
                        // nothing in assets to copy, so it would just throw a
                        // less specific "no *.onnx" error below.
                        throw PackNotDownloadedException(model)
                    }
                    if (removedMarkerFile(model).exists()) {
                        // "us" (the only asset-backed, non-VOICE_PACKS model)
                        // was EXPLICITLY removed via deletePack() — do not
                        // silently re-copy it from assets here. Without this
                        // check, the very next synth() call after tapping
                        // Remove would auto-heal it straight back, making
                        // removal look broken (owner-reported: "still doesn't
                        // get removed"). It must stay gone, exactly like a
                        // real pack would, until reinstallPack() explicitly
                        // clears this marker and re-copies it.
                        throw PackNotDownloadedException(model)
                    }
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
        private val downloadExecutor = Executors.newSingleThreadExecutor()

        /** packStatus({model}) -> {downloaded, approxBytes}. "us" reports its
         *  REAL filesDir state now that it's removable (see deletePack) — it's
         *  the one model whose ensureReady() can transparently regenerate
         *  "downloaded=false" for free (re-copy from assets), so this can
         *  legitimately go false and back to true without ever downloading
         *  anything. Any other model unknown to VOICE_PACKS (shouldn't happen
         *  in practice — the JS catalog only ever asks about real entries)
         *  falls back to reporting itself as always present, harmlessly. Lets
         *  the Settings UI show pack size before download and reflect current
         *  state without duplicating a flag in JS storage — the filesystem
         *  marker is the single source of truth. */
        @PluginMethod
        fun packStatus(call: PluginCall) {
            val model = call.getString("model")
            if (model.isNullOrEmpty()) { call.reject("model required"); return }
            val info = VOICE_PACKS[model]
            val ret = JSObject()
            if (info == null && model != "us") {
                ret.put("downloaded", true)
                ret.put("approxBytes", 0)
                call.resolve(ret)
                return
            }
            val dest = File(context.filesDir, folderFor(model))
            ret.put("downloaded", File(dest, ".ready-$MODEL_VERSION").exists())
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
                // Fires the MOMENT this task actually starts running — i.e. once
                // downloadExecutor has dequeued it, not when downloadPack() was
                // called. A download queued behind another gets NO packProgress
                // event until its turn comes, so the JS side can tell "queued,
                // hasn't started" (no event yet) apart from "started, 0 bytes so
                // far" (this event) — otherwise both looked identical as
                // "Downloading… 0%", which read as frozen while queued.
                if (stamp == currentDownloadEpoch(model)) {
                    val started = JSObject()
                    started.put("model", model); started.put("downloaded", 0L)
                    started.put("total", info.approxBytes); started.put("pct", 0)
                    notifyListeners("packProgress", started)
                }
                val folder = folderFor(model)
                val dest = File(context.filesDir, folder)
                // Extract into a scratch dir first, swap in only on full success —
                // a failed/cancelled download must never leave a half-written
                // folder that ensureReady() then treats as ready.
                val tmp = File(context.filesDir, "$folder-tmp")
                var conn: HttpURLConnection? = null
                try {
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
                    val progressIn = ProgressInputStream(conn.inputStream) { n ->
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 200 && stamp == currentDownloadEpoch(model)) {
                            lastEmit = now
                            val p = JSObject()
                            p.put("model", model); p.put("downloaded", downloaded); p.put("total", total)
                            p.put("pct", if (total > 0) minOf(99, (downloaded * 100 / total).toInt()) else 0)
                            notifyListeners("packProgress", p)
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
                    File(dest, ".ready-$MODEL_VERSION").createNewFile()
                    val p = JSObject(); p.put("model", model); p.put("downloaded", downloaded); p.put("total", downloaded); p.put("pct", 100)
                    notifyListeners("packProgress", p)
                    call.resolve()
                } catch (e: Throwable) {
                    tmp.deleteRecursively()
                    call.reject(e.message ?: "download failed", e as? Exception ?: RuntimeException(e))
                } finally {
                    conn?.disconnect()
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

        /** deletePack({model}) — reclaim the space a pack uses. Valid for any
         *  model in VOICE_PACKS, PLUS "us" (owner request 2026-08-04): even
         *  though the US model ships in the APK's assets, ensureReady() keeps a
         *  SEPARATE full copy in filesDir (native code needs real filesystem
         *  paths for espeak-ng-data/etc, not an AssetManager stream) — deleting
         *  that filesDir copy genuinely frees ~78 MB of device storage, even
         *  though the asset copy baked into the APK itself can't be freed
         *  without uninstalling.
         *  For "us" this ALSO writes removedMarkerFile() — without it,
         *  ensureReady() would silently re-copy "us" from assets on the very
         *  next synth() call (it has no download to fail, so nothing would
         *  otherwise stop it from just healing itself back), which made
         *  Remove look like it did nothing (owner-reported). With the marker,
         *  "us" now stays removed exactly like a real pack would, until
         *  reinstallPack() is called. */
        @PluginMethod
        fun deletePack(call: PluginCall) {
            val model = call.getString("model")
            if (model.isNullOrEmpty()) { call.reject("model required"); return }
            if (model != "us" && !VOICE_PACKS.containsKey(model)) { call.reject("not a removable pack: $model"); return }
            synchronized(lock) {
                if (loadedModel == model) { tts = null; loadedModel = null }
            }
            File(context.filesDir, folderFor(model)).deleteRecursively()
            if (!VOICE_PACKS.containsKey(model)) removedMarkerFile(model).createNewFile()
            call.resolve()
        }

        /** reinstallPack({model}) — explicit user action to bring back an
         *  asset-backed model (currently only "us") after deletePack() removed
         *  it. Deliberately NOT the same as prepare(): prepare() is also called
         *  implicitly at TTS startup to detect the loaded model's family, and
         *  must NEVER silently undo a deliberate removal just because playback
         *  started — only this explicit call may clear removedMarkerFile(). */
        @PluginMethod
        fun reinstallPack(call: PluginCall) {
            val model = call.getString("model")
            if (model.isNullOrEmpty()) { call.reject("model required"); return }
            if (VOICE_PACKS.containsKey(model)) { call.reject("use downloadPack for network packs"); return }
            try {
                removedMarkerFile(model).delete()
                ensureReady(model)
                call.resolve()
            } catch (e: Throwable) {
                call.reject(e.message ?: "reinstall failed", e as? Exception ?: RuntimeException(e))
            }
        }

        // Thin InputStream wrapper that reports bytes read as they're consumed —
        // used to drive download progress events without buffering the whole
        // response in memory first.
        private class ProgressInputStream(
            private val wrapped: InputStream,
            private val onBytes: (Int) -> Unit,
        ) : InputStream() {
            override fun read(): Int {
                val b = wrapped.read()
                if (b >= 0) onBytes(1)
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
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
