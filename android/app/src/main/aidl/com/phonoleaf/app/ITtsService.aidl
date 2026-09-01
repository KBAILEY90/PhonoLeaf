package com.phonoleaf.app;

/**
 * The process boundary between PhonoLeaf and the GPL-linked speech engine.
 *
 * Deliberately as arms-length and generic as it can be: primitives and strings
 * in, raw audio written to a path the CALLER chose, a short status string back.
 * No shared memory, no callbacks into the app, no custom Parcelables, nothing
 * that reaches into either side's internals. Text goes in, audio comes out.
 *
 * That shape is not an accident. espeak-ng is GPL-3.0 and is statically linked
 * into the sherpa-onnx library, so the engine runs in its own process
 * (android:process=":tts") and talks only through this interface. See TODO.md's
 * voice model licence section.
 *
 * Audio is written to a FILE rather than returned across the boundary: Binder
 * transactions cap at roughly 1 MB and a sentence of 44.1 kHz audio is larger
 * than that. The caller passes the destination, so the engine never decides
 * where anything lives.
 */
interface ITtsService {

    /**
     * Synthesize `text` and write raw 32-bit float samples, native byte order,
     * mono, to `outPath`.
     *
     * Returns "ok:<sampleRate>:<sampleCount>" on success, or "err:<message>".
     * A plain string rather than an exception or a typed result, so the
     * protocol stays trivial and version-tolerant.
     *
     * `stamp` is the caller's generation counter, echoed back to it. The engine
     * does not interpret it beyond comparing against the last cancel(), which
     * lets a result that was already obsolete when it finished be discarded on
     * arrival instead of played over a page the reader has left.
     */
    String synthesize(String text, int sid, float speed, String model, String outPath, int stamp);

    /**
     * Abandon anything in flight. Any synthesize() whose stamp predates this
     * call returns "err:cancelled" rather than audio.
     */
    void cancel(int stamp);

    /**
     * Load `model` without synthesizing, so the first sentence of a book does
     * not pay the load. Returns "ok:<modelType>" where modelType is "kokoro"
     * or "vits", or "err:<message>".
     */
    String prepare(String model);

    /**
     * Forget `model` if it is the resident one. Called after a pack is
     * re-downloaded or deleted, so the next synthesize picks up the new files
     * instead of serving a model whose bytes have changed underneath it.
     */
    void dropModel(String model);

    /** Which model is currently resident, or "" if none. Diagnostics only. */
    String loadedModel();
}
