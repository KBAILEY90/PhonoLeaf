// Kokoro TTS Web Worker — runs neural inference off the main thread.
// Loaded as a module worker: new Worker('./kokoro-worker.js', { type: 'module' })
import { KokoroTTS } from 'https://cdn.jsdelivr.net/npm/kokoro-js/+esm';

let tts = null;
let genId = 0;

self.onmessage = async ({ data }) => {
  if (data.type === 'init') {
    try {
      tts = await KokoroTTS.from_pretrained('onnx-community/Kokoro-82M-v1.0-ONNX', {
        dtype: 'q8',
        device: 'wasm',
        progress_callback: p => {
          if (p.progress != null) self.postMessage({ type: 'progress', progress: Math.round(p.progress), file: p.file || '' });
        },
      });
      self.postMessage({ type: 'ready' });
    } catch (e) {
      self.postMessage({ type: 'error', message: e.message });
    }

  } else if (data.type === 'generate') {
    if (!tts) { self.postMessage({ type: 'error', id: data.id, message: 'not ready' }); return; }
    const { id, text, voice, speed } = data;
    genId = id;
    try {
      const audio = await tts.generate(text, { voice: voice || 'af_heart', speed: +speed || 1 });
      if (genId !== id) return; // cancelled while generating
      // kokoro-js returns RawAudio { audio: Float32Array, sampling_rate: number }.
      const samples = audio.audio;
      const sampleRate = audio.sampling_rate ?? 24000;
      self.postMessage({ type: 'audio', id, samples, sampleRate }, [samples.buffer]);
    } catch (e) {
      if (genId === id) self.postMessage({ type: 'error', id, message: e.message });
    }

  } else if (data.type === 'cancel') {
    genId = -1; // any in-flight generate() will discard its result
  }
};
