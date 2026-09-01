# Speech engine component: licence boundary

`java/com/phonoleaf/ttsbridge/TtsService.kt` and
`aidl/com/phonoleaf/ttsbridge/ITtsService.aidl`, both in this directory, are the
only code that touches the speech engine. They are licensed **GPL-3.0** (see the
`LICENSE` beside this file), separately from PhonoLeaf itself, which is
proprietary.

**Restructured 2026-09-01, on legal advice.** These files previously sat in
`android/app/src/main/java/com/phonoleaf/app/` alongside the proprietary code,
under the same package name, with no licence file anywhere in the repository.
Counsel's point was that publishing source is not the same as licensing it: with
no LICENSE file, default copyright applied and the code was visible but offered
to nobody. Mixing GPL sources into a proprietary directory also weakened, in
appearance and in law, the claim that these are separate programs. Hence the
separate directory, the separate package (`com.phonoleaf.ttsbridge`), the
explicit GPL-3.0 headers, and the LICENSE beside them.

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

0. **Do not move these files back under `android/app/src/main/`,** do not
   rename the package back to `com.phonoleaf.app`, and do not delete the
   `LICENSE` or the per-file headers. `npm test` fails if any of that happens.
   Nothing breaks at runtime if you do, which is exactly why it is guarded: the
   cost is legal, invisible, and paid later.
1. **Do not call sherpa from anywhere except `TtsService.kt`.** If an import of
   `com.k2fsa.sherpa.onnx` appears anywhere else, the boundary is gone. This is
   enforced by `npm test`.
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

**Reviewed by counsel, 2026-09-01.** The architecture was described in full,
including the fact that the sherpa `.so` ships inside the same APK because both
processes belong to one installed app. The assessment:

* Running in separate memory spaces and communicating over Android's standard
  inter-process mechanism, exactly as two distinct applications would, is the
  main test the Free Software Foundation applies for separate works, and this
  meets it.
* The bridge being fully autonomous, depending on nothing from PhonoLeaf and
  reusable unchanged by any other application, is the strongest part of the
  argument.
* Shipping both in one installation package leaves some residual grey area,
  since a strict reading could treat an APK as a combined work rather than as
  mere aggregation. This is why the bridge must be impeccably licensed: that is
  what demonstrates good faith and acts as the buffer.

So: strong position, not a mathematical certainty. Keep the separation clean and
do not let it erode.

[sherpa]: https://github.com/k2-fsa/sherpa-onnx
[espeak]: https://github.com/espeak-ng/espeak-ng
