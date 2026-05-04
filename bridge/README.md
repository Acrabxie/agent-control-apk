# Agent Control Bridge

Local desktop daemon for the Agent Control Android APK.

## Run

The current Mac has a LaunchAgent installed at:

```text
/Users/xiehaibo/Library/LaunchAgents/com.xiehaibo.agentcontrol.bridge.plist
```

It runs:

```bash
AGENT_CONTROL_PORT=7149 node /Users/xiehaibo/Code/agent-control-apk/bridge/server.mjs
```

Health check:

```bash
curl http://127.0.0.1:7149/v1/health
```

Phone pairing URL:

```text
http://<desktop-lan-ip>:7149
https://<agent-control-relay>.workers.dev
```

Desktop pairing page:

```text
http://127.0.0.1:7149
```

## Security

The bridge generates a short-lived 8-digit key and exposes a public pairing challenge at `GET /v1/pairing-challenge`. The Android app proves knowledge of the key with HMAC-SHA256; the key is never sent in the pair request. After proof validation, pairing uses ECDH P-256 and all client requests and server responses use HKDF-SHA256 derived AES-256-GCM payload encryption. The desktop public-key fingerprint is pinned by the Android session.

For Google Play and out-of-home control, deploy `../relay` and start this bridge with `AGENT_CONTROL_RELAY_URL=https://...workers.dev`. The bridge registers the current 8-digit key with the relay and polls for phone requests, so no inbound desktop port is required. LAN direct mode is only a local fallback for networks that allow phone-to-desktop HTTP.

## Persistent Subagents

The bridge stores dynamic subagents at:

```text
~/.agents/shared-agent-loop/agent-control-subagents.json
```

Manual creation:

```text
/spawn Name
```

Agent self-creation protocol:

```text
AGENT_CONTROL_CREATE_SUBAGENT {"name":"ReleaseGuard","role":"release checklist monitor","tools":["direct-chat","report"]}
```

The directive must be one standalone line in the agent reply. The bridge strips it from visible chat, creates or reuses the persistent child, broadcasts `agent.spawned` and `team.changed`, and includes the child in future `/v1/snapshot` responses. Messages sent to a subagent are routed through its root parent adapter with subagent context injected.

## Team Group Chats

The bridge stores dynamic teams at:

```text
~/.agents/shared-agent-loop/agent-control-teams.json
```

Manual creation:

```text
/team-create MobileOps
```

Agent self-creation protocol:

```text
AGENT_CONTROL_CREATE_TEAM {"name":"MobileOps","purpose":"mobile release coordination","members":["codex","claude","gemini_cli"],"sharedProfile":"release room context","sharedDocuments":["MEMORY.md","QUEUE.md"]}
```

Agent-to-team side message protocol:

```text
AGENT_CONTROL_TEAM_MESSAGE {"teamId":"team-mobileops","text":"ReleaseGuard should watch the next build."}
```

The bridge hides these directive lines from visible chat, persists teams, broadcasts `team.created` and `team.changed`, and returns teams in every `/v1/snapshot`. Messages sent to a team row are routed as group chat rounds: selected members reply one after another with team profile, shared material, and recent group history injected.

## Target Agent Routing

`POST /v1/messages` payloads include `targetAgentId` which the bridge routes to the matching adapter:

| targetAgentId | Agent | Adapter |
|---|---|---|
| `codex` | Codex | `codex` CLI (default) |
| `claude` | Claude Code | `claude` CLI |
| `gemini_cli` | Gemini CLI | `gemini` CLI |
| `antigravity` | Antigravity | `antigravity`/`openclaw` CLI |
| `opencode` | OpenCode | `opencode run --model deepseek/deepseek-v4-pro --format default` |
| team-* | Persistent team | Group chat round-robin |

Fallback: unknown `targetAgentId` defaults to `codex`.

## API

- `GET /v1/pairing-challenge`
- `POST /v1/pair`
- `GET /v1/snapshot`
- `GET /v1/stream`
- `POST /v1/messages`
- `POST /v1/files`
- `PATCH /v1/projects/{projectId}/documents/{documentId}`
- `GET /v1/slash-commands`
