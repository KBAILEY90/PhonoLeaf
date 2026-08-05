# Testing PhonoLeaf

A practical guide for testing PhonoLeaf at each stage — written assuming no
prior app-testing experience. Everything here is free; the only money in the
entire pipeline is Google Play's **one-time $25** developer fee, paid only
when we actually publish (and Apple's $99/year if/when we do iOS).

---

## 1. Testing the web app (works today)

### On your computer
1. Open https://phonoleaf.com/ in **Chrome**.
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
- **Stage 2b (done — no setup needed, see §3.6):** the native neural TTS
  plugin (sherpa-onnx / Piper). The app downloads its voice model itself on
  first run (prompted during onboarding), then reads on-device at native
  speed — gapless, the whole point of going native. Before a pack is
  installed it still works, just falling back to the device voice.

Reference: the Android OAuth client is "PhonoLeaf Android (debug)" in
[Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials),
package `com.phonoleaf.app`, tied to this PC's debug-keystore SHA-1. A Play
Store release build is signed differently and will need its own SHA-1 added
to the same client later (Play App Signing shows it in the Play Console).

### 3.6 Voice models: nothing to place any more (was a ~10 min manual step)

**This section used to walk through downloading a model and dropping it into
`android\app\src\main\assets\kokoro\`. That step is GONE as of 2026-08-04 —
do not do it.** Every voice model, including the US default, is now a
**language pack the app downloads itself at runtime** into its own storage.
Nothing model-related ships in the APK, and nothing needs placing in a fresh
clone.

Why it changed: a bundled model was stored **twice** on device — once in the
APK's assets (which the app can never delete) and once as the filesDir copy
the native engine actually requires (espeak-ng uses ordinary file I/O and
can't read through Android's AssetManager). That put a US-only install at
~157 MB and meant "Remove" could only ever reclaim half of it. Downloading it
like every other pack leaves exactly one, fully deletable copy — and drops
the store download to app code only. See CLAUDE.md's "FULLY UNBUNDLED" note.

**Current catalog** (all from the same public sherpa-onnx GitHub release):
English (US) `us` and English (UK) `gb` — multi-speaker, 4 owner-auditioned
voices each; French `fr`, German `de`, Spanish `es` — single-speaker models
(siwis/thorsten/davefx, the standard community Piper voice per language),
**not yet owner-audited for quality/gender** (see the `PIPER_VOICES` comment
in `index.html`).

1. **Build and run:** `npm run sync`, then Run ▶ in Android Studio. No model
   setup first — it builds and installs with no voice data at all.

2. **First run does the download.** Sign in, pick your Drive folder, and the
   **Language Packs** popup appears automatically as the last onboarding step
   (fires once ever, guarded by `pl_packs_onboarded`). Pick a language and it
   downloads (~65–80 MB, needs real internet). Until a pack is installed the
   app falls back to the device's own robotic voice, so reading still works —
   it just won't sound natural.

3. **Managing packs later:** **Settings → Language packs → Downloads** lists
   all five with Download/Remove each. Things worth exercising here:
   - **Remove works on every pack including English (US)** — it frees the
     whole ~78 MB now, and re-downloading it is a normal download with a real
     percentage (not the old "Reinstall" special case, which is gone).
   - **Queueing:** start two packs back-to-back. The second shows **Queued…**
     until the first finishes — they run one at a time by design. Both should
     complete in order; neither should error out or freeze.
   - **Cancel** mid-download, then re-download, and confirm the pack still
     installs cleanly (a cancelled download must leave nothing half-written).

4. **Voice picker** (reader → voice button): lists only voices whose pack is
   installed, plus a trailing **"Get more voices"** row that opens the same
   Language Packs popup. With no pack installed it shows an empty state rather
   than a list of unusable voices. Picking a voice previews it mid-read — this
   is the first real chance to judge the French/German/Spanish voices and
   relabel them from the generic "French voice"/"German voice"/"Spanish voice"
   placeholders, the same way the US/UK set was curated.

Notes:
- **Upgrading an existing install must NOT re-download.** A device that ran
  the old bundled build already has `filesDir/kokoro` with a valid
  `.ready-` marker; the pack folder key was deliberately left as `kokoro` (not
  `kokoro-us`) so that copy is recognised as already installed. Worth
  confirming explicitly on the first device test after this change.
- `assets\kokoro*\` stays gitignored purely so a stale local copy can't get
  committed by accident — the plugin ignores those paths entirely now.
- The readout header shows `cores=N type/provider` (e.g. `vits/cpu`) and each
  line is `gGEN aAUDIO rRATIO`. `r` under 1.0 = faster than realtime = gapless.

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
- [ ] **Background playback (native): start reading, then lock the screen —
      audio keeps going; the lock screen shows a media notification with the
      book/chapter and working play/pause (and ⏭ moves a page). Switch to
      another app — audio continues.**

### Background-playback notes
- First launch may show an Android "Allow notifications?" prompt — allow it so
  the lock-screen media controls appear.
- If audio stops when the screen locks, check the phone's battery settings for
  PhonoLeaf and set it to **Unrestricted** (aggressive OEM battery savers —
  Samsung, Xiaomi, etc. — kill background audio otherwise). This is a
  device-settings issue, not an app bug, but worth confirming.

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
