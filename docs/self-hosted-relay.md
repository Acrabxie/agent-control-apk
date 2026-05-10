# Self-Hosted Relay Setup

This page is written for users and for their local coding agents. If a user asks an agent to "connect Agent Control for remote access", the agent should follow this file.

## Goal

Run a private HTTPS relay so the Android app can control the user's desktop agents from outside the local network. The relay only forwards encrypted envelopes. It does not run Codex, Claude Code, Gemini CLI, or any other agent, and it cannot decrypt paired message contents.

## Choose A Mode

- Direct / VPN: use `http://<desktop-ip>:7149` or `http://<tailscale-ip>:7149` when the phone can reach the computer directly.
- Self-hosted relay: deploy this repo's `relay/` folder to the user's own Cloudflare account, then use that relay URL in both the desktop bridge and Android app.
- Managed relay: use an app-developer-operated relay URL if the APK ships one. This is optional and not required for self-hosting.

## Agent Checklist

1. Confirm the repo path.
2. Deploy `relay/` with Wrangler.
3. Copy the deployed HTTPS Worker URL.
4. Start or restart the desktop bridge with `AGENT_CONTROL_RELAY_URL=<worker-url>`.
5. Open `http://127.0.0.1:7149` on the desktop.
6. Pair the Android app by scanning the QR code, or manually enter the relay URL plus the current 8-digit key.

Do not store Cloudflare tokens, account IDs, API keys, passwords, or private conversations in shared memory or committed files.

## Prompt To Give A Coding Agent

```text
Configure Agent Control remote access with a self-hosted relay. Read docs/self-hosted-relay.md, deploy relay/ with Wrangler to my Cloudflare account, copy the Worker HTTPS URL, start or update the desktop bridge with AGENT_CONTROL_RELAY_URL=<that URL>, then open http://127.0.0.1:7149 and pair the Android app by QR code or by entering the relay URL plus the 8-digit key. Do not commit or store Cloudflare tokens or secrets.
```

## Commands

From the repo root:

```bash
cd relay
npm install
npx wrangler login
npx wrangler whoami
npm run deploy
```

Wrangler prints a URL like:

```text
https://agent-control-relay.<account>.workers.dev
```

Return to the repo root, then start the bridge with that URL:

```bash
cd ..
AGENT_CONTROL_RELAY_URL=https://agent-control-relay.<account>.workers.dev \
AGENT_CONTROL_PORT=7149 \
node bridge/server.mjs
```

If the bridge is installed as a LaunchAgent, put the same value in the LaunchAgent environment:

```text
AGENT_CONTROL_RELAY_URL=https://agent-control-relay.<account>.workers.dev
```

Then restart the LaunchAgent.

## Pairing

Open this page on the desktop:

```text
http://127.0.0.1:7149
```

The page shows:

- QR code containing `agentcontrol://pair?...`
- current 8-digit key
- relay URL
- LAN fallback URL
- desktop fingerprint

In the Android app:

1. Open Pair.
2. Scan the QR code if possible.
3. If scanning is not available, tap custom/self-hosted address and enter:

```text
Computer or relay address: https://agent-control-relay.<account>.workers.dev
8-digit key: 1234 5678
```

The key expires after 5 minutes and rotates after successful pairing or too many failed attempts.

## Build With A Default Relay

If the user is building their own APK and wants the app to default to their self-hosted relay:

```bash
./gradlew assembleRelease \
  -PagentControlDefaultRelayUrl=https://agent-control-relay.<account>.workers.dev
```

Debug builds can use the same property:

```bash
./gradlew assembleDebug \
  -PagentControlDefaultRelayUrl=https://agent-control-relay.<account>.workers.dev
```

## Health Checks

Relay:

```bash
curl https://agent-control-relay.<account>.workers.dev/v1/health
```

Expected:

```json
{"ok":true,"service":"agent-control-relay","mode":"digital-key"}
```

Bridge:

```bash
curl http://127.0.0.1:7149/v1/health
```

Expected: JSON containing `ok: true`, `pairedDevices`, and `pairing`.

## Troubleshooting

- `desktop_offline`: the bridge is not running, not using the same relay URL, or cannot reach Cloudflare.
- `pairing_key_not_found`: the key expired, the bridge has not registered its current offer, or the app is using the wrong relay URL.
- `not_paired`: pair again with the current key.
- `desktop_timeout`: the relay reached the desktop but the desktop agent command did not reply before timeout.
- Cloudflare quota errors: use long-poll relay code from this repo, reduce idle polling, or move to a paid/self-managed backend.

## Privacy Notes For Agents

The relay sees request metadata needed to route traffic, such as desktop id, device id, request id, status code, and encrypted payload sizes. After pairing, message bodies and file payloads are encrypted for the desktop bridge. Do not add logging of decrypted payloads to the relay.
