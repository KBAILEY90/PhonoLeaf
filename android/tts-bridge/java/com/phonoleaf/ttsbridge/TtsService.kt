/*
 * Copyright (C) 2026 Kevin Bailey
 *
 * This file is part of the PhonoLeaf speech engine bridge: a small, standalone
 * component that loads an on-device speech synthesis engine and turns text
 * into audio. It runs in its own operating-system process and is reached only
 * over an inter-process interface, so no calling application links it.
 *
 * It is licensed GPL-3.0 because it links espeak-ng (via sherpa-onnx), which
 * is GPL-3.0. It depends on nothing but the Android framework, the Java
 * standard library and that engine, and could be reused by any other
 * application unchanged. See ENGINE_NOTICE.md in this directory.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.phonoleaf.ttsbridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The speech engine, isolated in its own process.
 *
 * WHY THIS EXISTS, since it looks like pointless indirection otherwise:
 * espeak-ng is GPL-3.0 and is statically linked into the sherpa-onnx native
 * library. Everything in THIS file is therefore kept on the far side of a
 * process boundary, talking to PhonoLeaf only through ITtsService: primitives
 * and strings in, raw audio written to a path the caller chose. PhonoLeaf's own
 * code never links against sherpa and never shares memory with it.
 *
 * This file is intended to be published as source. Keep it that way: put
 * nothing here that is not strictly needed to drive sherpa. In particular the
 * loudness calibration and WAV muxing deliberately live on the app side, since
 * they are ours and are not derived from anything here.
 *
 * Declared with android:process=":tts" in AndroidManifest.xml. Android may kill
 * this process when memory is tight, which is normal and safe: the next
 * synthesize() simply reloads the model, costing a fraction of a second.
 */
class TtsService : Service() {

    private val lock = Any()

    @Volatile private var tts: OfflineTts? = null
    @Volatile private var loaded: String? = null
    @Volatile private var loadedType: String = "?"

    /**
     * Newest cancel stamp seen. A synthesize() carrying an older stamp is
     * abandoned rather than returned: by the time it finished, the reader had
     * already moved on, and playing it would speak a page the user has left.
     */
    @Volatile private var cancelStamp = -1

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        synchronized(lock) { tts = null; loaded = null }
        super.onDestroy()
    }

    private val binder = object : ITtsService.Stub() {

        override fun synthesize(
            text: String?,
            sid: Int,
            speed: Float,
            model: String?,
            outPath: String?,
            stamp: Int,
        ): String {
            if (text.isNullOrBlank()) return "err:no text"
            if (model.isNullOrBlank()) return "err:no model"
            if (outPath.isNullOrBlank()) return "err:no outPath"
            if (stamp <= cancelStamp) return "err:cancelled"
            return try {
                // Serialized: one generation at a time, matching the single
                // thread the in-process engine used. Two concurrent generations
                // would contend for the same CPU budget and make both late.
                synchronized(lock) {
                    if (stamp <= cancelStamp) return "err:cancelled"
                    val engine = ensureReady(model)
                    val t0 = System.currentTimeMillis()
                    val audio = engine.generate(text, sid, speed)
                    val genMs = System.currentTimeMillis() - t0
                    // Checked again AFTER generating: a cancel that arrived while
                    // we were busy still counts, and this is the window that
                    // matters most because generation is the slow part.
                    if (stamp <= cancelStamp) return "err:cancelled"
                    writeRaw(File(outPath), audio.samples)
                    val durMs = audio.samples.size.toLong() * 1000 /
                        maxOf(1, audio.sampleRate)
                    Log.i(TAG, "gen=${genMs}ms audio=${durMs}ms chars=${text.length} model=$model")
                    "ok:${audio.sampleRate}:${audio.samples.size}:$loadedType"
                }
            } catch (e: EnginePackMissingException) {
                // Distinguishable so the app can offer the download instead of
                // treating a missing pack as an engine failure.
                "err:notdownloaded:${e.model}"
            } catch (t: Throwable) {
                "err:${t.javaClass.simpleName}: ${t.message ?: "no message"}"
            }
        }

        override fun cancel(stamp: Int) {
            if (stamp > cancelStamp) cancelStamp = stamp
        }

        override fun prepare(model: String?): String {
            if (model.isNullOrBlank()) return "err:no model"
            return try {
                synchronized(lock) { ensureReady(model) }
                "ok:$loadedType"
            } catch (e: EnginePackMissingException) {
                "err:notdownloaded:${e.model}"
            } catch (t: Throwable) {
                "err:${t.javaClass.simpleName}: ${t.message ?: "no message"}"
            }
        }

        override fun dropModel(model: String?) {
            synchronized(lock) {
                if (model != null && loaded == model) { tts = null; loaded = null; loadedType = "?" }
            }
        }

        override fun loadedModel(): String = loaded ?: ""
    }

    /** Raw 32-bit float, native order, mono. The app applies gain and muxes. */
    private fun writeRaw(f: File, samples: FloatArray) {
        f.parentFile?.mkdirs()
        val bb = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.nativeOrder())
        for (s in samples) bb.putFloat(s)
        BufferedOutputStream(FileOutputStream(f)).use { it.write(bb.array()) }
    }

    // ---------------------------------------------------------------------
    // Model loading. This is the ONLY route to the engine: the calling
    // application has no model-loading path of its own and never links the
    // engine library. (It briefly had a parallel copy while the two were being
    // compared; that was deleted once this became the only route.)
    // ---------------------------------------------------------------------
    private fun ensureReady(model: String): OfflineTts {
        tts?.let { if (loaded == model) return it }
        // Switching models drops the previous instance first, so only one is
        // ever resident and peak memory stays at one model.
        tts = null; loaded = null

        val dest = File(filesDir, folderFor(model))
        if (!File(dest, ".ready-${modelVersion(model)}").exists()) {
            throw EnginePackMissingException(model)
        }
        val base = dest.absolutePath
        // Only set optional paths that actually exist: sherpa rejects paths
        // pointing at nothing, and the English Kokoro model ships espeak-ng-data
        // but no dict/ or lexicon (those belong to the Chinese multi-lang ones).
        fun ifExists(rel: String) = if (File(dest, rel).exists()) "$base/$rel" else ""

        // Pointing at a missing .onnx crashes the native loader HARD, taking the
        // process with it rather than throwing. Resolve a real filename first.
        val modelFile = when {
            File(dest, "model.onnx").exists() -> "model.onnx"
            File(dest, "model.int8.onnx").exists() -> "model.int8.onnx"
            else -> dest.listFiles { f -> f.name.endsWith(".onnx") }?.firstOrNull()?.name
                ?: throw java.io.FileNotFoundException("No *.onnx in $base")
        }
        val lexicon = listOf("lexicon-us-en.txt", "lexicon-gb-en.txt", "lexicon-zh.txt")
            .map { File(dest, it) }.filter { it.exists() }
            .joinToString(",") { it.absolutePath }
        val threads = inferenceThreads()

        // voices.bin present means Kokoro (separate speaker embeddings);
        // otherwise VITS/Piper.
        val hasVoices = File(dest, "voices.bin").exists()
        loadedType = if (hasVoices) "kokoro" else "vits"
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
                    dataDir = ifExists("espeak-ng-data"),
                ),
                numThreads = threads,
                provider = "cpu",
            )
        }
        Log.i(TAG, "init model=$model file=$modelFile type=${if (hasVoices) "kokoro" else "vits"} threads=$threads")
        val t = OfflineTts(assetManager = null, config = OfflineTtsConfig(model = modelCfg))
        tts = t
        loaded = model
        return t
    }

    // Up to 4 threads: fills a modern phone's fast cores without spilling onto
    // the efficiency cores, which drag the whole generation down.
    private fun inferenceThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return maxOf(2, minOf(4, cores - 4))
    }

    private fun folderFor(model: String) = VOICE_FOLDERS[model] ?: "kokoro"
    private fun modelVersion(model: String) = MODEL_VERSIONS[model] ?: "piper-libritts-r-medium"

    companion object {
        private const val TAG = "PhonoLeafTtsSvc"

        // Folder names must match whatever the CALLING APPLICATION used when it
        // downloaded the packs. The caller owns downloading entirely; this side
        // only reads what is already on disk, so it needs the names and nothing
        // else. A different caller can use its own layout by changing this map.
        private val VOICE_FOLDERS = mapOf(
            "us" to "kokoro",
            "gb" to "kokoro-gb",
            "fr" to "kokoro-fr",
            "de" to "kokoro-de",
            "kokoro" to "kokoro-en",
        )
        private val MODEL_VERSIONS = mapOf(
            "us" to "piper-libritts-r-medium",
            "gb" to "piper-libritts-r-medium",
            "fr" to "piper-fr-upmc-medium-2spk",
            "de" to "piper-libritts-r-medium",
            "kokoro" to "kokoro-int8-en-v0_19",
        )
    }
}

/** Thrown when the model folder has no readiness marker yet. Named apart from
 * the plugin's own private nested equivalent so the two are never confused. */
class EnginePackMissingException(val model: String) :
    RuntimeException("pack not downloaded: $model")
