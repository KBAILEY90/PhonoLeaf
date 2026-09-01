# Speech engine component: licence boundary

`TtsService.kt` and `../../../../aidl/com/phonoleaf/app/ITtsService.aidl` are
the only part of PhonoLeaf that touches the speech engine. They are published
as source deliberately, and this file exists so that is obvious rather than
something you have to infer.

## Why there is a boundary here at all

PhonoLeaf synthesizes speech with [sherpa-onnx][sherpa]. That library
statically links [espeak-ng][espeak], which is **GPL-3.0**. PhonoLeaf itself is
a closed-source commercial app, and code linked into a GPL work inherits its
terms.

So the engine is not linked into the app. It runs in its own operating-system
process (`android:process=":tts"` in `AndroidManifest.xml`) and is reached only
through the AIDL interface above. The app sends text and receives audio. It
never links the library, never shares memory with it, and would keep working if
the component were replaced.

## Rules for anyone changing this

1. **Do not call sherpa from anywhere except `TtsService.kt`.** If an import of
   `com.k2fsa.sherpa.onnx` appears anywhere else, the boundary is gone.
2. **Keep the interface generic.** Primitives and strings in, raw audio written
   to a path the *caller* chose, a short status string back. No shared memory,
   no callbacks into the app, no custom Parcelables, nothing app-aware. A rich
   or app-specific protocol would undermine the argument that these are
   separable programs.
3. **Keep this component minimal.** Only what is strictly needed to drive the
   engine. PhonoLeaf's own audio work (loudness calibration, WAV muxing) lives
   on the app side on purpose and should stay there.

## Upstream sources

| Component | Licence | Source |
| --- | --- | --- |
| sherpa-onnx | Apache-2.0 | https://github.com/k2-fsa/sherpa-onnx |
| espeak-ng (statically linked into the above) | GPL-3.0 | https://github.com/espeak-ng/espeak-ng |
| ONNX Runtime | MIT | https://github.com/microsoft/onnxruntime |

Voice models are **not** distributed by PhonoLeaf. They download directly from
the upstream sherpa-onnx releases to the user's device. Their individual
licences are listed in the app under Settings, About, Licences.

## Status

This arrangement improves PhonoLeaf's position; it does not by itself settle
the question. The sherpa `.so` still ships inside the APK, because both
processes belong to one installed app, and whether bundling two separate
programs in one APK counts as mere aggregation or as a single combined work is
genuinely unsettled. That judgement is not made here.

[sherpa]: https://github.com/k2-fsa/sherpa-onnx
[espeak]: https://github.com/espeak-ng/espeak-ng
