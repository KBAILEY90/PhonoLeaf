# Testing PhonoLeaf

A practical guide for testing PhonoLeaf at each stage — written assuming no
prior app-testing experience. Everything here is free; the only money in the
entire pipeline is Google Play's **one-time $25** developer fee, paid only
when we actually publish (and Apple's $99/year if/when we do iOS).

---

## 1. Testing the web app (works today)

### On your computer
1. Open https://kbailey90.github.io/phonoleaf/ in **Chrome**.
2. Check the build: **Settings tab → footer** shows a 7-character build code
   (e.g. `4d29b8d`). Compare it against the latest commit on
   https://github.com/KBAILEY90/PhonoLeaf/commits/main — if it doesn't match,
   the deploy hasn't finished or the page is cached (hard-reload:
   `Ctrl+Shift+R`).
3. First launch after this update downloads the ~90 MB voice model once (you
   will see a progress toast, then "Natural voice ready"). After that it's
   cached and loads in seconds.
4. Open a book and play. The voice should be the natural (Kokoro) voice, with
   no gaps between sentences.

### On your phone (PWA — the browser version)
The phone browser can't run the neural voice in real time (that's exactly why
we're building the native app). Expected behavior there:
- The app silently uses your **device's system voice** and Settings shows a
  "Natural voice — Unavailable on this device" row with a **Retry** button.
- Since you installed the sherpa-onnx Kokoro engine as your system TTS, your
  "fallback" voice is actually Kokoro — with gaps between sentences. Those
  gaps are what the native app eliminates.

### If something looks wrong
- **Settings → Debug log → Copy log** and paste it to Claude. It records
  page-turn decisions and voice-engine verdicts (`{"e":"bench", ...}` = the
  device speed probe).

---

## 2. One-time setup for native app testing (~1 hour)

Do these once, in order, and you're permanently equipped to test Android
builds. All free.

### 2.1 Install Android Studio (on your PC)
1. Download: https://developer.android.com/studio
2. Run the installer, accept the defaults (they include the Android SDK and
   an emulator). First launch runs a setup wizard — again accept defaults
   ("Standard" install). It downloads a few GB; let it finish.

### 2.2 Put your phone in developer mode
Official guide: https://developer.android.com/studio/debug/dev-options
1. On the phone: **Settings → About phone → Software information** (Samsung)
   and tap **Build number seven times**. A toast counts down, then says
   "you are now a developer".
2. Go back to Settings → **Developer options** (now visible; on Samsung it's
   at the bottom of the main Settings list).
3. Turn on **USB debugging**.

### 2.3 Connect the phone
1. Plug the phone into the PC with a USB cable (a data cable, not
   charge-only — if in doubt use the one that came with the phone).
2. The phone shows **"Allow USB debugging?"** with the PC's fingerprint —
   check "Always allow from this computer" and tap **Allow**.
3. Verify: in Android Studio, the device dropdown in the toolbar shows your
   phone model. (No project open yet? That's fine — you'll see it in
   Stage 2.)

### 2.4 (Optional) The emulator
Android Studio can also run a virtual phone (Tools → Device Manager →
Create device): https://developer.android.com/studio/run/emulator
Useful for UI checks, but **never judge voice speed on it** — the emulator's
performance is nothing like a real phone's. Kokoro verdicts come from your
real device only.

---

## 3. Native testing (Stage 2 — the Capacitor project is in the repo)

> **Golden rule for every command below:** npm commands only work inside the
> project folder (the one containing `package.json`). Always start with:
>
> ```
> cd C:\Repo\phonoleaf
> ```
>
> Running `npm install` anywhere else (e.g. `C:\Users\kevin`) gives
> `ENOENT: Could not read package.json` — npm is telling you there's no
> project in that folder.
>
> (If the folder on your PC is still called `C:\Repo\koboaudio`, rename it
> to `C:\Repo\phonoleaf` first — close any terminal/editor/Claude session
> using it, then rename in File Explorer. Git doesn't mind.)

### 3.1 One-time project setup
Open **Command Prompt** (search Start menu for `cmd`) and run:

```
cd C:\Repo\phonoleaf
npm install
```

This installs Capacitor (the native shell tooling) into `node_modules/` —
takes a few seconds.

> **Using PowerShell instead of Command Prompt?** You'll likely hit:
> `npm.ps1 cannot be loaded because running scripts is disabled on this
> system`. That's PowerShell's script execution policy blocking npm's
> `.ps1` wrapper — Command Prompt doesn't have this restriction, which is
> why it's recommended above. To use PowerShell anyway, run this once
> (no admin rights needed, type `Y` to confirm):
> ```
> Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
> ```

### 3.2 The test loop (every time)

```
cd C:\Repo\phonoleaf
npm run sync
npm run open
```

- `npm run sync` copies the current web app (`index.html` etc.) into the
  native project — run it after ANY web-app change so the app sees it.
- `npm run open` opens the project in Android Studio.
- **First open only:** Android Studio runs a "Gradle sync" (progress bar at
  the bottom, downloads build tools — several minutes; needs internet). Let
  it finish before doing anything.
- Plug in your phone → pick it in the device dropdown (top toolbar) → press
  the green **Run ▶** button. The app builds, installs, and launches on the
  phone. That's the whole loop: change → `npm run sync` → Run ▶.

### 3.3 Debugging the app on the phone
With the app running, open `chrome://inspect#devices` in desktop Chrome —
your phone's WebView appears; click **inspect** for full DevTools (console,
network) of the live app.
Guide: https://developer.chrome.com/docs/devtools/remote-debugging/

### 3.4 What works at each stage
- **Stage 2a (done — plain wrapper):** the app installs and launches to the
  sign-in screen. That proves your whole toolchain.
- **Stage 3 (done — Android OAuth client created 2026-07-06):** sign-in goes
  through your phone's real browser and returns to the app with a permanent
  session (refresh token — no more hourly re-login). `npm run sync` + Run ▶
  and test it: tap Sign In, pick your account in the browser tab that opens,
  confirm you land back in PhonoLeaf's library. You may see a "Google hasn't
  verified this app" warning first (expected pre-launch — the drive.readonly
  scope needs formal verification eventually; for now click Advanced →
  Continue).
- **Stage 2b (done — needs the one-time model placement in §3.6):** the native
  Kokoro plugin (sherpa-onnx). Once the model files are in place, the app reads
  with on-device neural Kokoro at native speed — gapless, the whole point of
  going native. Without the model files it still works, just falling back to
  the device voice.

Reference: the Android OAuth client is "PhonoLeaf Android (debug)" in
[Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials),
package `com.phonoleaf.app`, tied to this PC's debug-keystore SHA-1. A Play
Store release build is signed differently and will need its own SHA-1 added
to the same client later (Play App Signing shows it in the Play Console).

### 3.6 Stage 2b one-time setup: place the Kokoro voice model (~10 min)

The neural voice model is too big for git, so you download it once and drop it
into the app. The sherpa-onnx native library (the code that runs it) IS
committed, so this is the only manual piece.

> **Use `kokoro-int8-en-v0_19` — the int8 English model.** Same 11 English
> voices as the plain `kokoro-en-v0_19`, but "int8" means it's quantized to run
> **2-4× faster** on a phone — the fp32 model took 10-30 s to generate ONE
> sentence (unusably slow, froze the UI); int8 is what your standalone
> sherpa-onnx test used when it ran faster than realtime. The voices and their
> order are identical, so nothing in the app changes — just the model file.
> **Delete the old `...\assets\kokoro\` folder first.**

1. **Delete the old model folder:**
   ```
   rmdir /s /q C:\Repo\phonoleaf\android\app\src\main\assets\kokoro
   ```
   (The app caches a copy on the phone too, but it re-copies automatically when
   the model changes — no need to touch the phone.)

2. **Download + extract into the app's model folder** — from a terminal
   (Windows 11 has `tar` built in):
   ```
   cd C:\Repo\phonoleaf\android\app\src\main\assets
   curl -L -o k.tar.bz2 https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2
   tar -xf k.tar.bz2
   ren kokoro-int8-en-v0_19 kokoro
   del k.tar.bz2
   ```

3. **Verify the layout** — `model.onnx` must sit DIRECTLY in the `kokoro`
   folder, not in a nested subfolder:
   ```
   android\app\src\main\assets\kokoro\model.onnx
   android\app\src\main\assets\kokoro\voices.bin
   android\app\src\main\assets\kokoro\tokens.txt
   android\app\src\main\assets\kokoro\espeak-ng-data\...
   ```
   (This English model has NO `dict\` or `lexicon-*.txt` files — that's
   expected; the plugin only uses what's present.)

4. **Build and run:** `npm run sync`, then Run ▶ in Android Studio.

5. **Test:** open a book and play. First playback shows "Preparing the natural
   voice…" while the app copies the model into place (a few seconds, one time),
   then reads — now with short/no gaps between sentences, and the UI stays
   responsive (you can leave the reader). Try the voices in the picker.

Notes:
- This model folder is gitignored — it never gets committed, and each fresh
  clone re-does this step.
- The whole model is bundled into the debug APK (big APK, fine for testing).
  The Play Store build will download it on first run instead (Stage 5) so the
  store download stays small.
- **If playback still gaps between sentences, grab Settings → Debug log →
  Copy log and send it.** The `nsynth` entries show generation time (`g`) vs
  audio length (`a`) in ms and their ratio (`r`) — `r` under ~1.0 means the
  device generates faster than it plays (should be gapless); repeated `r`
  above 1.0 means the model is too slow on this device and we'd switch to the
  smaller int8 model (`kokoro-int8-multi-lang-v1_1`, but note its different
  voice set).

---

## 4. Regression checklist (run before any release)

Quick pass, ~10 minutes, on both desktop Chrome and the phone:

- [ ] Sign in with Google; greeting shows your name
- [ ] Library lists your books, covers fill in
- [ ] Open a book → resumes where you left off
- [ ] Audio starts, reads the visible page, follows page turns
- [ ] Swipe forward through a chapter end with a short last page (the
      historical bug area — must land on it, then move on cleanly)
- [ ] Voice picker: pick two different voices — they sound different
- [ ] Speed change mid-sentence: no skipped text, cache still hits
- [ ] Double-tap pause/resume; reader chrome auto-hides
- [ ] Stats tab: minutes accrue; Reset clears everything including
      started/finished
- [ ] Theme: light/dark/auto all render correctly (incl. reading surface)
- [ ] Settings footer build code matches the latest commit

---

## 5. Publishing (later — for reference only)

1. **Google Play Console** account: one-time $25 —
   https://play.google.com/console/signup
2. First distribution goes to the **Internal testing** track (up to 100
   testers, no review delays, instant updates):
   https://support.google.com/googleplay/android-developer/answer/9845334
3. Store listing, privacy policy (roadmap item), then production rollout.
4. iOS later: Apple Developer Program $99/year —
   https://developer.apple.com/programs/
