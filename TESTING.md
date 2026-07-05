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

## 3. How native testing will work (Stage 2 — once the Capacitor app exists)

For orientation — these steps become relevant when the `android/` project
lands in the repo:

1. `npm install` then `npx cap open android` in the repo → Android Studio
   opens the project.
2. Select your phone in the toolbar device dropdown, press the green **Run ▶**
   button. The app builds, installs, and launches on your phone
   automatically. That's the entire loop: edit → Run ▶ → test on device.
3. Debugging the web layer inside the app: open `chrome://inspect#devices` in
   desktop Chrome while the app runs — your phone's WebView appears, click
   **inspect** for the full DevTools (console, network) of the running app.
   Guide: https://developer.chrome.com/docs/devtools/remote-debugging/
4. Known Stage-2 caveat: **Google sign-in will NOT work in the first native
   builds.** Google blocks OAuth inside embedded WebViews; the fix (Stage 3)
   is a system-browser sign-in flow (Custom Tabs + PKCE). Early native builds
   are for testing the voice engine, not the Drive flow.

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
