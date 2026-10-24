# GoWalkin fork of Andee

This is a modified version of [kuafuai/andee](https://github.com/kuafuai/andee), maintained by GoWalkin
for **internal use**. It turns Andee into an assistant for someone who lives in mainland China,
uses Chinese apps, and cannot read Chinese.

**Setup and first use:** [docs/china-setup.md](docs/china-setup.md).

## What this fork adds

| Feature | Where |
|---|---|
| **Bridge mode** ("I don't read Chinese"): English for everything, on-screen labels shown as *English (中文)*, messages to others drafted in Chinese and shown with an English back-translation first, mainland defaults (phone + SMS login, no Google sign-in) | `core/.../cn/BridgePrompt.kt` (§14), hooked in through `BrainHost.extraPrompt()` |
| **Autonomy switch**: Semi-auto (default) / Assist / Full-auto, on the settings card only. The model has no tool to change it | `VoiceConfig.autonomy`, `SettingsUi.chinaSection`, §15 in `BridgePrompt` |
| **Action guard in code**: pay buttons always refused; orders (calling a car, submitting an order) need the user's fresh yes to a question that stated the price; BOSS直聘 applications capped per day (default 30) with 20–45 s random spacing; free-text sends in BOSS / DiDi chat follow the autonomy level; the keyboard's Send counts as a send; a tap-length swipe counts as a tap, and slide-to-confirm controls count as orders or payments | `core/.../cn/ActionGuard.kt` (pure, tested), `app/.../cn/ActionGate.kt`, checks in `ScreenController.tap` / `longPress` / `swipe` / `submitInput`, approval recorded in the `ui.ask` branch of `CommandDispatcher` |
| **Scenes**: *Job hunt on BOSS直聘* and *Get me a ride* (DiDi; Pony.ai or Apollo Go robotaxis via Amap / Alipay / WeChat) | `app/.../cn/ChinaPresets.kt`, `Preset.triggerApps` in `ScenePresets.kt` |
| **Reading a CV**: `pick_document` opens the system picker (the brain can navigate it to the file the user named), `read_document` returns text for .docx/.txt, rendered pages for PDF, or the image | `core/.../cn/CnTools.kt`, `core/.../cn/DocxText.kt`, `app/.../cn/DocumentImport.kt`, `app/.../cn/DocumentPickActivity.kt` |
| **App aliases**: `launch_app` accepts English and pinyin names (DiDi, Gaode, Boss, Meituan…), and notification rows name apps in English on an English interface | `core/.../cn/AppAliases.kt`, fallback in `DeviceActionsController.resolveLabel`, `NotificationRelayService.appLabel` |
| **English voice**: with the bridge on, answers are read by an English Volcengine voice (default `en_female_dacey_uranus_bigtts`, changeable in settings), and the Chinese glosses are shown but not read aloud | `VoiceConfig.lookSpeaker`, `core/.../cn/SpokenEnglish.kt`, `TtsController.speakImpl` |
| **Phone voice (free)**: a third voice provider with no key and no network: the phone's own TextToSpeech (WAV → resampled PCM into the existing player) and offline Vosk recognition (English model downloaded by CI into assets). Works on Huawei / Honor without Google services | `app/.../cn/PhoneVoice.kt`, `PhoneTts.kt`, `PhoneAsr.kt`, `core/.../cn/WavPcm.kt`, provider branches in `AsrController` / `TtsController` / `MeetingRecorder`, `SettingsUi`, `SelfCheck` |
| **Translate key**: a one-tap "translate this screen" button on the ball's bar, shown only with the bridge on | `FloatingWindowUi` bar, `BodyCoreService.onTranslateClick`, `res/drawable/ic_translate.xml` |
| **FreeLLMAPI and strict OpenAI gateways**: loopback HTTP allowed; `thinking` / `reasoning_effort` dropped automatically after a gateway refuses them; a self-check row tests that the model can read a screenshot and call a tool | `res/xml/network_security_config.xml`, `LlmClient.rejectsThinkingFields`, `core/.../cn/BrainSkills.kt`, `SelfCheck.brainSkills` |
| **Recruiter replies aren't lost**: notifications from BOSS直聘 (and from the active scene's apps) that arrive while a task runs are queued and re-offered when it ends. BOSS is in the default chat tier | `NotificationRelayService` (`held`, `flushHeld`, `PUSH_PKGS`) |
| **No phone-home**: the update check is off unless `VERSION_API_BASE` is set in `local.properties` | `SelfCheck.version`, `app/build.gradle` |
| **LAN brain (debug build)**: plain HTTP to any host, so FreeLLMAPI can run on a home PC. Release stays strict | `app/src/debug/res/xml/network_security_config.xml` |
| **Signed release in CI** once the four `ANDEE_*` secrets exist; unsigned gate otherwise | `app/build.gradle` (`signingConfigs`), `.github/workflows/build.yml` |
| CI builds `feature/**` branches, runs `:core:test`, and uploads the debug APKs | `.github/workflows/build.yml` |

New tools live outside `ToolSchemas.kt`, and the base system prompt (`LocalPrompt.TEXT`) is unchanged, so
upstream's golden snapshot tests stay valid. New logic lives in `cn/` packages, and existing files get only
hook lines, which keeps upstream merges small.

## Modified upstream files (Apache 2.0 §4(b) / LICENSE A.1(c))

`.github/workflows/build.yml`, `CHANGELOG.md`, `CLAUDE.md`, `README.md`, `app/build.gradle`,
`app/src/debug/res/xml/network_security_config.xml`, `app/.../tts/TtsController.kt`, `app/.../ui/FloatingWindowUi.kt`,
`app/.../BodyCoreService.kt`, `app/.../asr/AsrController.kt`, `app/.../meeting/MeetingRecorder.kt`, `core/.../config/VoiceSettings.kt`, `app/proguard-rules.pro`, `.gitignore`,
`app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`,
`app/src/main/res/values-en/strings.xml`, `app/src/main/res/xml/network_security_config.xml`,
`app/.../brain/AndroidBrainHost.kt`, `app/.../config/ScenePresets.kt`, `app/.../config/VoiceConfig.kt`,
`app/.../device/DeviceActionsController.kt`, `app/.../device/NotificationRelayService.kt`,
`app/.../device/SelfCheck.kt`, `app/.../net/CommandDispatcher.kt`, `app/.../screen/ScreenController.kt`,
`app/.../ui/SettingsUi.kt`, `core/.../brain/BrainHost.kt`, `core/.../brain/LlmClient.kt`,
`core/.../brain/LocalBrain.kt`, `docs/permissions.md`, `docs/permissions.zh-CN.md`.
Every change is marked `GoWalkin fork` in a comment at the site.

## License obligations we keep

- **LICENSE travels with the code, unchanged.** This file is the notice of modification it requires.
- **No white-labelling (A.1(a)):** the app still shows the name *Andee*, its icon and its copyright notices.
- **No multi-tenant hosting (A.1(b)) and no embedding in a product (A.1(d))** without written permission from
  KUAFUAI. This fork is for GoWalkin's own devices. If that changes, ask KUAFUAI first.
- **Not an official release (A.3).**
- **Acceptable use** (`docs/acceptable-use.md`) still applies, and breaking it terminates the license. Note
  §3: a human must be present for any order or payment, which the action guard enforces for labelled buttons.
  Note also §4, "review before it speaks for you", which **Full-auto knowingly relaxes**. The settings card says so.

## Staying in sync with upstream

```bash
git fetch upstream main
git merge upstream/main     # conflicts, if any, are in the hook lines listed above
```
