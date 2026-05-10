# Google Play Readiness

## Status

Agent Control is ready to prepare for Google Play internal or closed testing. It is not yet ready for a production rollout until release signing, Play Console forms, store assets, and tester requirements are completed.

## Current App Facts

- Package: `com.acrab.agentcontrol`
- Current version: `1.0.1` / `versionCode 51`
- SDKs: `compileSdk 36`, `targetSdk 35`, `minSdk 26`
- Permissions: `android.permission.INTERNET` only
- Distribution model: phone-to-desktop companion app with Direct/VPN mode, self-hosted HTTPS relay mode, and optional managed relay mode
- Account model: no app account system
- Monetization/analytics: no ads and no analytics SDK
- Pairing model: short-lived numeric key, HMAC proof, ECDH, HKDF, AES-GCM, and desktop fingerprint pinning
- Persistence model: app-private Android storage for pairing state, desktop identity, runtime preferences, drafts, and recent conversation history; desktop-private bridge state on the user's computer
- Tester support: first-run setup, pairing diagnostics, `/status`, attachment, reconnect checks, copyable tester report, and per-agent/team detail sheets

## Release Signing

Google Play uploads should be Android App Bundles signed with an upload key. The Gradle release build supports either Gradle properties or environment variables:

```bash
export AGENT_CONTROL_UPLOAD_STORE_FILE="$HOME/secure/agent-control-upload.p12"
export AGENT_CONTROL_UPLOAD_STORE_PASSWORD="..."
export AGENT_CONTROL_UPLOAD_KEY_ALIAS="agent-control-upload"
export AGENT_CONTROL_UPLOAD_KEY_PASSWORD="..."

./gradlew bundleRelease
```

Equivalent Gradle properties:

```properties
agentControlUploadStoreFile=/absolute/path/to/agent-control-upload.p12
agentControlUploadStorePassword=...
agentControlUploadKeyAlias=agent-control-upload
agentControlUploadKeyPassword=...
```

Do not commit keystores, passwords, `keystore.properties`, or release artifacts.

## Google Play Console Checklist

- Create the app record with package `com.acrab.agentcontrol`.
- Enroll in Play App Signing and upload a signed `.aab`.
- Publish the privacy policy URL:
  `https://github.com/Acrabxie/agent-control-apk/blob/main/docs/privacy-policy.md`
- Use `docs/play-console-submission.md` for store listing, release notes, App access, Data safety, target audience, and content rating copy.
- Use `docs/internal-testing-guide.md` for internal/closed tester instructions.
- Ask testers to run pairing diagnostics before submitting feedback; the copied tester report redacts keys and session material.
- Complete Data safety with the draft below.
- Complete App access / review instructions with the desktop bridge setup below.
- Complete content rating questionnaire.
- Add app icon, screenshots, short description, full description, and feature graphic.
- For new personal developer accounts, complete the required closed testing period before production access if Play Console asks for it.
- Before production, run `./gradlew testDebugUnitTest assembleDebug bundleRelease lint` on a stable network.

## Data Safety Draft

Use Play Console's exact current categories, but keep these declarations aligned with the app behavior:

- Data collected or transmitted: user-entered chat messages, slash commands typed by the user, selected files/photos and their metadata when the user sends them, speech transcription text when the user uses voice input, pairing/session identifiers, desktop or relay address, desktop public-key fingerprint, runtime preferences, and recent conversation history.
- Purpose: app functionality, pairing with the user's desktop bridge, sending user requests to local agents, showing replies/progress, restoring sessions after restarts, and preserving conversation context.
- Sharing: data is sent only to the paired desktop bridge or configured relay. Managed relay mode routes traffic operated by the app developer; self-hosted relay mode routes traffic through the user's own Cloudflare Worker; Direct/VPN mode sends traffic directly to the user's computer.
- Security: post-pairing message and file payloads are encrypted with AES-256-GCM using keys derived from ECDH P-256 and HKDF-SHA256. Pairing uses HMAC-SHA256 proof with a short-lived numeric key.
- User control: users can forget a paired desktop from the Pair dialog, start a new conversation to separate context, or clear app data in Android settings.
- Ads/analytics: none.

## Store Review Notes

Use this in the Play Console reviewer instructions:

```text
Agent Control is a companion app for controlling coding agents on the user's own computer. The Android app does not work as a standalone chatbot. To review pairing, install and start the desktop bridge from https://github.com/Acrabxie/agent-control-apk, open http://127.0.0.1:7149 on the desktop, then pair the phone by scanning the QR code or entering the bridge/relay address and the 8-digit key.

The app supports Direct/VPN mode, self-hosted HTTPS relay mode, and optional managed relay mode. The only Android permission is INTERNET. Messages and selected files/photos are sent to the paired desktop bridge or configured relay; post-pairing payloads are encrypted for the desktop bridge.
```

## Product Copy Boundaries

- Say "companion app for local coding agents" rather than "cloud AI assistant".
- Say users must install the desktop bridge from the GitHub repository before pairing.
- Say remote access requires Direct/VPN, self-hosted relay, or a managed relay build.
- Do not claim "works anywhere with no setup" unless a reliable developer-operated managed relay is funded and monitored.
- Do not say users must register Cloudflare. Say "self-hosted relay is available for technical users" and keep it optional.

## Known Review Risks

- `android:usesCleartextTraffic="true"` is needed for user-entered Direct/VPN HTTP desktop addresses. Keep HTTPS as the recommended remote mode and explain direct HTTP as user-managed local/VPN connectivity.
- The app is useful only with a desktop bridge. The onboarding and review notes must make that dependency obvious.
- A managed relay changes operational responsibility and Data safety wording. If a release embeds `agentControlDefaultRelayUrl`, make sure the privacy policy and Data safety form describe that relay accurately.
