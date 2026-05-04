# Google Play Readiness

## Current Defaults

- Package: `com.xiehaibo.agentcontrol`
- `targetSdk`: 35
- Distribution model: phone-to-desktop companion app with HTTPS relay mode for normal use and LAN mode as a local fallback, no account system, no ads, no analytics SDK.
- Network model: user-provided bridge or relay URL, then app-level HMAC proof plus AES-GCM encrypted payloads after pairing.

## Data Safety Draft

- Data collected by the app: user-entered messages, selected file/photo metadata, and selected file/photo contents when the user sends them to their paired desktop.
- Sharing: data is sent only to the user-provided paired desktop bridge or configured relay. In relay mode, post-pairing message payloads remain encrypted for the desktop bridge.
- Security practices: pairing proof uses HMAC-SHA256; post-pairing payloads are encrypted with AES-256-GCM using keys derived from ECDH P-256 and HKDF-SHA256.
- User control: users can forget the paired desktop in the pairing dialog; the app currently stores pairing state only in the running app session.

## Store Review Notes

- Keep `targetSdk >= 35` for current Google Play submission requirements.
- Keep `android.permission.INTERNET`; avoid adding storage, contacts, location, microphone, camera, notification, or account permissions unless a later feature truly needs them.
- The app uses cleartext HTTP only for user-entered local-network desktop addresses. Google Play builds should present HTTPS relay as the recommended/default connection path.
- Set `agentControlDefaultRelayUrl` during release builds so the pair dialog uses the built-in HTTPS relay by default and hides LAN/manual addressing behind the custom address action.
- Before production release, publish a privacy policy matching the Data Safety declarations above.
