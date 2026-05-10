# Agent Control Relay

Cloudflare Worker relay for out-of-home Agent Control sessions. This folder is intended for self-hosting: a user or their local coding agent can deploy it to the user's own Cloudflare account and paste the resulting HTTPS URL into Agent Control.

The user-facing pairing flow stays simple:

1. Desktop bridge shows an 8-digit key.
2. Android app uses the relay URL and that key.
3. The relay forwards pairing, encrypted message, snapshot, file, and project-document envelopes between the phone and desktop.

The relay does not run agents and does not decrypt post-pairing payloads. The desktop bridge still owns ECDH pairing, AES-GCM session keys, agent routing, and file handling.

The relay keeps hot-path state in Durable Object memory and uses desktop long polling. Empty desktop polls wait briefly for work instead of returning immediately, which keeps idle request volume low enough for development and small private use.

For full setup instructions written for humans and coding agents, read [`../docs/self-hosted-relay.md`](../docs/self-hosted-relay.md).

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

After deploy, set the desktop LaunchAgent or shell environment to the URL printed by Wrangler:

```bash
AGENT_CONTROL_RELAY_URL=https://agent-control-relay.<account>.workers.dev
```

In the Android Pair dialog, use the same URL as the computer or relay address, then enter the current 8-digit key from `http://127.0.0.1:7149`.

If the user is building their own APK and wants the app to default to this self-hosted relay, pass the deployed HTTPS URL into the Android build:

```bash
./gradlew assembleRelease -PagentControlDefaultRelayUrl=https://agent-control-relay.<account>.workers.dev
```
