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
package com.phonoleaf.ttsbridge;

/**
 * The process boundary between a calling application and the GPL-linked speech engine.
 *
 * Deliberately as arms-length and generic as it can be: primitives and strings
 * in, raw audio written to a path the CALLER chose, a short status string back.
 * No shared memory, no callbacks into the app, no custom Parcelables, nothing
 * that reaches into either side's internals. Text goes in, audio comes out.
 *
 * That shape is not an accident. espeak-ng is GPL-3.0 and is statically linked
 * into the sherpa-onnx library, so the engine runs in its own process
 * (android:process=":tts") and talks only through this interface. See
 * ENGINE_NOTICE.md in this directory.
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

    /**
     * Release everything and terminate this process.
     *
     * Deliberately blunt, and deliberately part of the interface rather than an
     * implementation detail: the phonemizer this engine uses keeps state for the
     * whole PROCESS, initialised once from the first model's data directory and
     * not revisited. If a caller deletes that directory (for instance when it
     * replaces a voice pack), no per-instance cleanup can undo that, and every
     * later synthesis produces near-empty audio.
     *
     * Ending the process is the only reliable reset. It is safe by design: the
     * caller simply rebinds and the next request reloads its model.
     */
    void shutdown();

    /** Which model is currently resident, or "" if none. Diagnostics only. */
    String loadedModel();
}
