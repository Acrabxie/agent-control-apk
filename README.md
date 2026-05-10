# Agent Control APK

Android MVP for phone-based control of local coding agents: Codex, Claude Code, Antigravity, Gemini CLI, and their subagents.

## Built In

- Conversational control surface for direct agent and subagent chat.
- Natural light-gray execution/status stream alongside text output, including Codex JSONL stage messages and Claude/Gemini/Antigravity/OpenCode stdout, stderr, and JSON event hooks for running, editing, creating, build/test, model fallback, and context-compaction events.
- Slash commands remain available by typing `/status`, `/agents`, `/spawn`, `/team`, `/parent`, `/memory`, `/heartbeat`, `/files`, `/photo`, `/tools`, `/handoff`, `/approve`, `/pause`, `/resume`, `/stop`, `/clear`, `/api`, or `/help` in the composer.
- Per-agent runtime model menus: Codex, Claude Code, Gemini CLI, Antigravity/OpenClaw, OpenCode, and subagents expose the model IDs that belong to their own adapter instead of sharing Codex-only GPT choices.
- Lightweight onboarding and tester diagnostics for pairing, `/status`, attachment, reconnect, and copyable tester reports without keeping a permanent setup screen in the main app.
- Encrypted connection diagnostics for paired sessions, plus public lightweight bridge/relay health checks.
- Agent and team detail sheets with identity, runtime controls, tools, recent action/error, and team shared material.
- Bidirectional transfer model for photos and files.
- Team roster with one administrator and explicit parent-child agent hierarchy.
- Persistent subagents created by agents or `/spawn`, stored by the bridge and shown as first-class app conversations; users can remove any persistent subagent with `/dismiss Name`, and parent/self agents can remove scoped children through the directive protocol.
- Persistent teams created by agents or `/team-create`, shown as group-chat rows with shared team material and agent-to-agent messages; group rounds let all available members discuss in order and stop when the user sends `/stop` or at least one-third of members vote to stop.
- Project panel for editing memory, queue, heartbeat, and bridge API documents.
- Short-lived 8-digit desktop key pairing with HMAC-SHA256 proof, ECDH P-256, HKDF-SHA256, and AES-256-GCM.
- Three connection modes with the same numeric-key pairing UX: Direct/VPN, self-hosted HTTPS relay, and optional managed relay.
- First-install two-page onboarding: install the desktop bridge/relay from this repo with a coding agent, then pair the phone or skip for later.
- WhatsApp/Telegram-inspired chat screen with bottom rounded composer and no always-visible command list.

## Bridge API

The desktop companion daemon should expose a compact encrypted API:

- `GET /v1/pairing-challenge`
- `POST /v1/pair`
- `GET /v1/health`
- `GET /v1/diagnostics`
- `GET /v1/stream`
- `POST /v1/messages`
- `POST /v1/files`
- `PATCH /v1/projects/{projectId}/documents/{documentId}`
- `GET /v1/slash-commands`

After pairing, every request and server-sent event is wrapped in `agent-control.v1` and encrypted with the derived AES-256-GCM session key.

The included Node bridge lives in `bridge/server.mjs`. On this Mac it is installed as a LaunchAgent and listens on port `7149`. Open the desktop pairing page locally to see the current key:

```text
http://127.0.0.1:7149
```

The 8-digit key expires after 5 minutes and is invalidated after successful pairing or too many failed attempts.

## Connection Modes

Agent Control supports three modes. The phone UI always pairs with the desktop by QR code or by address plus 8-digit key.

### 1. Direct / VPN

Use a direct desktop URL when the phone can reach the computer:

```text
http://192.168.1.42:7149
http://100.x.y.z:7149
```

This is free and works well with LAN, Tailscale, ZeroTier, or another user-managed VPN. It is not reliable as a default Google Play path because routers, guest Wi-Fi, carrier networks, VPNs, and AP isolation can block phone-to-desktop HTTP.

### 2. Self-Hosted Relay

Recommended for technical users who already run Codex, Claude Code, Gemini CLI, or similar local agents. The user deploys `relay/` to their own Cloudflare account, then uses that HTTPS URL in both places:

```bash
# Desktop bridge
AGENT_CONTROL_RELAY_URL=https://agent-control-relay.<account>.workers.dev \
AGENT_CONTROL_PORT=7149 \
node bridge/server.mjs
```

```text
# Android app Pair dialog
Computer or relay address = https://agent-control-relay.<account>.workers.dev
8-digit key = the current key shown at http://127.0.0.1:7149
```

Full agent-readable setup instructions live in [`docs/self-hosted-relay.md`](docs/self-hosted-relay.md).

### 3. Managed Relay

Optional future/default path where the app developer operates the relay and embeds the URL in the APK. Users only scan the QR code or enter the 8-digit key. For a managed build:

```bash
./gradlew assembleRelease -PagentControlDefaultRelayUrl=https://agent-control-relay.example.com
```

The desktop bridge must be started with the same relay URL:

```bash
AGENT_CONTROL_RELAY_URL=https://agent-control-relay.example.com AGENT_CONTROL_PORT=7149 node bridge/server.mjs
```

## Persistent Subagents

Subagents are persisted by the bridge in `~/.agents/shared-agent-loop/agent-control-subagents.json`. They are returned in every snapshot and appear in the Android app as normal chat rows. Tapping a subagent routes the message through its parent agent adapter with the subagent role/context included.

Users can create one manually with `/spawn Name`. Agent adapters can create one by including a single standalone directive in their reply:

```text
AGENT_CONTROL_CREATE_SUBAGENT {"name":"ReleaseGuard","role":"release checklist monitor","tools":["direct-chat","report"]}
```

The bridge hides the directive from chat, persists the child, and broadcasts `agent.spawned` plus `team.changed`.

Users can remove a persistent subagent from any conversation with:

```text
/dismiss ReleaseGuard
```

Agents can withdraw a scoped child or themselves with:

```text
AGENT_CONTROL_REMOVE_SUBAGENT {"id":"ReleaseGuard","reason":"no longer needed"}
```

## Team Group Chats

Teams are persisted by the bridge in `~/.agents/shared-agent-loop/agent-control-teams.json`. They appear in the same Chat list as agents, with a group avatar, shared profile, member list, and shared documents/material.

Users can create one manually with `/team-create Name`. Agent adapters can create one by including a single standalone directive:

```text
AGENT_CONTROL_CREATE_TEAM {"name":"MobileOps","purpose":"mobile release coordination","members":["codex","claude","gemini_cli"],"sharedProfile":"release room context","sharedDocuments":["MEMORY.md","QUEUE.md"]}
```

Agents can also post into a team without showing the directive text:

```text
AGENT_CONTROL_TEAM_MESSAGE {"teamId":"team-mobileops","text":"ReleaseGuard should watch the next build."}
```

When a user sends a normal message to a team row, the bridge lets available members answer in order, so later members see the earlier group replies. A team round stops after `/stop` from the user or after at least one-third of participating members request stop:

```text
AGENT_CONTROL_TEAM_STOP {"reason":"blocked on user confirmation"}
```

Google Play readiness notes live in [`docs/google-play-readiness.md`](docs/google-play-readiness.md). Play Console copy lives in [`docs/play-console-submission.md`](docs/play-console-submission.md), tester instructions live in [`docs/internal-testing-guide.md`](docs/internal-testing-guide.md), and the publishable privacy policy draft lives in [`docs/privacy-policy.md`](docs/privacy-policy.md).

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release Bundle

Configure an upload key with environment variables or Gradle properties, then build an Android App Bundle for Play Console:

```bash
export AGENT_CONTROL_UPLOAD_STORE_FILE="$HOME/secure/agent-control-upload.p12"
export AGENT_CONTROL_UPLOAD_STORE_PASSWORD="..."
export AGENT_CONTROL_UPLOAD_KEY_ALIAS="agent-control-upload"
export AGENT_CONTROL_UPLOAD_KEY_PASSWORD="..."

./gradlew bundleRelease
```

The release AAB is written to `app/build/outputs/bundle/release/app-release.aab`. Do not commit keystores, passwords, or release artifacts.
