package com.phonoleaf.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Debug
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.ceil
import kotlin.random.Random

/**
 * THROWAWAY SPIKE, added 2026-08-31. Not part of the shipping voice path.
 *
 * Answers three questions about Supertonic that cannot be answered from a
 * desktop, in this order of importance:
 *   1. Does a 380 MB fp32 model load and run on a real phone without the OS
 *      killing us? (memory is the suspected blocker, ahead of quality)
 *   2. How fast is it, as a real-time factor against Piper?
 *   3. Does it actually sound as good on-device as the published samples?
 *
 * Why this exists at all: espeak-ng is GPL-3.0 and is statically linked into
 * the sherpa-onnx AAR we ship today, which is incompatible with a closed
 * source app. Supertonic uses no phonemizer whatsoever (text maps through a
 * Unicode index table), so it removes that entire problem class rather than
 * swapping one dependency for another. See TODO.md's voice model licence
 * section.
 *
 * The inference contract below (tensor names, shapes, the latent-length
 * formula, the flow-matching loop) was derived from the MIT-licensed
 * reference implementations, principally nedmah/supertonic-kmp. This is our
 * own implementation of that contract, not a copy of it.
 *
 * DELETE THIS FILE once the engine decision is made, either way.
 */
object SupertonicSpike {

    private const val SAMPLE_RATE = 44100
    // Each latent frame covers this many audio samples. Together with the
    // predicted duration this is what sets the latent sequence length.
    private const val CHUNK_SIZE = 3072
    private const val LATENT_CHANNELS = 144
    private const val STYLE_TTL_TOKENS = 50
    private const val STYLE_TTL_DIM = 256
    private const val STYLE_DP_TOKENS = 8
    private const val STYLE_DP_DIM = 16

    private const val HF = "https://huggingface.co/Supertone/supertonic-3/resolve/main"
    private val FILES = listOf(
        "onnx/duration_predictor.onnx",
        "onnx/text_encoder.onnx",
        "onnx/vector_estimator.onnx",
        "onnx/vocoder.onnx",
        "onnx/unicode_indexer.json",
        "voice_styles/F1.json",
    )

    fun dir(ctx: Context): File = File(ctx.filesDir, "supertonic-spike")

    /** Bytes already on disk, so the UI can say whether a download is needed. */
    fun downloadedBytes(ctx: Context): Long =
        FILES.sumOf { rel ->
            val f = File(dir(ctx), rel.substringAfterLast('/'))
            if (f.exists()) f.length() else 0L
        }

    fun isDownloaded(ctx: Context): Boolean =
        FILES.all { File(dir(ctx), it.substringAfterLast('/')).exists() }

    /**
     * Streams the six files into filesDir. ~380 MB, so this is deliberately
     * resumable-by-restart (a partial file is deleted and re-fetched) rather
     * than clever: it runs once, on wifi, during a test.
     */
    fun download(ctx: Context, onProgress: (String, Int) -> Unit) {
        val out = dir(ctx)
        if (!out.exists()) out.mkdirs()
        for (rel in FILES) {
            val name = rel.substringAfterLast('/')
            val dest = File(out, name)
            if (dest.exists() && dest.length() > 0) { onProgress(name, 100); continue }
            val tmp = File(out, "$name.part")
            if (tmp.exists()) tmp.delete()
            val conn = (URL("$HF/$rel").openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                val total = conn.contentLengthLong
                FileOutputStream(tmp).use { fos ->
                    val buf = ByteArray(1 shl 16)
                    var got = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        got += n
                        if (total > 0) {
                            val pct = ((got * 100) / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(name, pct) }
                        }
                    }
                }
            }
            conn.disconnect()
            if (!tmp.renameTo(dest)) throw IllegalStateException("could not finalize $name")
        }
    }

    /** Flat table: index is the Unicode codepoint, value is the token id. */
    private fun loadIndexer(f: File): IntArray {
        val arr = JSONArray(f.readText())
        return IntArray(arr.length()) { arr.getInt(it) }
    }

    /**
     * Codepoint lookup, dropping anything unmapped (-1) or outside the BMP.
     * No normalisation and no special tokens: that is the whole tokenizer,
     * which is precisely why there is no GPL phonemizer in this path.
     */
    private fun tokenize(text: String, indexer: IntArray): LongArray {
        val ids = ArrayList<Long>(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp < indexer.size) {
                val id = indexer[cp]
                if (id >= 0) ids.add(id.toLong())
            }
        }
        return ids.toLongArray()
    }

    private fun styleTensor(obj: JSONObject, key: String, tokens: Int, dim: Int): FloatArray {
        val data = obj.getJSONObject(key).getJSONArray("data")
        // stored as [1][tokens][dim]
        val outer = data.getJSONArray(0)
        val out = FloatArray(tokens * dim)
        for (t in 0 until tokens) {
            val row = outer.getJSONArray(t)
            for (d in 0 until dim) out[t * dim + d] = row.getDouble(d).toFloat()
        }
        return out
    }

    private fun tensor(env: OrtEnvironment, data: FloatArray, vararg shape: Long): OnnxTensor =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    private fun tensor(env: OrtEnvironment, data: LongArray, vararg shape: Long): OnnxTensor =
        OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)

    @Suppress("UNCHECKED_CAST")
    private fun firstFloatArray(r: OrtSession.Result): Any = r.get(0).value

    data class Report(
        val ok: Boolean,
        val message: String,
        val loadMs: Long = 0,
        val synthMs: Long = 0,
        val audioMs: Long = 0,
        val rtf: Double = 0.0,
        val peakNativeHeapMb: Long = 0,
        val peak: Float = 0f,
        val rms: Float = 0f,
        val javaHeapMb: Long = 0,
        val wavPath: String = "",
    )

    /**
     * Loads all four sessions, synthesizes one sentence, writes a wav, and
     * reports timings plus memory. Peak native heap is the number that
     * matters: ONNX Runtime allocates weights natively, so the Java heap
     * barely moves even when we are about to be killed.
     */
    fun run(ctx: Context, text: String, steps: Int = 8, normalize: Boolean = false): Report {
        val d = dir(ctx)
        if (!isDownloaded(ctx)) return Report(false, "Models not downloaded yet")

        var env: OrtEnvironment? = null
        val sessions = ArrayList<OrtSession>(4)
        try {
            val baselineNative = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            val t0 = System.currentTimeMillis()

            env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(maxOf(1, Runtime.getRuntime().availableProcessors() / 2))
            }
            val dp = env.createSession(File(d, "duration_predictor.onnx").absolutePath, opts)
            val te = env.createSession(File(d, "text_encoder.onnx").absolutePath, opts)
            val ve = env.createSession(File(d, "vector_estimator.onnx").absolutePath, opts)
            val vo = env.createSession(File(d, "vocoder.onnx").absolutePath, opts)
            sessions.addAll(listOf(dp, te, ve, vo))
            val loadMs = System.currentTimeMillis() - t0

            val indexer = loadIndexer(File(d, "unicode_indexer.json"))
            val style = JSONObject(File(d, "F1.json").readText())
            val styleTtl = styleTensor(style, "style_ttl", STYLE_TTL_TOKENS, STYLE_TTL_DIM)
            val styleDp = styleTensor(style, "style_dp", STYLE_DP_TOKENS, STYLE_DP_DIM)

            val ids = tokenize(text, indexer)
            if (ids.isEmpty()) return Report(false, "Tokenizer produced no ids for that text")
            val tLen = ids.size.toLong()

            val t1 = System.currentTimeMillis()

            val textMask = FloatArray(ids.size) { 1f }
            val idsT = tensor(env, ids, 1, tLen)
            val maskT = tensor(env, textMask, 1, 1, tLen)
            val ttlT = tensor(env, styleTtl, 1, STYLE_TTL_TOKENS.toLong(), STYLE_TTL_DIM.toLong())
            val dpT = tensor(env, styleDp, 1, STYLE_DP_TOKENS.toLong(), STYLE_DP_DIM.toLong())

            // Stage 1: how long should this utterance be?
            val durOut = dp.run(mapOf("text_ids" to idsT, "style_dp" to dpT, "text_mask" to maskT))
            val durationSec = flatten(firstFloatArray(durOut))[0]
            durOut.close()

            // Stage 2: text embedding
            val encOut = te.run(mapOf("text_ids" to idsT, "style_ttl" to ttlT, "text_mask" to maskT))
            val textEmbTensor = encOut.get(0) as OnnxTensor
            val embShape = textEmbTensor.info.shape
            val textEmb = flatten(textEmbTensor.value)

            val latentLen = ceil(durationSec * SAMPLE_RATE / CHUNK_SIZE).toInt().coerceAtLeast(1)
            val noiseSize = LATENT_CHANNELS * latentLen
            val rng = Random(0)          // fixed seed so repeat runs are comparable
            var latent = FloatArray(noiseSize) { gaussian(rng) }
            val latentMask = FloatArray(latentLen) { 1f }

            // Stage 3: flow-matching denoise loop
            val embT = tensor(env, textEmb, *embShape)
            val latMaskT = tensor(env, latentMask, 1, 1, latentLen.toLong())
            val totalT = tensor(env, floatArrayOf(steps.toFloat()), 1)
            for (s in 0 until steps) {
                val noisyT = tensor(env, latent, 1, LATENT_CHANNELS.toLong(), latentLen.toLong())
                val curT = tensor(env, floatArrayOf(s.toFloat()), 1)
                val r = ve.run(mapOf(
                    "noisy_latent" to noisyT,
                    "text_emb" to embT,
                    "style_ttl" to ttlT,
                    "latent_mask" to latMaskT,
                    "text_mask" to maskT,
                    "current_step" to curT,
                    "total_step" to totalT,
                ))
                latent = flatten(firstFloatArray(r))
                r.close(); noisyT.close(); curT.close()
            }

            // Stage 4: latent -> waveform
            val latT = tensor(env, latent, 1, LATENT_CHANNELS.toLong(), latentLen.toLong())
            val vocOut = vo.run(mapOf("latent" to latT))
            val wav = flatten(firstFloatArray(vocOut))
            vocOut.close(); latT.close()

            val synthMs = System.currentTimeMillis() - t1
            val peakNative = (Debug.getNativeHeapAllocatedSize() / (1024 * 1024)) - baselineNative
            val javaHeap = (Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) / (1024 * 1024)

            // Measure BEFORE anything touches the samples. A peak well above
            // 1.0 means writeWav16 was clamping the waveform into distortion,
            // which sounds like a broken voice rather than a bad one.
            var peak = 0f
            var sumSq = 0.0
            for (v in wav) {
                val a = kotlin.math.abs(v)
                if (a > peak) peak = a
                sumSq += (v * v).toDouble()
            }
            val rms = if (wav.isEmpty()) 0f else kotlin.math.sqrt(sumSq / wav.size).toFloat()
            if (normalize && peak > 0f) {
                val g = 0.95f / peak
                for (i in wav.indices) wav[i] = wav[i] * g
            }

            val outFile = File(ctx.cacheDir, "supertonic-spike.wav")
            writeWav16(outFile, wav)

            val audioMs = (wav.size * 1000L) / SAMPLE_RATE
            encOut.close(); idsT.close(); maskT.close(); ttlT.close(); dpT.close()
            embT.close(); latMaskT.close(); totalT.close()

            return Report(
                ok = true,
                message = "OK: ${ids.size} tokens, predicted ${"%.2f".format(durationSec)}s, $latentLen latent frames",
                loadMs = loadMs,
                synthMs = synthMs,
                audioMs = audioMs,
                rtf = if (audioMs > 0) synthMs.toDouble() / audioMs.toDouble() else 0.0,
                peakNativeHeapMb = peakNative,
                peak = peak,
                rms = rms,
                javaHeapMb = javaHeap,
                wavPath = outFile.absolutePath,
            )
        } catch (t: Throwable) {
            // OutOfMemoryError is a plausible outcome here and is a RESULT, not
            // a crash to hide: report it rather than letting it kill the app.
            return Report(false, "${t.javaClass.simpleName}: ${t.message ?: "no message"}")
        } finally {
            sessions.forEach { runCatching { it.close() } }
        }
    }

    /** ONNX returns nested arrays; we only ever want the flat float payload. */
    private fun flatten(v: Any?): FloatArray {
        val out = ArrayList<Float>()
        fun walk(x: Any?) {
            when (x) {
                is FloatArray -> x.forEach { out.add(it) }
                is Array<*> -> x.forEach { walk(it) }
                is Float -> out.add(x)
                else -> {}
            }
        }
        walk(v)
        return FloatArray(out.size) { out[it] }
    }

    private fun gaussian(rng: Random): Float {
        // Box-Muller; kotlin.random has no nextGaussian
        val u1 = rng.nextDouble().coerceAtLeast(1e-12)
        val u2 = rng.nextDouble()
        return (Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2)).toFloat()
    }

    private fun writeWav16(f: File, samples: FloatArray) {
        val dataBytes = samples.size * 2
        val bb = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + dataBytes)
        bb.put("WAVE".toByteArray()); bb.put("fmt ".toByteArray())
        bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(SAMPLE_RATE); bb.putInt(SAMPLE_RATE * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(dataBytes)
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            bb.putShort(v)
        }
        FileOutputStream(f).use { it.write(bb.array()) }
    }
}
