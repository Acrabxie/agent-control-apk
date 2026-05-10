# Internal Testing Guide

Use this guide for Google Play internal testing and later closed testing.

## Tester Prerequisites

- Android phone with the Agent Control test build installed from Google Play.
- A desktop computer with a supported local coding agent, such as Codex, Claude Code, Gemini CLI, OpenCode, or a compatible adapter.
- The Agent Control repository:
  `https://github.com/Acrabxie/agent-control-apk`
- For remote access outside the same network, either a user-managed VPN/direct route or a self-hosted HTTPS relay.

## Desktop Setup

Ask the tester's coding agent or terminal to run:

```text
Go to https://github.com/Acrabxie/agent-control-apk, read docs/self-hosted-relay.md, install/start the desktop bridge, deploy a self-hosted relay if remote access is needed, then open http://127.0.0.1:7149 for pairing. Do not commit or store Cloudflare tokens or secrets.
```

For local direct testing only:

```bash
cd /path/to/agent-control-apk
AGENT_CONTROL_PORT=7149 node bridge/server.mjs
```

Open on the desktop:

```text
http://127.0.0.1:7149
```

## Android Pairing Test

1. Install the Google Play internal testing build.
2. Open Agent Control.
3. Read the two-page setup flow.
4. Open the `Setup` tab and tap `Start pairing`.
5. Scan the QR code from the desktop page, or enter the relay/direct address and 8-digit key.
6. Confirm the app shows paired/encrypted state.

Expected result:

- Pairing succeeds with a fresh 8-digit key.
- The app pins the desktop fingerprint.
- Closing and reopening the app keeps the paired state.

## Setup Diagnostics Test

1. Open the `Setup` tab.
2. Tap `Run diagnostics`.
3. Tap `Send /status`.
4. Tap `Copy report`.

Expected result:

- The checklist shows bridge install/reachability, pairing, diagnostics, `/status`, attachment, and reconnect checks.
- Diagnostics never show tokens, passwords, relay secrets, raw prompts, or decrypted payloads.
- The copied tester report includes package/version, connection mode, diagnostic summary, and redacted failure text.

## Basic Message Test

1. Open the Codex or another available agent row.
2. Send:

```text
/status
```

3. Send a normal text message:

```text
Reply with one short sentence confirming Agent Control is connected.
```

Expected result:

- The user message appears immediately.
- The composer can show `Sending...` while waiting.
- The agent row shows thinking or progress.
- A final reply appears in the conversation.

## Progress Hook Test

Send a small safe request:

```text
List the files in the current test project, but do not edit anything.
```

Expected result:

- The chat shows natural running status such as thinking, reading, running, or replying.
- The final bubble shows only the user-facing reply text.

## New Conversation Test

1. In an agent chat, tap the new conversation button.
2. Send:

```text
What do you remember from the previous thread?
```

Expected result:

- The new thread should not blindly include the previous conversation's local app context.
- Shared agent memory may still be read if the desktop bridge is configured to provide it.

## Attachment Test

1. Open the composer plus menu.
2. Attach a small text file or photo.
3. Send a short request asking the agent to acknowledge the attachment.

Expected result:

- The attachment is listed before sending.
- The message sends to the desktop bridge.
- The agent receives attachment metadata/content if the desktop adapter supports it.

## Reconnect Test

1. Force-close the Android app.
2. Reopen Agent Control.
3. Send `/status`.

Expected result:

- Pairing state persists.
- The app can reconnect to the paired desktop or relay without re-pairing, unless the desktop bridge state has been reset.

## Failure Cases To Report

Ask testers to report:

- Pairing fails with a fresh QR/key.
- Message remains in `Sending...` forever.
- Chat shows received/pending state but no final output.
- Agent replies are routed to the wrong row.
- Model/reasoning options do not match the selected agent.
- File/photo sending fails.
- The app exposes secrets, local passwords, or private tokens in UI or logs.

## Feedback Template

```text
Device:
Android version:
Agent Control version:
Connection mode: Direct/VPN / self-hosted relay / managed relay
Desktop OS:
Agent adapter: Codex / Claude Code / Gemini CLI / OpenCode / other
Setup report:
What happened:
Expected:
Screenshot/log if available:
```
