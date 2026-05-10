# Agent Control Privacy Policy

Effective date: May 5, 2026

Agent Control is a companion app for controlling coding agents that run on a user's own computer or relay. The app does not include ads, analytics SDKs, or an account system.

## Data The App Handles

Agent Control may handle the following data when the user chooses to use those features:

- Messages typed into the chat composer.
- Slash commands typed as normal messages.
- Photos or files selected by the user for sending to the paired desktop.
- Pairing and connection metadata, including the desktop or relay address, device id, desktop public-key fingerprint, pairing timestamps, session state, and recent conversation history.
- Runtime preferences such as selected agent, model, reasoning level, permission mode, draft text, and local app settings.
- Speech transcription text returned by the device's speech recognition flow when the user uses voice input.

## How Data Is Used

Data is used to pair the phone with the user's desktop bridge, send user requests to the selected local agent, show replies and progress, preserve conversation context, and keep the app usable after the Android process or desktop bridge restarts.

## Data Sharing And Transfer

Agent Control sends messages, selected files, selected photos, and connection metadata only to the paired desktop bridge or the relay address configured by the user or by the app build.

In relay mode, the relay routes requests between the phone and desktop bridge. Post-pairing message and file payloads are encrypted for the paired desktop bridge. A relay may still process routing metadata needed to deliver requests, such as relay paths, desktop identifiers, request identifiers, timing, and basic health information.

Agent Control does not sell user data and does not share data with advertising or analytics providers.

## Security

Pairing uses a short-lived numeric key with HMAC-SHA256 proof. The app and desktop bridge establish a session with ECDH P-256 and HKDF-SHA256, then protect post-pairing payloads with AES-256-GCM. The app pins the paired desktop public-key fingerprint and requires re-pairing if that identity changes.

Direct desktop mode may use a user-entered HTTP address for LAN, Tailscale, ZeroTier, or another user-managed network. Remote relay mode should use HTTPS.

## Local Storage

The Android app stores pairing state, desktop identity, runtime preferences, and recent conversation history in app-private storage. The desktop bridge stores its own private bridge state on the user's computer. These stores are used to restore the pairing and conversation experience after restarts.

## User Control

Users can remove a paired desktop from the Pair dialog. Users can start a new conversation for an agent or team to keep new requests separate from prior context. Users can remove app data through Android system settings.

## Children

Agent Control is intended for developers and technical users. It is not designed for children.

## Contact

For privacy questions or deletion requests related to a managed relay operated by this project, open an issue at:

https://github.com/Acrabxie/agent-control-apk/issues

For self-hosted relays or direct desktop mode, data is controlled by the user and their own infrastructure.

## Changes

This policy may be updated as Agent Control changes. Material changes should be reflected in this file and in the Google Play listing before release.
