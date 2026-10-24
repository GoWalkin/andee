# Andee for a non-Chinese speaker in China: setup and first use (GoWalkin fork)

What this build does is listed in [FORK.md](../FORK.md). This page covers getting it onto your Android
phone and running it. You need an **Android 11+ phone**: Andee has no iPhone version, and an iPhone
can't be automated this way.

---

## 1. Get the app

1. On GitHub, open **GoWalkin/andee → Actions**. If Actions are disabled, click **"I understand my workflows,
   go ahead and enable them"**; GitHub turns Actions off on forks.
2. Open the latest **build** run for the `feature/china-assistant` branch, download the **app-debug** artifact
   and unzip it.
3. Install **`app-arm64-v8a-debug.apk`** on the phone (almost every phone from the last 6 years is arm64).
   Allow "install unknown apps" for your file manager when asked.

## 2. Grant what it needs (in this order)

1. Open Andee. Its checklist shows each missing grant with a button.
2. **Display over other apps**: the floating ball appears.
3. **Accessibility → Andee → on**: it can now see and tap the screen. Nothing works without this.
4. Tap the ball and **allow the microphone** (optional if you only type).
5. **Notification access** for Andee, so recruiter replies and driver messages reach it.
6. On Xiaomi/MIUI/HyperOS, also set **Autostart** on and **Battery saver → No restrictions** for Andee (and for
   Termux, below), or the system kills them.

Optional, needs a computer once: `adb shell pm grant net.kuafuai.andee android.permission.WRITE_SECURE_SETTINGS`.
This lets Andee switch MIUI's hidden accessibility helper back on when WeChat-style apps hide their screen
contents. Without it, Andee reads those apps from screenshots instead, which works but is slower.

## 3. The brain: FreeLLMAPI on the phone

Andee needs an OpenAI-compatible model that **can read images and call tools**. Every tap returns a
screenshot, and every action is a tool call.

1. Install **Termux** from F-Droid (the Play Store build is outdated).
2. Follow FreeLLMAPI's own **Termux (experimental)** guide at <https://github.com/tashfeenahmed/freellmapi>.
   It needs Node 20+ (`pkg install nodejs-lts git`). The commands change between versions, so trust its README
   over this page.
3. In FreeLLMAPI's dashboard, add keys for providers you can reach **from mainland China without a VPN**:
   **Zhipu (Z.ai / bigmodel.cn)** and **ModelScope** (Qwen models; needs an Aliyun account bound to it). Google,
   Groq, OpenRouter and the rest need a VPN.
4. **Pin one model ID that supports vision and tool calling** (a Qwen-VL or GLM-V model). **Do not use `auto`.**
   Auto-routing can switch to a text-only model in the middle of a task, and then Andee's hands stop working.
5. Keep it alive: install **Termux:Boot** to start it on boot, run `termux-wake-lock`, and turn off battery
   optimisation for Termux.
6. In Andee: **⚙ → Backend → On-device**:
   - **Base URL:** `http://127.0.0.1:3001/v1`
   - **API key:** FreeLLMAPI's key if it gives you one, otherwise any non-empty text. Andee won't start its
     brain with an empty key.
   - **Model:** the model ID you pinned.
7. Open the checklist (✓). Both **Model** and **Model can see and use tools** must be green. If the second one is
   red, the model you pinned can't do the job: pick another.

**If Termux keeps getting killed:** run FreeLLMAPI on a computer at home instead and use
`http://<that computer's LAN IP>:3001/v1` as the Base URL. The **debug** APK allows plain HTTP to your own network;
the signed release APK allows only `127.0.0.1`.

**What to expect from free models:** each step sends a screenshot plus about 20k tokens of instructions, so free
daily limits run out during a long job-search batch. When quality drops or calls fail, that's the cause. A paid
vision key (Qwen-VL on DashScope, or Doubao on Volcano Ark) is the same three fields with a different URL.

**Privacy:** screenshots of your screen (chats, your CV) go to whichever provider serves the request. Free
tiers may keep or train on what they receive.

## 4. Voice (optional)

Typing works without voice: double-tap the ball. For voice, put a **Volcengine (火山引擎) speech** key in
**⚙ → Backend → Voice**. Volcengine is reachable in China; the Google option needs a VPN.

With **Can I read Chinese? = No**, answers are read by an **English voice** (default
`en_female_dacey_uranus_bigtts`; `en_male_tim_uranus_bigtts` is a male one). Change it in ⚙ → China assistant →
English voice. Nothing checks the ID in advance: if Andee goes quiet after a change, the ID is probably wrong, so
clear the field to return to the default. Volcengine's voice list is at
<https://www.volcengine.com/docs/6561/1257544>. The Chinese in brackets is shown on screen but not
read aloud.

## 5. Set it up for you

**⚙ → China assistant:**

- **Can I read Chinese? → No.** Everything gets explained in English, and drafts are back-translated for you.
- **Autonomy:**
  - **Semi-auto** (default): applies on BOSS within the cap, sends your résumé when a recruiter asks for it,
    and asks you before any other message.
  - **Assist**: prepares everything, and you press the buttons.
  - **Full-auto**: replies on your own behalf within the scene's rules. Read the warning first.
  - At every level, calling a car or placing an order needs your yes, and paying is always yours. This is
    enforced in code, not just requested of the AI.
- **BOSS直聘 applications per day:** start around **15**. New or suddenly busy accounts get flagged.

**Scenes (the 情景 button) → Ready-made scenes → Adopt** both *Job hunt on BOSS直聘* and *Get me a ride*.

## 6. First things to say

| Say or type | What happens |
|---|---|
| "My CV is in Downloads, it's called Nouman_CV.pdf. Read it." | Opens the file picker, picks the file, reads it, saves a profile (skills, roles, cities, salary, work permit), and asks about anything missing |
| "What do you remember about me?" | Shows the saved profile (also under **What it remembers**) |
| "Run my job hunt on Boss: Android jobs in Shanghai." | Enters the scene, checks your BOSS profile and greeting, searches, applies to good matches within the cap, translates replies, and builds an English **Job tracker** page |
| "Do this every day at 10am." | Sets a daily task that re-enters the scene |
| "Get me a DiDi to Hongqiao Railway Station." | Finds 虹桥火车站, fills the order, then asks: *"DiDi Express, here → Hongqiao Railway Station (虹桥火车站), about ¥45, pickup ~4 min. Call it?"* Only your yes calls the car. Then it reads you the plate number |
| "Get me a robotaxi." | Tries Pony.ai / Apollo Go via Amap or Alipay. Outside their zones, it says so and offers DiDi |
| "Translate this screen." / "What does this say?", or the **Translate key** (文A) on the ball's bar | Puts an English page on screen explaining every label and button |
| "Show the driver I'm at the north gate." | A full-screen card in big Chinese with a small English line |

## 7. Checking that the guard works (worth doing once)

1. Set **BOSS applications per day = 3** and run a job hunt. After 3 applications the log shows
   `guard refused GREET`, and Andee tells you the cap is reached.
2. Switch **Autonomy → Assist** and ask it to apply. It prepares the application and tells you which button to
   press instead of pressing it.
3. Ask for a DiDi and answer **No** at the confirmation. No car is called.

Logs, if you have a computer: `adb logcat -s Body:*` (look for lines starting `guard`).

## Known limits

- **Self-drawn screens:** the guard reads button labels. Screens that draw their own pixels (most mini-programs
  inside Alipay and WeChat, including Pony's) expose no labels, so there only the AI's instructions protect you.
  Read its confirmations.
- **BOSS直聘 may restrict accounts** that behave like bots. The cap and pacing lower the risk but don't remove it.
- **Pony.ai's own PonyPilot app is iPhone-only;** on Android this build uses Amap, Alipay or WeChat to reach it.
  Coverage is limited to designated zones in Guangzhou, Beijing, Shenzhen and Shanghai.
- **Payment pages often show as black** to Andee (Android's secure-screen flag). Paying is always your step anyway.
- **The guard's button lists are a first draft.** Until they have been checked against the current BOSS and DiDi
  screens, read Andee's confirmations.

## 8. A signed release build (optional, recommended)

The debug APK is debuggable: anyone with adb access to the phone can read the app's data. For daily use, build
a signed release:

1. On your computer, create a keystore once and keep it safe (losing it means reinstalling, which wipes Andee's data):
   `keytool -genkeypair -v -keystore andee-release.jks -alias andee -keyalg RSA -keysize 2048 -validity 10000`
2. In **GoWalkin/andee → Settings → Secrets and variables → Actions**, add `ANDEE_KEYSTORE_B64` (the output of
   `base64 -w0 andee-release.jks`), `ANDEE_STORE_PASS`, `ANDEE_KEY_ALIAS` (`andee`) and `ANDEE_KEY_PASS`.
3. The next build uploads **app-release-signed**. Install `app-arm64-v8a-release.apk`. It's a different signature
   from the debug build, so uninstall the debug build first (this wipes its data).
