# Play Console Submission Copy

Use this file as copy/paste material for Google Play Console. Keep the wording truthful for the actual release build.

## App Identity

- App name: `Agent Control`
- Package name: `com.acrab.agentcontrol`
- Default language: English (United States)
- App type: App
- Category: Tools
- Tags: Developer tools, Productivity, Remote control
- Contains ads: No
- App is a news app: No
- Government app: No
- Health app: No
- Financial features: No
- Account creation: No app account system

## Short Description

```text
Control local coding agents from your phone.
```

## Full Description

```text
Agent Control is a companion app for developers who run coding agents on their own computer.

Pair your phone with the desktop bridge, then send messages to local agents such as Codex, Claude Code, Gemini CLI, OpenCode, and compatible adapters. Agent Control shows each agent as a conversation, supports subagents and team chats, and displays running status such as thinking, reading, editing, testing, and replying.

Features:
- Pair by QR code or 8-digit numeric key
- Control local desktop coding agents from Android
- Separate conversations for agents, subagents, and teams
- Per-agent model and reasoning controls when the desktop adapter supports them
- Plan mode and permission mode controls
- File and photo sending to the paired desktop bridge
- Setup diagnostics and copyable tester report
- Agent/team detail pages with runtime controls and recent status
- Slash commands typed directly in the composer
- Direct/VPN, self-hosted relay, and optional managed relay connection modes
- App-private pairing and conversation persistence

Agent Control is not a standalone chatbot. It requires the desktop bridge from the Agent Control GitHub repository. Remote access requires either a reachable direct/VPN desktop address, a self-hosted HTTPS relay, or a managed relay build.

The app has no ads, no analytics SDK, and no account system.
```

## Internal Testing Release Notes

```text
Agent Control 0.3.47 internal testing build.

- Pair Android with the desktop bridge by QR code or 8-digit key
- Control local agents including Codex, Claude Code, Gemini CLI, OpenCode, and compatible adapters
- Show agent, subagent, and team conversations
- Show running status such as thinking, reading, editing, testing, and replying
- Support per-agent model/reasoning controls, Plan mode, file upload, and photo sending
- Add Setup diagnostics, tester checklist/report copy, and agent/team detail pages
- Keep first-install setup, privacy policy entry points, and Google Play release signing
```

## App Access

Recommended answer:

```text
No login credentials are required. The app has no account system.

Agent Control is a companion app. Full live testing requires a desktop bridge from:
https://github.com/Acrabxie/agent-control-apk

Reviewer steps:
1. Install and run the desktop bridge from the repository.
2. Open http://127.0.0.1:7149 on the desktop.
3. On Android, open Pair, scan the QR code or enter the bridge/relay address and 8-digit key.
4. Open Setup, run diagnostics, then send /status to a local agent.

If the reviewer does not install the desktop bridge, they can still inspect onboarding, pairing UI, privacy policy entry points, and app navigation. Agent replies require a paired desktop bridge.
```

If Play Console asks whether all app functionality is available without special access, use the no-account path and provide the above instructions. Do not invent reviewer credentials.

## Privacy Policy

Use this URL after the file is pushed to GitHub:

```text
https://github.com/Acrabxie/agent-control-apk/blob/main/docs/privacy-policy.md
```

## Data Safety

Internal testing-only apps may not need a completed public Data safety section, but prepare this before closed testing or production.

### Collection And Security Overview

- Does the app collect or share user data? Yes
- Is all collected user data encrypted in transit? Yes, after pairing. Direct/VPN HTTP is user-entered local or VPN transport; post-pairing payloads use app-level AES-GCM encryption.
- Can users request deletion? Yes. Users can remove paired desktop state in the app, clear local Android app data, start new conversations, and contact the developer for managed-relay deletion questions.

### Data Types To Declare

Declare only the data types that match the release build and Play Console's current choices:

- App activity: in-app interactions, app-generated conversation actions, slash commands, and user-generated message content.
- Files and docs: selected files sent by the user to the paired desktop.
- Photos and videos: selected photos or camera images sent by the user to the paired desktop.
- Device or other IDs: local device id, desktop id, request/session identifiers, and desktop public-key fingerprint used for pairing/routing.
- App info and performance: only if you add crash reporting, analytics, diagnostics, or managed relay logging beyond basic routing metadata. Current Android app has no analytics SDK.

Do not declare location, contacts, calendar, SMS, call logs, health, financial info, or installed apps unless a future build adds those features.

### Purpose

For the selected data types, choose:

- App functionality
- Security, fraud prevention, and compliance, where available, for pairing/session identity and encryption metadata

Do not choose advertising, marketing, analytics, or personalization unless a future build adds those features.

### Required Or Optional

- Messages and pairing metadata are required for core paired-agent functionality.
- Photos/files are optional and only sent when the user explicitly attaches them.
- Voice input text is optional and only created when the user starts device speech recognition.

### Sharing Notes

Agent Control sends data to the paired desktop bridge or a user-configured relay. In a self-hosted relay setup, the relay is controlled by the user. In a managed relay build, routing is operated by the app developer and must be reflected in the privacy policy and Data safety form.

Use Play Console's current definitions when deciding whether the configured relay counts as "sharing." If unsure for a managed relay release, choose the conservative disclosure.

## Content Rating Notes

Likely answers for the current build:

- No violence or graphic content
- No sexual content
- No gambling
- No drugs, alcohol, or tobacco
- No user-to-user public sharing or public social feed
- No location sharing
- No purchases
- Internet access is used for paired desktop/relay communication

Because users can type arbitrary messages to their own local agents, do not describe the app as moderated public social content. It is a private developer companion tool.

## Target Audience

Recommended target audience:

```text
18 and over
```

Rationale:

```text
Agent Control is a developer tool for controlling local coding agents and desktop automation. It is not designed for children.
```

## Store Listing Asset Notes

Minimum next assets to prepare:

- Phone screenshots showing onboarding, pairing, chat list, agent chat, composer actions, and progress/status output
- Feature graphic
- Optional demo video only after the desktop bridge setup is stable enough for public demonstration

Avoid screenshots that reveal private file paths, tokens, account emails, relay secrets, private conversations, or unreleased third-party credentials.
