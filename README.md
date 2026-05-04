# Agent Control APK

Android MVP for phone-based control of local coding agents: Codex, Claude Code, Antigravity, Gemini CLI, and their subagents.

## Built In

- Conversational control surface for direct agent and subagent chat.
- Visible tool-call timeline alongside text output.
- Slash commands remain available by typing `/status`, `/agents`, `/spawn`, `/team`, `/parent`, `/memory`, `/heartbeat`, `/files`, `/photo`, `/tools`, `/handoff`, `/approve`, `/pause`, `/resume`, `/stop`, `/clear`, `/api`, or `/help` in the composer.
- Bidirectional transfer model for photos and files.
- Team roster with one administrator and explicit parent-child agent hierarchy.
- Persistent subagents created by agents or `/spawn`, stored by the bridge and shown as first-class app conversations.
- Persistent teams created by agents or `/team-create`, shown as group-chat rows with shared team material and agent-to-agent messages.
- Project panel for editing memory, queue, heartbeat, and bridge API documents.
- Short-lived 8-digit desktop key pairing with HMAC-SHA256 proof, ECDH P-256, HKDF-SHA256, and AES-256-GCM.
- Cloudflare Worker relay for out-of-home control while keeping the same numeric-key pairing UX; LAN direct mode remains a local fallback.
- WhatsApp/Telegram-inspired chat screen with bottom rounded composer and no always-visible command list.

## Bridge API

The desktop companion daemon should expose a compact encrypted API:

- `GET /v1/pairing-challenge`
- `POST /v1/pair`
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

For production and Google Play use, deploy `relay/` and enter the HTTPS relay URL shown on the desktop page. LAN addresses such as `http://192.168.1.42:7149` are only a local fallback because routers, guest Wi-Fi, VPNs, and AP isolation can block phone-to-desktop HTTP even when both devices are on the same subnet. The 8-digit key expires after 5 minutes and is invalidated after successful pairing or too many failed attempts.

Google Play builds can embed the official relay URL so users only see the secure relay option plus the numeric key:

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

When a user sends a normal message to a team row, the bridge lets selected members answer in order, so later members see the earlier group replies.

Google Play readiness notes live in `docs/google-play-readiness.md`.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
