# Agent Control Relay

Cloudflare Worker relay for out-of-home Agent Control sessions.

The user-facing pairing flow stays simple:

1. Desktop bridge shows an 8-digit key.
2. Android app uses the relay URL and that key.
3. The relay forwards pairing, encrypted message, snapshot, file, and project-document envelopes between the phone and desktop.

The relay does not run agents and does not decrypt post-pairing payloads. The desktop bridge still owns ECDH pairing, AES-GCM session keys, agent routing, and file handling.

## Run Locally

```bash
npm install
npm run dev
```

Then start the desktop bridge with:

```bash
AGENT_CONTROL_RELAY_URL=http://127.0.0.1:8787 AGENT_CONTROL_PORT=7149 node ../bridge/server.mjs
```

## Deploy

```bash
npx wrangler whoami
npm run deploy
```

After deploy, set the desktop LaunchAgent or shell environment:

```bash
AGENT_CONTROL_RELAY_URL=https://agent-control-relay.<account>.workers.dev
```

Android users enter that HTTPS relay URL plus the current 8-digit desktop key. LAN URLs are only a fallback for local networks that allow direct phone-to-desktop HTTP.

For store builds, pass the deployed HTTPS URL into the Android build so the pair dialog defaults to the built-in secure relay:

```bash
./gradlew assembleRelease -PagentControlDefaultRelayUrl=https://agent-control-relay.example.com
```
