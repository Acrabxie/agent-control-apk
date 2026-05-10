import { createCipheriv, createDecipheriv, createHash, createHmac, createPrivateKey, createPublicKey, diffieHellman, generateKeyPairSync, hkdfSync, randomBytes, randomInt, timingSafeEqual } from "node:crypto";
import { createServer } from "node:http";
import { execFile, spawn } from "node:child_process";
import { chmod, mkdir, readFile, unlink, writeFile } from "node:fs/promises";
import { existsSync, readFileSync } from "node:fs";
import { basename, dirname, join } from "node:path";
import { homedir, hostname, networkInterfaces } from "node:os";
import { fileURLToPath } from "node:url";
import { ProxyAgent } from "undici";
import QRCode from "qrcode";

const ROOT = process.env.AGENT_CONTROL_ROOT || join(homedir(), ".agents", "shared-agent-loop");
const UPLOAD_ROOT = join(ROOT, "uploads");
const AGENT_ROSTER_FILE = join(ROOT, "agent-control-subagents.json");
const TEAM_ROSTER_FILE = join(ROOT, "agent-control-teams.json");
const PRIVATE_STATE_DIR = process.env.AGENT_CONTROL_PRIVATE_STATE_DIR || join(homedir(), "Library", "Application Support", "Agent Control");
const BRIDGE_STATE_FILE = join(PRIVATE_STATE_DIR, "bridge-state.json");
const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(SCRIPT_DIR, "..");
const PORT = Number(process.env.AGENT_CONTROL_PORT || "7149");
const RELAY_URL = normalizeUrl(process.env.AGENT_CONTROL_RELAY_URL || "");
const RELAY_PROXY_URL = relayProxyUrl();
const RELAY_DISPATCHER = RELAY_PROXY_URL ? new ProxyAgent(RELAY_PROXY_URL) : null;
const RELAY_ALLOW_DIRECT_FALLBACK = process.env.AGENT_CONTROL_RELAY_ALLOW_DIRECT_FALLBACK === "1";
const VERSION = "agent-control.v1";
const PAIRING_TTL_MS = 5 * 60 * 1000;
const PAIRING_MAX_ATTEMPTS = 5;
const RELAY_POLL_MS = 750;
const RELAY_POLL_TIMEOUT_MS = 35_000;
const RELAY_OFFER_REFRESH_MS = 30_000;
const RELAY_FETCH_ATTEMPTS = 3;
const RELAY_FETCH_TIMEOUT_MS = 8_000;
const RELAY_CURL_TIMEOUT_SECONDS = 12;
const DISABLE_REAL_AGENTS = process.env.AGENT_CONTROL_DISABLE_REAL_AGENTS === "1";
const MAX_IN_MEMORY_MESSAGES = 800;
const MAX_PERSISTED_MESSAGES = 500;
const MAX_PROMPT_MEMORY_MESSAGES = 24;
const MAX_PROMPT_MEMORY_CHARS = 1200;
const MAX_SHARED_FILE_PROMPT_CHARS = 3200;
const BRIDGE_ONLINE_MESSAGE = "Desktop bridge online. Pair the phone, then stream encrypted agent events.";
const CODEX_DEFAULT_MODEL = "gpt-5.5";
const CODEX_MODEL_OPTIONS = [
  { id: "gpt-5.5", label: "gpt-5.5", contextLimitTokens: 400000 },
  { id: "gpt-5.4", label: "gpt-5.4", contextLimitTokens: 400000 },
  { id: "gpt-5.3-codex", label: "gpt-5.3-codex", contextLimitTokens: 200000 },
  { id: "gpt-5.2", label: "gpt-5.2", contextLimitTokens: 200000 },
];
const CODEX_REASONING_OPTIONS = [
  { id: "low", label: "Low" },
  { id: "medium", label: "Medium" },
  { id: "high", label: "High" },
  { id: "xhigh", label: "Extra High" },
];
const CODEX_PERMISSION_OPTIONS = [
  { id: "read-only", label: "Read Only", sandbox: "read-only", approval: "never" },
  { id: "workspace-write", label: "Workspace Write", sandbox: "workspace-write", approval: "never" },
  { id: "full-access", label: "Full Access", sandbox: "danger-full-access", approval: "never" },
];
const CLAUDE_DEFAULT_MODEL = "claude-sonnet-4-6";
const CLAUDE_MODEL_OPTIONS = [
  { id: "claude-sonnet-4-6", label: "Claude Sonnet 4.6", contextLimitTokens: 200000 },
  { id: "claude-opus-4-6", label: "Claude Opus 4.6", contextLimitTokens: 200000 },
  { id: "sonnet", label: "sonnet", contextLimitTokens: 200000 },
  { id: "opus", label: "opus", contextLimitTokens: 200000 },
];
const CLAUDE_REASONING_OPTIONS = [
  { id: "low", label: "Low" },
  { id: "medium", label: "Medium" },
  { id: "high", label: "High" },
  { id: "xhigh", label: "Extra High" },
  { id: "max", label: "Max" },
];
const GEMINI_DEFAULT_MODEL = "gemini-2.5-flash";
const GEMINI_MODEL_OPTIONS = [
  { id: "gemini-2.5-flash", label: "Gemini 2.5 Flash", contextLimitTokens: 1048576 },
  { id: "gemini-2.5-pro", label: "Gemini 2.5 Pro", contextLimitTokens: 1048576 },
  { id: "gemini-3-flash-preview", label: "Gemini 3 Flash", contextLimitTokens: 1048576 },
  { id: "gemini-3.1-pro-preview", label: "Gemini 3.1 Pro", contextLimitTokens: 1048576 },
];
const GEMINI_REASONING_OPTIONS = [
  { id: "default", label: "Default" },
];
const ANTIGRAVITY_DEFAULT_MODEL = "openrouter/deepseek/deepseek-v3.2";
const ANTIGRAVITY_MODEL_OPTIONS = [
  { id: "openrouter/deepseek/deepseek-v3.2", label: "DeepSeek V3.2", contextLimitTokens: 160000 },
  { id: "openrouter/google/gemini-3-flash-preview", label: "Gemini 3 Flash", contextLimitTokens: 1048576 },
  { id: "openrouter/anthropic/claude-opus-4.6", label: "Claude Opus 4.6", contextLimitTokens: 200000 },
  { id: "openrouter/google/gemini-3.1-pro-preview", label: "Gemini 3.1 Pro", contextLimitTokens: 1048576 },
  { id: "openai/gpt-5.4", label: "GPT 5.4", contextLimitTokens: 266000 },
  { id: "openai-codex/gpt-5.4", label: "Codex GPT 5.4", contextLimitTokens: 266000 },
];
const ANTIGRAVITY_REASONING_OPTIONS = [
  { id: "off", label: "Off" },
  { id: "minimal", label: "Minimal" },
  { id: "low", label: "Low" },
  { id: "medium", label: "Medium" },
  { id: "high", label: "High" },
  { id: "xhigh", label: "Extra High" },
];
const OPENCODE_DEFAULT_MODEL = "deepseek/deepseek-v4-pro";
const OPENCODE_MODEL_OPTIONS = [
  { id: "deepseek/deepseek-v4-pro", label: "DeepSeek V4-Pro", contextLimitTokens: 128000 },
  { id: "openrouter/deepseek/deepseek-v3.2", label: "DeepSeek V3.2", contextLimitTokens: 160000 },
  { id: "openrouter/google/gemini-3-flash-preview", label: "Gemini 3 Flash", contextLimitTokens: 1048576 },
  { id: "openrouter/anthropic/claude-opus-4.6", label: "Claude Opus 4.6", contextLimitTokens: 200000 },
];
const OPENCODE_REASONING_OPTIONS = [
  { id: "default", label: "Default" },
  { id: "minimal", label: "Minimal" },
  { id: "low", label: "Low" },
  { id: "medium", label: "Medium" },
  { id: "high", label: "High" },
  { id: "max", label: "Max" },
];

const persistedBridgeState = loadPrivateBridgeState();
const livePair = loadOrCreateLivePair(persistedBridgeState);
const livePublicDer = livePair.publicKey.export({ type: "spki", format: "der" });
const livePrivatePem = livePair.privateKey.export({ type: "pkcs8", format: "pem" });
const desktopPublicKey = base64UrlEncode(livePublicDer);
const desktopFingerprint = fingerprint(livePublicDer);
const sessions = new Map();
const pairingChallenges = new Map();
const clients = new Set();
let activePairingKey = createPairingKey();
let relayDesktopId = persistedBridgeState.relayDesktopId || `desktop-${id().slice(0, 12)}`;
let relaySecret = persistedBridgeState.relaySecret || base64UrlEncode(randomBytes(32));
let relayPolling = false;
let relayOfferRegisteredKeyId = "";
let relayOfferRegisteredAt = 0;
const relayResponseCache = new Map();
let claudeDirectDisabledUntil = 0;
let codexRuntimeSettings = normalizeCodexRuntimeSettings(persistedBridgeState.codexRuntimeSettings || {});
let privateStateSaveTimer = null;
const SUBAGENT_DIRECTIVE = "AGENT_CONTROL_CREATE_SUBAGENT";
const REMOVE_SUBAGENT_DIRECTIVE = "AGENT_CONTROL_REMOVE_SUBAGENT";
const TEAM_DIRECTIVE = "AGENT_CONTROL_CREATE_TEAM";
const TEAM_MESSAGE_DIRECTIVE = "AGENT_CONTROL_TEAM_MESSAGE";
const FILE_DIRECTIVE = "AGENT_CONTROL_SEND_FILE";
const TEAM_ROUND_MAX_SPEAKERS = 2;
const MAX_INLINE_AGENT_FILE_BYTES = 5 * 1024 * 1024;

const commands = [
  ["/status", "Status", "team"],
  ["/agents", "Agents", "team"],
  ["/spawn", "Spawn", "selected"],
  ["/dismiss", "Dismiss subagent", "selected"],
  ["/team", "Team", "team"],
  ["/team-create", "New team", "team"],
  ["/start", "Start", "chat"],
  ["/commands", "Commands", "chat"],
  ["/new", "New chat", "chat"],
  ["/model", "Model", "selected"],
  ["/reasoning", "Reasoning", "selected"],
  ["/permissions", "Permissions", "selected"],
  ["/context", "Context", "selected"],
  ["/compact", "Compact", "selected"],
  ["/diagnostics", "Diagnostics", "team"],
  ["/parent", "Parent", "selected"],
  ["/memory", "Memory", "project"],
  ["/heartbeat", "Heartbeat", "project"],
  ["/files", "Files", "transfer"],
  ["/photo", "Photo", "transfer"],
  ["/tools", "Tools", "selected"],
  ["/handoff", "Handoff", "team"],
  ["/approve", "Approve", "selected"],
  ["/pause", "Pause", "selected"],
  ["/resume", "Resume", "selected"],
  ["/stop", "Stop", "selected"],
  ["/clear", "Clear", "chat"],
  ["/api", "API", "project"],
  ["/help", "Help", "chat"],
].map(([trigger, label, target]) => ({ trigger, label, target }));

const state = {
  messages: [],
  transfers: [],
  dynamicAgents: [],
  dynamicTeams: [],
  heartbeats: [],
};
restorePersistedSessions(persistedBridgeState.sessions);
restorePersistedMessages(persistedBridgeState.messages);
savePrivateBridgeState().catch((error) => console.error(`failed to save bridge state: ${error.message}`));

const agentDefinitions = [
  {
    id: "codex",
    name: "Codex",
    kind: "CODEX",
    role: "controller, planner, integrator",
    status: "ONLINE",
    parentId: null,
    teamId: "core",
    tools: ["shell", "patch", "browser", "notion", "android-build", "bridge"],
    modelOptions: CODEX_MODEL_OPTIONS,
    reasoningOptions: CODEX_REASONING_OPTIONS,
    permissionOptions: CODEX_PERMISSION_OPTIONS,
    slashCommands: [
      { trigger: "/plan", label: "Plan", target: "selected" },
      { trigger: "/diff", label: "Diff", target: "selected" },
      { trigger: "/review", label: "Review", target: "selected" },
      { trigger: "/test", label: "Test", target: "selected" },
      { trigger: "/commit", label: "Commit", target: "selected" },
    ],
    canSpawnChildren: true,
  },
  {
    id: "claude",
    name: "Claude Code",
    kind: "CLAUDE_CODE",
    role: "deep implementation and repo surgery",
    status: commandExists("claude") ? "ONLINE" : "IDLE",
    parentId: null,
    teamId: "core",
    tools: ["repo-read", "edit", "test", "review"],
    modelOptions: CLAUDE_MODEL_OPTIONS,
    reasoningOptions: CLAUDE_REASONING_OPTIONS,
    permissionOptions: CODEX_PERMISSION_OPTIONS,
    slashCommands: [
      { trigger: "/plan", label: "Plan", target: "selected" },
      { trigger: "/edit", label: "Edit", target: "selected" },
      { trigger: "/diff", label: "Diff", target: "selected" },
      { trigger: "/review", label: "Review", target: "selected" },
      { trigger: "/login", label: "Login status", target: "selected" },
    ],
    canSpawnChildren: true,
  },
  {
    id: "antigravity",
    name: "Antigravity",
    kind: "ANTIGRAVITY",
    role: "independent UI/product review",
    status: antigravityCommand() ? "ONLINE" : "IDLE",
    parentId: null,
    teamId: "core",
    tools: ["browser", "manual-check", "visual-review"],
    modelOptions: ANTIGRAVITY_MODEL_OPTIONS,
    reasoningOptions: ANTIGRAVITY_REASONING_OPTIONS,
    permissionOptions: CODEX_PERMISSION_OPTIONS,
    slashCommands: [
      { trigger: "/review", label: "Review", target: "selected" },
      { trigger: "/screenshot", label: "Screenshot", target: "selected" },
      { trigger: "/issues", label: "Issues", target: "selected" },
    ],
    canSpawnChildren: true,
  },
  {
    id: "gemini_cli",
    name: "Gemini CLI",
    kind: "GEMINI_CLI",
    role: "official Gemini API-key agent lane",
    status: commandExists("gemini") ? "ONLINE" : "IDLE",
    parentId: null,
    teamId: "core",
    tools: ["gemini", "planning", "analysis", "review"],
    modelOptions: GEMINI_MODEL_OPTIONS,
    reasoningOptions: GEMINI_REASONING_OPTIONS,
    permissionOptions: CODEX_PERMISSION_OPTIONS,
    slashCommands: [
      { trigger: "/ask", label: "Ask", target: "selected" },
      { trigger: "/plan", label: "Plan", target: "selected" },
      { trigger: "/research", label: "Research", target: "selected" },
      { trigger: "/review", label: "Review", target: "selected" },
    ],
    canSpawnChildren: true,
  },
  {
    id: "opencode",
    name: "OpenCode",
    kind: "OPENCODE",
    role: "OpenCode CLI using DeepSeek V4-Pro",
    status: commandExists("opencode") ? "ONLINE" : "IDLE",
    parentId: null,
    teamId: "core",
    tools: ["opencode", "deepseek-v4-pro", "coding", "review"],
    modelOptions: OPENCODE_MODEL_OPTIONS,
    reasoningOptions: OPENCODE_REASONING_OPTIONS,
    permissionOptions: CODEX_PERMISSION_OPTIONS,
    slashCommands: [
      { trigger: "/run", label: "Run", target: "selected" },
      { trigger: "/edit", label: "Edit", target: "selected" },
      { trigger: "/test", label: "Test", target: "selected" },
      { trigger: "/review", label: "Review", target: "selected" },
    ],
    canSpawnChildren: true,
  },
];

await loadPersistedAgents();
await loadPersistedTeams();
seedMessages();
schedulePrivateBridgeStateSave();

const server = createServer(async (req, res) => {
  try {
    setCors(res);
    if (req.method === "OPTIONS") return sendJson(res, 204, {});

    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    if (url.pathname !== "/v1/health") {
      console.log(`${new Date().toISOString()} ${req.method} ${url.pathname} from ${req.socket.remoteAddress || "unknown"}`);
    }
    if (req.method === "GET" && url.pathname === "/") {
      return sendHtml(res, await pairingPage(req));
    }

    if (req.method === "GET" && url.pathname === "/v1/health") {
      return sendJson(res, 200, {
        ok: true,
        version: VERSION,
        desktopName: hostname(),
        addresses: localAddresses(),
        pairedDevices: sessions.size,
        pairing: publicPairingState(),
      });
    }

    if (req.method === "GET" && url.pathname === "/v1/pairing-challenge") {
      return sendJson(res, 200, createPairingChallenge());
    }

    if (req.method === "POST" && url.pathname === "/v1/pair") {
      return handlePair(req, res);
    }

    if (req.method === "GET" && url.pathname === "/v1/snapshot") {
      const session = requireSession(req, res);
      if (!session) return;
      return sendEncrypted(res, session, "snapshot", await snapshot());
    }

    if (req.method === "GET" && url.pathname === "/v1/diagnostics") {
      const session = requireSession(req, res);
      if (!session) return;
      return sendEncrypted(res, session, "diagnostics", await diagnostics(session, String(req.headers["x-device-id"] || "")));
    }

    if (req.method === "GET" && url.pathname === "/v1/slash-commands") {
      const session = requireSession(req, res);
      if (!session) return;
      return sendEncrypted(res, session, "slash.commands", allSlashCommands());
    }

    if (req.method === "GET" && url.pathname === "/v1/stream") {
      const session = requireSession(req, res);
      if (!session) return;
      return handleStream(req, res, session);
    }

    if (req.method === "POST" && url.pathname === "/v1/messages") {
      const session = requireSession(req, res);
      if (!session) return;
      const body = await readJson(req);
      const result = await performMessage(session, body);
      return sendJson(res, result.status, result.body);
    }

    if (req.method === "POST" && url.pathname === "/v1/files") {
      const session = requireSession(req, res);
      if (!session) return;
      const body = await readJson(req);
      const payload = decryptJson(body, session.key);
      const transfer = await saveUpload(payload);
      broadcast("file.uploaded", transfer);
      return sendEncrypted(res, session, "file.uploaded", transfer);
    }

    const projectPatch = url.pathname.match(/^\/v1\/projects\/([^/]+)\/documents\/([^/]+)$/);
    if (req.method === "PATCH" && projectPatch) {
      const session = requireSession(req, res);
      if (!session) return;
      const body = await readJson(req);
      const payload = decryptJson(body, session.key);
      const document = await writeProjectDocument(projectPatch[2], payload.content || "");
      broadcast("project.document.changed", document);
      return sendEncrypted(res, session, "project.document.changed", document);
    }

    return sendJson(res, 404, { error: "not_found" });
  } catch (error) {
    console.error(error);
    return sendJson(res, 500, { error: "bridge_error", detail: error.message });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`Agent Control bridge listening on http://0.0.0.0:${PORT}`);
  console.log(`Open http://127.0.0.1:${PORT} on this desktop to view the pairing key.`);
  console.log(`Desktop fingerprint: ${desktopFingerprint}`);
  console.log(`LAN URLs: ${localAddresses().map((address) => `http://${address}:${PORT}`).join(", ")}`);
  if (RELAY_URL) {
    console.log(`Remote relay enabled: ${RELAY_URL}`);
    registerRelayOffer({ force: true }).catch((error) => console.error("relay register failed", error));
    startRelayLoop();
  }
});

async function handlePair(req, res) {
  const body = await readJson(req);
  const result = await performPair(body);
  return sendJson(res, result.status, result.body);
}

async function performPair(body) {
  const challenge = pairingChallenges.get(String(body.sessionId || ""));
  const now = Date.now();

  if (!challenge) {
    return { status: 404, body: { accepted: false, error: "pairing_challenge_not_found" } };
  }
  if (challenge.used) {
    return { status: 409, body: { accepted: false, error: "pairing_challenge_used" } };
  }
  if (challenge.expiresAt <= now) {
    pairingChallenges.delete(challenge.sessionId);
    return { status: 410, body: { accepted: false, error: "pairing_challenge_expired" } };
  }
  if (challenge.attempts >= PAIRING_MAX_ATTEMPTS) {
    return { status: 429, body: { accepted: false, error: "pairing_challenge_locked" } };
  }

  const devicePublicKey = String(body.devicePublicKey || "");
  const expectedProof = pairingProof({
    pairingKey: challenge.pairingKey,
    sessionId: challenge.sessionId,
    nonce: challenge.nonce,
    devicePublicKey,
    desktopPublicKey,
  });
  if (!constantTimeEquals(String(body.pairingProof || ""), expectedProof)) {
    challenge.attempts += 1;
    if (challenge.attempts >= PAIRING_MAX_ATTEMPTS) {
      invalidatePairingKeyChallenges(challenge.pairingKeyId);
      activePairingKey = createPairingKey();
      relayOfferRegisteredKeyId = "";
      logPairingKey("Pairing key rotated after too many failed attempts");
      await registerRelayOffer({ force: true });
      return { status: 429, body: { accepted: false, error: "pairing_challenge_locked" } };
    }
    return { status: 403, body: { accepted: false, error: "bad_pairing_proof", attemptsRemaining: PAIRING_MAX_ATTEMPTS - challenge.attempts } };
  }

  challenge.used = true;
  pairingChallenges.delete(challenge.sessionId);
  if (activePairingKey.id === challenge.pairingKeyId) {
    invalidatePairingKeyChallenges(challenge.pairingKeyId);
    activePairingKey = createPairingKey();
    relayOfferRegisteredKeyId = "";
    logPairingKey("Pairing key rotated after successful pair");
    await registerRelayOffer({ force: true });
  }

  const devicePublicDer = base64UrlDecode(devicePublicKey);
  const sharedSecret = diffieHellman({
    privateKey: livePair.privateKey,
    publicKey: createPublicKeyFromDer(devicePublicDer),
  });
  const key = deriveSessionKey(sharedSecret, livePublicDer, devicePublicDer);
  const deviceId = fingerprint(devicePublicDer);
  const previousSession = sessions.get(deviceId);
  sessions.set(deviceId, {
    key,
    oldKeys: previousSession ? [previousSession.key, ...(previousSession.oldKeys || [])].slice(0, 2) : [],
    deviceName: body.deviceName || "Android",
    devicePublicKey,
    desktopFingerprint,
    pairedAt: now,
  });
  savePrivateBridgeState().catch((error) => console.error(`failed to save bridge state: ${error.message}`));
  relayOfferRegisteredKeyId = "";
  await registerRelayOffer({ force: true });

  heartbeat("security", `Paired ${body.deviceName || "Android"} as ${deviceId}`);
  return {
    status: 200,
    body: {
      desktopPublicKey,
      desktopName: hostname(),
      desktopFingerprint,
      accepted: true,
      deviceId,
      pairedAt: now,
      snapshot: await snapshot(),
    },
  };
}

function createPublicKeyFromDer(der) {
  return createPublicKey({ key: der, type: "spki", format: "der" });
}

function createPairingChallenge() {
  prunePairingChallenges();
  const key = ensureActivePairingKey();
  const sessionId = id();
  const nonce = base64UrlEncode(randomBytes(24));
  const expiresAt = Math.min(key.expiresAt, Date.now() + PAIRING_TTL_MS);
  pairingChallenges.set(sessionId, {
    sessionId,
    nonce,
    expiresAt,
    attempts: 0,
    used: false,
    pairingKey: key.key,
    pairingKeyId: key.id,
  });
  return {
    sessionId,
    desktopName: hostname(),
    desktopPublicKey,
    desktopFingerprint,
    nonce,
    expiresAt,
    addresses: localAddresses(),
    port: PORT,
    cipherSuite: "ECDH-P256 + HKDF-SHA256 + AES-256-GCM",
    proof: "HMAC-SHA256",
  };
}

function publicPairingChallenge() {
  const challenge = createPairingChallenge();
  return {
    sessionId: challenge.sessionId,
    desktopName: challenge.desktopName,
    desktopPublicKey: challenge.desktopPublicKey,
    desktopFingerprint: challenge.desktopFingerprint,
    nonce: challenge.nonce,
    expiresAt: challenge.expiresAt,
    addresses: challenge.addresses,
    port: challenge.port,
    cipherSuite: challenge.cipherSuite,
    proof: challenge.proof,
  };
}

function createPairingKey() {
  return {
    id: id(),
    key: String(randomInt(0, 100000000)).padStart(8, "0"),
    createdAt: Date.now(),
    expiresAt: Date.now() + PAIRING_TTL_MS,
  };
}

function ensureActivePairingKey() {
  if (!activePairingKey || activePairingKey.expiresAt <= Date.now()) {
    activePairingKey = createPairingKey();
    relayOfferRegisteredKeyId = "";
    logPairingKey("Pairing key rotated after expiry");
  }
  return activePairingKey;
}

function prunePairingChallenges() {
  const now = Date.now();
  for (const [sessionId, challenge] of pairingChallenges.entries()) {
    if (challenge.used || challenge.expiresAt <= now) pairingChallenges.delete(sessionId);
  }
}

function invalidatePairingKeyChallenges(pairingKeyId) {
  for (const [sessionId, challenge] of pairingChallenges.entries()) {
    if (challenge.pairingKeyId === pairingKeyId) pairingChallenges.delete(sessionId);
  }
}

function pairingProof({ pairingKey, sessionId, nonce, devicePublicKey, desktopPublicKey }) {
  const message = [sessionId, nonce, devicePublicKey, desktopPublicKey].join("\n");
  return createHmac("sha256", normalizePairingKey(pairingKey)).update(message).digest("base64url");
}

function normalizePairingKey(value) {
  return String(value || "").replace(/\D/g, "");
}

function constantTimeEquals(left, right) {
  const leftBuffer = Buffer.from(String(left));
  const rightBuffer = Buffer.from(String(right));
  return leftBuffer.length === rightBuffer.length && timingSafeEqual(leftBuffer, rightBuffer);
}

function publicPairingState() {
  const key = ensureActivePairingKey();
  return {
    challengeTtlMs: PAIRING_TTL_MS,
    maxAttempts: PAIRING_MAX_ATTEMPTS,
    keyExpiresAt: key.expiresAt,
    pendingChallenges: pairingChallenges.size,
    desktopFingerprint,
  };
}

function logPairingKey(prefix) {
  console.log(`${prefix}. Open http://127.0.0.1:${PORT} on this desktop to view the current key.`);
}

function formatPairingKey(key) {
  return `${key.slice(0, 4)} ${key.slice(4)}`;
}

async function pairingPage(req) {
  const key = ensureActivePairingKey();
  if (RELAY_URL && relayOfferRegisteredKeyId !== key.id) {
    registerRelayOffer({ force: true }).catch((error) => console.error("relay register failed", error));
  }
  const loopback = isLoopback(req.socket.remoteAddress);
  const address = localAddresses()[0] || "127.0.0.1";
  const phoneAddress = RELAY_URL || `http://${address}:${PORT}`;
  const addressLabel = RELAY_URL ? "Self-hosted relay address" : "Direct phone address";
  const deepLink = pairingDeepLink({ phoneAddress, pairingKey: key.key });
  const qrSvg = loopback ? await pairingQrSvg(deepLink) : "";
  const keyMarkup = loopback
    ? `<div class="qr">${qrSvg}</div><div class="key">${escapeHtml(formatPairingKey(key.key))}</div><p>Scan the QR code with your phone camera, or enter this 8-digit key in the Android app. It expires at ${escapeHtml(new Date(key.expiresAt).toLocaleTimeString())}.</p>`
    : "<p>Open this page on the desktop to see the pairing key.</p>";
  const modeMarkup = RELAY_URL
    ? `<p><strong>Remote mode:</strong> this bridge is using a self-hosted HTTPS relay. Use the relay address below in the Android Pair dialog, or scan the QR code.</p>
<pre>AGENT_CONTROL_RELAY_URL=${escapeHtml(RELAY_URL)} AGENT_CONTROL_PORT=${PORT} node bridge/server.mjs</pre>`
    : `<p><strong>Direct / VPN mode:</strong> use the direct phone address below only when the phone can reach this computer by LAN, Tailscale, ZeroTier, or another VPN.</p>
<p>For remote access, deploy <code>relay/</code> to the user's Cloudflare account, then restart this bridge with <code>AGENT_CONTROL_RELAY_URL=https://agent-control-relay.&lt;account&gt;.workers.dev</code>.</p>`;
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Agent Control Pairing</title>
<style>
body{margin:0;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:#101418;color:#f7fafc;display:grid;min-height:100vh;place-items:center}
main{width:min(520px,calc(100vw - 32px));background:#171d23;border:1px solid #334155;border-radius:18px;padding:28px;box-shadow:0 18px 50px rgba(0,0,0,.35)}
.lock{width:56px;height:56px;border-radius:18px;background:#22313b;display:grid;place-items:center;font-size:13px;font-weight:750;letter-spacing:.08em;margin-bottom:16px}
h1{font-size:28px;line-height:1.1;margin:0 0 8px}
p{color:#cbd5e1;line-height:1.45;margin:8px 0}
strong{color:#f7fafc}
.key{font-size:48px;font-weight:750;letter-spacing:.12em;margin:22px 0 6px;color:#7dd3fc}
.qr{width:232px;height:232px;border-radius:18px;background:#fff;padding:14px;margin:20px 0 12px;box-sizing:border-box}
.qr svg{display:block;width:100%;height:100%}
.meta{margin-top:18px;padding-top:16px;border-top:1px solid #334155;font-size:14px;color:#94a3b8}
code{color:#b5e48c}
pre{white-space:pre-wrap;word-break:break-word;background:#0f1720;border:1px solid #334155;border-radius:12px;padding:12px;color:#b5e48c;font-size:13px;line-height:1.35}
</style>
</head>
<body>
<main>
<div class="lock">LOCK</div>
<h1>Agent Control pairing</h1>
${keyMarkup}
${modeMarkup}
<div class="meta">
<div>Computer: <code>${escapeHtml(hostname())}</code></div>
<div>${escapeHtml(addressLabel)}: <code>${escapeHtml(phoneAddress)}</code></div>
${RELAY_URL ? `<div>Direct/VPN fallback: <code>http://${escapeHtml(address)}:${PORT}</code></div>` : ""}
<div>Fingerprint: <code>${escapeHtml(desktopFingerprint)}</code></div>
</div>
</main>
</body>
</html>`;
}

function pairingDeepLink({ phoneAddress, pairingKey }) {
  const params = new URLSearchParams({
    v: "1",
    url: phoneAddress,
    key: pairingKey,
    fp: desktopFingerprint,
    name: hostname(),
  });
  return `agentcontrol://pair?${params.toString()}`;
}

async function pairingQrSvg(value) {
  return QRCode.toString(value, {
    type: "svg",
    margin: 1,
    width: 204,
    color: {
      dark: "#101418",
      light: "#FFFFFF",
    },
  });
}

async function registerRelayOffer({ force = false } = {}) {
  if (!RELAY_URL) return;
  ensureActivePairingKey();
  if (!force && relayOfferRegisteredKeyId === activePairingKey.id && Date.now() - relayOfferRegisteredAt < RELAY_OFFER_REFRESH_MS) {
    return;
  }
  const challenge = publicPairingChallenge();
  const offeredKey = pairingChallenges.get(challenge.sessionId)?.pairingKey || activePairingKey.key;
  const offeredKeyId = pairingChallenges.get(challenge.sessionId)?.pairingKeyId || activePairingKey.id;
  const expiresAt = pairingChallenges.get(challenge.sessionId)?.expiresAt || activePairingKey.expiresAt;
  const response = await relayFetch(`${RELAY_URL}/v1/desktop/offer`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${relaySecret}`,
    },
    body: JSON.stringify({
      desktopId: relayDesktopId,
      pairingKey: offeredKey,
      challenge,
      expiresAt,
      pairedDeviceIds: [...sessions.keys()].slice(0, 100),
    }),
  });
  if (!response.ok) {
    const errorText = await response.text();
    if (response.status === 410) {
      invalidatePairingKeyChallenges(offeredKeyId);
      activePairingKey = createPairingKey();
      relayOfferRegisteredKeyId = "";
    }
    throw new Error(`relay offer failed: ${response.status} ${errorText}`);
  }
  relayOfferRegisteredKeyId = offeredKeyId;
  relayOfferRegisteredAt = Date.now();
}

function startRelayLoop() {
  if (relayPolling || !RELAY_URL) return;
  relayPolling = true;
  pollRelay().catch((error) => console.error("relay poll failed", error));
}

async function pollRelay() {
  while (relayPolling) {
    try {
      await registerRelayOffer();
      const response = await relayFetch(`${RELAY_URL}/v1/desktop/poll?desktopId=${encodeURIComponent(relayDesktopId)}`, {
        headers: { authorization: `Bearer ${relaySecret}` },
        timeoutMs: RELAY_POLL_TIMEOUT_MS,
      });
      if (response.ok) {
        const payload = await response.json();
        for (const job of payload.jobs || []) {
          await handleRelayJob(job);
        }
      }
    } catch (error) {
      console.error("relay poll failed", describeError(error));
    }
    await delay(RELAY_POLL_MS);
  }
}

async function handleRelayJob(job) {
  const cached = relayResponseCache.get(job.requestId);
  if (cached) {
    await postRelayResponse(job.requestId, cached.status, cached.body);
    return;
  }
  let result;
  if (job.kind === "pair") {
    result = await performPair(job.body || {});
    await cacheAndPostRelayResponse(job.requestId, result.status, result.body);
    return;
  }
  const session = sessions.get(String(job.deviceId || ""));
  if (!session) {
    await cacheAndPostRelayResponse(job.requestId, 401, { error: "not_paired" });
    return;
  }
  if (job.kind === "message") {
    result = await performMessage(session, job.body || {});
    await cacheAndPostRelayResponse(job.requestId, result.status, result.body);
    return;
  }
  if (job.kind === "snapshot") {
    await cacheAndPostRelayResponse(job.requestId, 200, encryptForSession(session, "snapshot", await snapshot()));
    return;
  }
  if (job.kind === "diagnostics") {
    await cacheAndPostRelayResponse(job.requestId, 200, encryptForSession(session, "diagnostics", await diagnostics(session, String(job.deviceId || ""))));
    return;
  }
  if (job.kind === "slashCommands") {
    await cacheAndPostRelayResponse(job.requestId, 200, encryptForSession(session, "slash.commands", allSlashCommands()));
    return;
  }
  if (job.kind === "file") {
    const payload = decryptJson(job.body || {}, session.key);
    const transfer = await saveUpload(payload);
    broadcast("file.uploaded", transfer);
    await cacheAndPostRelayResponse(job.requestId, 200, encryptForSession(session, "file.uploaded", transfer));
    return;
  }
  if (job.kind === "projectPatch") {
    const payload = decryptJson(job.body || {}, session.key);
    const document = await writeProjectDocument(String(job.documentId || ""), payload.content || "");
    broadcast("project.document.changed", document);
    await cacheAndPostRelayResponse(job.requestId, 200, encryptForSession(session, "project.document.changed", document));
    return;
  }
  await cacheAndPostRelayResponse(job.requestId, 400, { error: "unknown_relay_job" });
}

async function cacheAndPostRelayResponse(requestId, status, body) {
  rememberRelayResponse(requestId, status, body);
  await postRelayResponse(requestId, status, body);
}

function rememberRelayResponse(requestId, status, body) {
  pruneRelayResponseCache();
  relayResponseCache.set(requestId, { status, body, createdAt: Date.now() });
}

function pruneRelayResponseCache() {
  const cutoff = Date.now() - 10 * 60 * 1000;
  for (const [requestId, response] of relayResponseCache.entries()) {
    if (Number(response.createdAt || 0) < cutoff) relayResponseCache.delete(requestId);
  }
}

async function postRelayResponse(requestId, status, body) {
  const response = await relayFetch(`${RELAY_URL}/v1/desktop/respond`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${relaySecret}`,
    },
    body: JSON.stringify({ desktopId: relayDesktopId, requestId, status, body }),
  });
  if (!response.ok) throw new Error(`relay response failed: ${response.status} ${await response.text()}`);
}

function normalizeUrl(value) {
  return String(value || "").trim().replace(/\/+$/, "");
}

function relayProxyUrl() {
  return (
    process.env.AGENT_CONTROL_RELAY_PROXY ||
    process.env.HTTPS_PROXY ||
    process.env.https_proxy ||
    process.env.HTTP_PROXY ||
    process.env.http_proxy ||
    ""
  ).trim();
}

function relayFetch(url, init = {}) {
  return retryRelayFetch(url, init);
}

async function retryRelayFetch(url, init = {}) {
  const routes = relayFetchRoutes();
  const errors = [];
  const { timeoutMs, ...fetchInitBase } = init;
  const effectiveTimeoutMs = Number(timeoutMs || RELAY_FETCH_TIMEOUT_MS);
  for (const route of routes) {
    for (let attempt = 1; attempt <= RELAY_FETCH_ATTEMPTS; attempt += 1) {
      try {
        const fetchInit = route.dispatcher ? { ...fetchInitBase, dispatcher: route.dispatcher } : fetchInitBase;
        return await fetchWithTimeout(url, fetchInit, effectiveTimeoutMs);
      } catch (error) {
        errors.push(`${route.name}: ${describeError(error)}`);
        if (attempt < RELAY_FETCH_ATTEMPTS) await delay(650 * attempt);
      }
    }
  }
  for (const route of routes) {
    try {
      console.error(`relay fetch falling back to curl ${route.name}: ${errors.at(-1) || "fetch failed"}`);
      return await curlRelayFetch(url, fetchInitBase, {
        useProxy: route.useProxy,
        timeoutSeconds: Math.ceil(effectiveTimeoutMs / 1000) + 2,
      });
    } catch (error) {
      errors.push(`curl ${route.name}: ${describeError(error)}`);
    }
  }
  throw new Error(`relay fetch failed after retries: ${errors.join("; ")}`);
}

function relayFetchRoutes() {
  if (!RELAY_DISPATCHER) return [{ name: "direct", dispatcher: null, useProxy: false }];
  const routes = [{ name: "proxy", dispatcher: RELAY_DISPATCHER, useProxy: true }];
  if (RELAY_ALLOW_DIRECT_FALLBACK) routes.push({ name: "direct", dispatcher: null, useProxy: false });
  return routes;
}

async function fetchWithTimeout(url, init = {}, timeoutMs = RELAY_FETCH_TIMEOUT_MS) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(new Error(`relay fetch timed out after ${timeoutMs}ms`)), timeoutMs);
  timer.unref?.();
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

async function curlRelayFetch(url, init = {}, options = {}) {
  if (!commandExists("curl")) throw new Error("curl not found");
  const method = String(init.method || "GET").toUpperCase();
  const body = init.body == null ? null : String(init.body);
  const timeoutSeconds = Number(options.timeoutSeconds || RELAY_CURL_TIMEOUT_SECONDS);
  const args = [
    "-sS",
    "--connect-timeout",
    "20",
    "--max-time",
    String(timeoutSeconds),
    "--write-out",
    "\n__AGENT_CONTROL_HTTP_STATUS__:%{http_code}",
  ];
  if (options.useProxy && RELAY_PROXY_URL) {
    args.push("--proxy", RELAY_PROXY_URL);
  } else {
    args.push("--noproxy", "*");
  }
  for (const [name, value] of relayHeaderEntries(init.headers)) {
    args.push("-H", `${name}: ${value}`);
  }
  if (method !== "GET" || body != null) args.push("-X", method);
  if (body != null) args.push("--data-binary", "@-");
  args.push(url);

  const { stdout } = await spawnFilePromise("curl", args, {
    input: body,
    timeout: (timeoutSeconds + 5) * 1000,
    maxBuffer: 1024 * 1024 * 12,
    env: process.env,
  });
  const marker = "\n__AGENT_CONTROL_HTTP_STATUS__:";
  const markerIndex = stdout.lastIndexOf(marker);
  if (markerIndex < 0) throw new Error(`curl returned malformed response: ${tailText(stdout)}`);
  const responseBody = stdout.slice(0, markerIndex);
  const status = Number(stdout.slice(markerIndex + marker.length).trim());
  if (!Number.isFinite(status) || status <= 0) throw new Error(`curl returned invalid status: ${tailText(stdout)}`);
  return new Response(responseBody, {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

function relayHeaderEntries(headers = {}) {
  if (!headers) return [];
  if (headers instanceof Headers) return [...headers.entries()];
  if (Array.isArray(headers)) return headers;
  return Object.entries(headers);
}

function describeError(error) {
  if (!error) return "unknown error";
  const cause = error.cause;
  const causeParts = cause
    ? [cause.code, cause.reason, cause.host ? `host=${cause.host}` : "", cause.port ? `port=${cause.port}` : ""].filter(Boolean).join(" ")
    : "";
  return [error.message || String(error), causeParts].filter(Boolean).join(" | ");
}

function loadPrivateBridgeState() {
  try {
    if (!existsSync(BRIDGE_STATE_FILE)) return {};
    return JSON.parse(readFileSync(BRIDGE_STATE_FILE, "utf8"));
  } catch (error) {
    console.error(`failed to load private bridge state: ${error.message}`);
    return {};
  }
}

function loadOrCreateLivePair(savedState) {
  const privateKeyPem = String(savedState?.desktopPrivateKeyPem || "");
  if (privateKeyPem) {
    try {
      const privateKey = createPrivateKey(privateKeyPem);
      return {
        privateKey,
        publicKey: createPublicKey(privateKey),
      };
    } catch (error) {
      console.error(`failed to restore desktop keypair, generating a new one: ${error.message}`);
    }
  }
  return generateKeyPairSync("ec", { namedCurve: "prime256v1" });
}

function restorePersistedSessions(savedSessions = []) {
  if (!Array.isArray(savedSessions)) return;
  for (const saved of savedSessions) {
    try {
      const deviceId = String(saved.deviceId || "");
      const key = base64UrlDecode(saved.key || "");
      if (!deviceId || key.length !== 32) continue;
      sessions.set(deviceId, {
        key,
        oldKeys: Array.isArray(saved.oldKeys) ? saved.oldKeys.map((value) => base64UrlDecode(value)).filter((value) => value.length === 32).slice(0, 2) : [],
        deviceName: saved.deviceName || "Android",
        devicePublicKey: saved.devicePublicKey || "",
        desktopFingerprint: saved.desktopFingerprint || desktopFingerprint,
        pairedAt: Number(saved.pairedAt || Date.now()),
      });
    } catch (error) {
      console.error(`failed to restore paired session: ${error.message}`);
    }
  }
}

function serializeSessions() {
  return [...sessions.entries()].map(([deviceId, session]) => ({
    deviceId,
    key: base64UrlEncode(session.key),
    oldKeys: (session.oldKeys || []).map((key) => base64UrlEncode(key)),
    deviceName: session.deviceName,
    devicePublicKey: session.devicePublicKey,
    desktopFingerprint: session.desktopFingerprint,
    pairedAt: session.pairedAt,
  }));
}

function restorePersistedMessages(savedMessages = []) {
  if (!Array.isArray(savedMessages)) return;
  state.messages = savedMessages
    .map(normalizePersistedMessage)
    .filter(Boolean)
    .slice(-MAX_IN_MEMORY_MESSAGES);
}

function serializeMessages() {
  return state.messages
    .filter(isPersistableMessage)
    .slice(-MAX_PERSISTED_MESSAGES)
    .map(normalizePersistedMessage)
    .filter(Boolean);
}

function isPersistableMessage(message) {
  return !(message?.authorId === "system" && message.text === BRIDGE_ONLINE_MESSAGE);
}

function normalizePersistedMessage(message) {
  if (!message || typeof message !== "object") return null;
  const now = Date.now();
  const text = boundedText(message.text, 12000);
  const targetAgentId = message.targetAgentId == null ? null : sanitizeText(message.targetAgentId, "", 80) || null;
  return {
    id: sanitizeText(message.id, id(), 80),
    authorId: sanitizeText(message.authorId, "system", 80),
    kind: ["USER", "AGENT", "SYSTEM"].includes(message.kind) ? message.kind : "AGENT",
    text,
    createdAt: finiteTimestamp(message.createdAt, now),
    targetAgentId,
    conversationId: sanitizeText(message.conversationId, "", 140),
    attachments: normalizePersistedAttachments(message.attachments),
    toolCalls: normalizePersistedToolCalls(message.toolCalls),
  };
}

function normalizePersistedAttachments(value) {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item) => item && typeof item === "object")
    .slice(0, 8)
    .map((item) => ({
      id: sanitizeText(item.id, id(), 80),
      name: sanitizeText(item.name, "attachment", 160),
      mimeType: sanitizeText(item.mimeType || item.type, "application/octet-stream", 120),
      direction: ["PHONE_TO_DESKTOP", "DESKTOP_TO_PHONE"].includes(item.direction) ? item.direction : "PHONE_TO_DESKTOP",
      uri: boundedText(item.uri || item.url || item.path, 1000),
      sizeLabel: sanitizeText(item.sizeLabel, `${finiteNumber(item.sizeBytes ?? item.size, 0)} bytes`, 80),
    }));
}

function normalizePersistedToolCalls(value) {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item) => item && typeof item === "object")
    .slice(0, 8)
    .map((item) => ({
      id: sanitizeText(item.id, id(), 80),
      agentId: sanitizeText(item.agentId, "", 80),
      toolName: sanitizeText(item.toolName, "tool", 80),
      status: ["QUEUED", "RUNNING", "SUCCESS", "FAILED"].includes(item.status) ? item.status : "SUCCESS",
      input: boundedText(item.input, 1000),
      output: boundedText(item.output, 1000),
      startedAt: finiteTimestamp(item.startedAt, Date.now()),
    }));
}

function pruneMessages() {
  if (state.messages.length > MAX_IN_MEMORY_MESSAGES) {
    state.messages.splice(0, state.messages.length - MAX_IN_MEMORY_MESSAGES);
  }
}

function boundedText(value, maxLength) {
  return String(value || "").replace(/\u0000/g, "").trim().slice(0, maxLength);
}

function finiteTimestamp(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : fallback;
}

function finiteNumber(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number : fallback;
}

async function savePrivateBridgeState() {
  await mkdir(PRIVATE_STATE_DIR, { recursive: true });
  const body = {
    savedAt: Date.now(),
    desktopPrivateKeyPem: livePrivatePem,
    relayDesktopId,
    relaySecret,
    sessions: serializeSessions(),
    codexRuntimeSettings: normalizeCodexRuntimeSettings(codexRuntimeSettings),
    messages: serializeMessages(),
  };
  await writeFile(BRIDGE_STATE_FILE, JSON.stringify(body, null, 2), { mode: 0o600 });
  await chmod(BRIDGE_STATE_FILE, 0o600).catch(() => {});
}

function schedulePrivateBridgeStateSave() {
  if (privateStateSaveTimer) return;
  privateStateSaveTimer = setTimeout(() => {
    privateStateSaveTimer = null;
    savePrivateBridgeState().catch((error) => console.error(`failed to save bridge state: ${error.message}`));
  }, 250);
  privateStateSaveTimer.unref?.();
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isLoopback(address) {
  return address === "127.0.0.1" || address === "::1" || address === "::ffff:127.0.0.1";
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  })[char]);
}

async function snapshot() {
  const documents = await readDocuments();
  const agents = allAgents();
  updateCodexContextEstimate();
  return {
    agents,
    teams: allTeams(),
    commands: allSlashCommands(),
    messages: state.messages,
    transfers: state.transfers,
    documents,
    heartbeats: state.heartbeats.slice(0, 30),
    runtimeSettings: {
      codex: codexRuntimeSnapshot(),
      agents: agentRuntimeSnapshots(agents),
      permissionOptions: CODEX_PERMISSION_OPTIONS.map(({ id, label }) => ({ id, label })),
    },
  };
}

async function diagnostics(session, deviceId = "") {
  const agents = allAgents();
  const runtimeSnapshots = agentRuntimeSnapshots(agents);
  const activeKey = ensureActivePairingKey();
  const connectionMode = RELAY_URL ? "relay" : "direct";
  return {
    bridgeVersion: VERSION,
    desktopName: hostname(),
    generatedAt: Date.now(),
    pairing: {
      pairedDeviceCount: sessions.size,
      sessionActive: Boolean(session),
      pairedDeviceId: deviceId,
      desktopFingerprint,
      pendingChallenges: pairingChallenges.size,
      keyExpiresAt: activeKey.expiresAt,
    },
    connectionMode,
    relayConfigured: Boolean(RELAY_URL),
    relayHost: relayHostLabel(),
    pairedDeviceCount: sessions.size,
    sessionActive: Boolean(session),
    pairedDeviceId: deviceId,
    agents: agents.map((agent) => agentDiagnostic(agent, runtimeSnapshots[agent.id] || runtimeSettingsForAgent(rootAdapterFor(agent)))),
    runtimeOptions: runtimeOptionAvailability(runtimeSnapshots),
    recentErrors: recentDiagnosticErrors(),
  };
}

function agentDiagnostic(agent, runtimeSettings = {}) {
  const recent = [...state.messages].reverse().find((message) =>
    message.authorId === agent.id ||
    message.targetAgentId === agent.id ||
    (agent.kind === "SUBAGENT" && message.authorId === rootAdapterFor(agent).id)
  );
  const recentCalls = Array.isArray(recent?.toolCalls) ? recent.toolCalls : [];
  const lastCall = [...recentCalls].reverse().find((call) => call?.toolName);
  const failedCall = [...recentCalls].reverse().find((call) => call?.status === "FAILED");
  const textError = recent?.kind === "AGENT" && /failed to reply|auth|timeout|offline|error/i.test(String(recent.text || ""))
    ? firstLine(recent.text)
    : "";
  return {
    id: agent.id,
    name: agent.name,
    kind: agent.kind,
    status: agent.status,
    parentId: agent.parentId || null,
    teamId: agent.teamId || "",
    tools: Array.isArray(agent.tools) ? agent.tools.slice(0, 20) : [],
    model: runtimeSettings.model || "",
    reasoningEffort: runtimeSettings.reasoningEffort || "",
    permissionMode: runtimeSettings.permissionMode || "",
    contextUsedTokens: Number(runtimeSettings.contextUsedTokens || 0),
    contextLimitTokens: Number(runtimeSettings.contextLimitTokens || 0),
    modelOptions: optionIds(runtimeSettings.modelOptions),
    reasoningOptions: optionIds(runtimeSettings.reasoningOptions),
    permissionOptions: optionIds(runtimeSettings.permissionOptions),
    lastAction: diagnosticActionLabel(lastCall),
    lastError: boundedText(failedCall?.output || textError || "", 220),
    diagnosticState: failedCall || textError ? "warn" : agent.status === "PAUSED" ? "warn" : "ok",
  };
}

function runtimeOptionAvailability(runtimeSnapshots = {}) {
  return Object.fromEntries(Object.entries(runtimeSnapshots).map(([agentId, settings]) => [
    agentId,
    {
      modelOptions: optionIds(settings.modelOptions),
      reasoningOptions: optionIds(settings.reasoningOptions),
      permissionOptions: optionIds(settings.permissionOptions),
    },
  ]));
}

function optionIds(options = []) {
  return Array.isArray(options) ? options.map((option) => String(option.id || "")).filter(Boolean).slice(0, 50) : [];
}

function diagnosticActionLabel(call) {
  if (!call) return "";
  const status = String(call.status || "").toLowerCase();
  const name = String(call.toolName || "agent.step");
  const output = firstLine(call.output || call.input || "");
  return [name, status, output].filter(Boolean).join(" / ").slice(0, 220);
}

function recentDiagnosticErrors() {
  const errors = [];
  for (const message of [...state.messages].reverse()) {
    const text = String(message.text || "");
    const textLooksError = /failed to reply|auth|timeout|offline|error/i.test(text);
    const failedCall = Array.isArray(message.toolCalls)
      ? [...message.toolCalls].reverse().find((call) => call?.status === "FAILED")
      : null;
    if (textLooksError || failedCall) {
      errors.push(boundedText(firstLine(failedCall?.output || text), 220));
    }
    if (errors.length >= 8) break;
  }
  return [...new Set(errors.filter(Boolean))];
}

function relayHostLabel() {
  if (!RELAY_URL) return "";
  try {
    return new URL(RELAY_URL).host;
  } catch {
    return "";
  }
}

function runtimeContextFor(agent, payload) {
  const rootAgent = rootAdapterFor(agent);
  const runtimeAgent = agentHasRuntimeOptions(agent) ? agent : rootAgent;
  const runtimeSettings = runtimeSettingsForAgent(runtimeAgent, payload?.runtimeOptions || {});
  const requestedPermission = payload?.agentPermissionMode || payload?.runtimeOptions?.permissionMode || runtimeSettings.permissionMode;
  const permissionMode = optionIdOrDefault(runtimeSettings.permissionOptions || CODEX_PERMISSION_OPTIONS, requestedPermission, runtimeSettings.permissionMode || "read-only");
  const baseContext = {
    conversationId: sanitizeText(payload?.conversationId, "", 140),
    permissionMode,
    runtimeSettings: {
      ...runtimeSettings,
      permissionMode,
    },
  };
  if (rootAgent.id !== "codex") return baseContext;
  if (payload?.runtimeOptions) {
    codexRuntimeSettings = normalizeCodexRuntimeSettings({
      ...codexRuntimeSettings,
      ...payload.runtimeOptions,
      permissionMode,
    });
    schedulePrivateBridgeStateSave();
  }
  updateCodexContextEstimate();
  return { ...baseContext, runtimeSettings: codexRuntimeSettings };
}

function agentRuntimeSnapshots(agents = allAgents()) {
  const snapshots = {
    codex: codexRuntimeSnapshot(),
    claude: runtimeSettingsForAgent({ id: "claude" }),
    gemini_cli: runtimeSettingsForAgent({ id: "gemini_cli" }),
    antigravity: runtimeSettingsForAgent({ id: "antigravity" }),
    opencode: runtimeSettingsForAgent({ id: "opencode" }),
  };
  for (const agent of agents) {
    const root = rootAdapterFor(agent);
    snapshots[agent.id] = agentHasRuntimeOptions(agent)
      ? runtimeSettingsForAgent(agent)
      : root.id === "codex" ? snapshots.codex : runtimeSettingsForAgent(root);
  }
  return snapshots;
}

function agentHasRuntimeOptions(agent) {
  return runtimeOptionInputHasValues(agent?.modelOptions || agent?.models) ||
    runtimeOptionInputHasValues(agent?.reasoningOptions || agent?.reasoning) ||
    runtimeOptionInputHasValues(agent?.permissionOptions || agent?.permissions);
}

function runtimeOptionInputHasValues(value) {
  return Array.isArray(value) && value.length > 0;
}

function codexRuntimeSnapshot() {
  const settings = normalizeCodexRuntimeSettings(codexRuntimeSettings);
  return {
    ...settings,
    modelOptions: CODEX_MODEL_OPTIONS.map(({ id, label }) => ({ id, label })),
    reasoningOptions: CODEX_REASONING_OPTIONS.map(({ id, label }) => ({ id, label })),
    permissionOptions: CODEX_PERMISSION_OPTIONS.map(({ id, label }) => ({ id, label })),
  };
}

function runtimeSettingsForAgent(agent, value = {}) {
  if (agent?.id === "codex") return normalizeCodexRuntimeSettings(value && Object.keys(value).length ? value : codexRuntimeSettings);
  const definition = runtimeDefinitionForAgent(agent);
  return normalizeGenericRuntimeSettings(definition, value);
}

function runtimeDefinitionForAgent(agent) {
  switch (agent?.id) {
    case "claude":
      return {
        defaultModel: CLAUDE_DEFAULT_MODEL,
        modelOptions: CLAUDE_MODEL_OPTIONS,
        defaultReasoningEffort: "medium",
        reasoningOptions: CLAUDE_REASONING_OPTIONS,
      };
    case "gemini_cli":
      return {
        defaultModel: GEMINI_DEFAULT_MODEL,
        modelOptions: GEMINI_MODEL_OPTIONS,
        defaultReasoningEffort: "default",
        reasoningOptions: GEMINI_REASONING_OPTIONS,
      };
    case "antigravity":
      return {
        defaultModel: ANTIGRAVITY_DEFAULT_MODEL,
        modelOptions: ANTIGRAVITY_MODEL_OPTIONS,
        defaultReasoningEffort: "off",
        reasoningOptions: ANTIGRAVITY_REASONING_OPTIONS,
      };
    case "opencode":
      return {
        defaultModel: OPENCODE_DEFAULT_MODEL,
        modelOptions: OPENCODE_MODEL_OPTIONS,
        defaultReasoningEffort: "default",
        reasoningOptions: OPENCODE_REASONING_OPTIONS,
      };
    default:
      if (agentHasRuntimeOptions(agent)) {
        const modelOptions = normalizeRuntimeOptionList(agent.modelOptions, agent.id || "agent", agent.name || "Agent", 128000);
        const reasoningOptions = normalizeRuntimeOptionList(agent.reasoningOptions, "default", "Default", 0);
        return {
          defaultModel: modelOptions[0]?.id || sanitizeText(agent?.id, "agent", 80),
          modelOptions,
          defaultReasoningEffort: reasoningOptions[0]?.id || "default",
          reasoningOptions,
          permissionOptions: normalizeRuntimeOptionList(agent.permissionOptions || CODEX_PERMISSION_OPTIONS, "read-only", "Read Only", 0),
        };
      }
      return {
        defaultModel: sanitizeText(agent?.id, "agent", 80),
        modelOptions: [{ id: sanitizeText(agent?.id, "agent", 80), label: sanitizeText(agent?.name || agent?.id, "Agent", 80), contextLimitTokens: 128000 }],
        defaultReasoningEffort: "default",
        reasoningOptions: [{ id: "default", label: "Default" }],
        permissionOptions: CODEX_PERMISSION_OPTIONS,
      };
  }
}

function normalizeGenericRuntimeSettings(definition, value = {}) {
  const model = optionIdOrDefault(definition.modelOptions, value.model, definition.defaultModel);
  const reasoningEffort = optionIdOrDefault(definition.reasoningOptions, value.reasoningEffort, definition.defaultReasoningEffort);
  const permissionOptions = definition.permissionOptions || CODEX_PERMISSION_OPTIONS;
  const permissionMode = optionIdOrDefault(permissionOptions, value.permissionMode, "read-only");
  const modelInfo = definition.modelOptions.find((option) => option.id === model) || definition.modelOptions[0];
  return {
    model,
    reasoningEffort,
    permissionMode,
    contextUsedTokens: Number.isFinite(value.contextUsedTokens) ? Math.max(0, Math.round(value.contextUsedTokens)) : 0,
    contextLimitTokens: modelInfo.contextLimitTokens || 128000,
    updatedAt: Date.now(),
    modelOptions: definition.modelOptions.map(({ id, label }) => ({ id, label })),
    reasoningOptions: definition.reasoningOptions.map(({ id, label }) => ({ id, label })),
    permissionOptions: permissionOptions.map(({ id, label }) => ({ id, label })),
  };
}

function normalizeRuntimeOptionList(value, fallbackId, fallbackLabel, fallbackContextLimit = 128000) {
  const items = Array.isArray(value) ? value : [];
  const options = items
    .map((item) => {
      if (typeof item === "string") {
        const idValue = sanitizeText(item, "", 120);
        return idValue ? { id: idValue, label: idValue, contextLimitTokens: fallbackContextLimit } : null;
      }
      if (item && typeof item === "object") {
        const idValue = sanitizeText(item.id || item.value || item.model, "", 120);
        if (!idValue) return null;
        return {
          id: idValue,
          label: sanitizeText(item.label || item.name || idValue, idValue, 120),
          contextLimitTokens: Number(item.contextLimitTokens || item.context || fallbackContextLimit) || fallbackContextLimit,
        };
      }
      return null;
    })
    .filter(Boolean)
    .filter((option, index, list) => list.findIndex((item) => item.id === option.id) === index);
  return options.length ? options : [{ id: fallbackId, label: fallbackLabel, contextLimitTokens: fallbackContextLimit }];
}

function normalizeOptionalRuntimeOptionList(value, fallbackContextLimit = 128000) {
  const items = Array.isArray(value) ? value : [];
  return items
    .map((item) => {
      if (typeof item === "string") {
        const idValue = sanitizeText(item, "", 120);
        return idValue ? { id: idValue, label: idValue, contextLimitTokens: fallbackContextLimit } : null;
      }
      if (item && typeof item === "object") {
        const idValue = sanitizeText(item.id || item.value || item.model, "", 120);
        if (!idValue) return null;
        return {
          id: idValue,
          label: sanitizeText(item.label || item.name || idValue, idValue, 120),
          contextLimitTokens: Number(item.contextLimitTokens || item.context || fallbackContextLimit) || fallbackContextLimit,
        };
      }
      return null;
    })
    .filter(Boolean)
    .filter((option, index, list) => list.findIndex((item) => item.id === option.id) === index);
}

function normalizeAgentSlashCommands(value) {
  const items = Array.isArray(value) ? value : [];
  return items
    .map(normalizeSlashCommandSpec)
    .filter(Boolean)
    .filter((item, index, list) => list.findIndex((candidate) => candidate.trigger === item.trigger) === index)
    .slice(0, 24);
}

function normalizeSlashCommandSpec(item) {
  if (typeof item === "string") {
    const trigger = normalizeCommandTrigger(item);
    return trigger ? { trigger, label: commandLabel(trigger), target: "selected" } : null;
  }
  if (!item || typeof item !== "object") return null;
  const trigger = normalizeCommandTrigger(item.trigger || item.command || item.name);
  if (!trigger) return null;
  return {
    trigger,
    label: sanitizeText(item.label || item.title || commandLabel(trigger), commandLabel(trigger), 80),
    target: sanitizeText(item.target || "selected", "selected", 32),
  };
}

function normalizeCommandTrigger(value = "") {
  const raw = String(value || "").trim();
  if (!raw) return "";
  const withSlash = raw.startsWith("/") ? raw : `/${raw}`;
  const normalized = canonicalSlashCommand(withSlash);
  if (!/^\/[a-z0-9][a-z0-9-]{0,48}$/i.test(normalized)) return "";
  return normalized;
}

function commandLabel(trigger = "") {
  return String(trigger || "")
    .replace(/^\//, "")
    .split("-")
    .filter(Boolean)
    .map((part) => part.slice(0, 1).toUpperCase() + part.slice(1))
    .join(" ") || "Command";
}

function normalizeCodexRuntimeSettings(value = {}) {
  const model = optionIdOrDefault(CODEX_MODEL_OPTIONS, value.model, CODEX_DEFAULT_MODEL);
  const reasoningEffort = optionIdOrDefault(CODEX_REASONING_OPTIONS, value.reasoningEffort, "low");
  const permissionMode = optionIdOrDefault(CODEX_PERMISSION_OPTIONS, value.permissionMode, "read-only");
  const modelInfo = CODEX_MODEL_OPTIONS.find((option) => option.id === model) || CODEX_MODEL_OPTIONS[0];
  return {
    model,
    reasoningEffort,
    permissionMode,
    contextUsedTokens: Number.isFinite(value.contextUsedTokens) ? Math.max(0, Math.round(value.contextUsedTokens)) : 0,
    contextLimitTokens: modelInfo.contextLimitTokens,
    updatedAt: Date.now(),
  };
}

function optionIdOrDefault(options, requested, fallback) {
  return options.some((option) => option.id === requested) ? requested : fallback;
}

function normalizeAgentPermissionMode(value, fallback = "read-only") {
  return CODEX_PERMISSION_OPTIONS.some((option) => option.id === value) ? value : fallback;
}

function agentPermissionForContext(context = {}) {
  const requested = context.permissionMode || context.runtimeSettings?.permissionMode;
  const builtin = CODEX_PERMISSION_OPTIONS.find((option) => option.id === requested);
  if (builtin) return builtin;
  const custom = Array.isArray(context.runtimeSettings?.permissionOptions)
    ? context.runtimeSettings.permissionOptions.find((option) => option?.id === requested)
    : null;
  if (custom) {
    return {
      id: sanitizeText(custom.id, "read-only", 120),
      label: sanitizeText(custom.label || custom.id, custom.id, 120),
      approval: "never",
      sandbox: "prompt-scoped custom permission",
    };
  }
  return CODEX_PERMISSION_OPTIONS[0];
}

function agentPermissionPromptLine(context = {}) {
  const permission = agentPermissionForContext(context);
  return `Current Agent Control permissions for this agent: ${permission.label} (${permission.id}). Read Only means inspect/plan only; Workspace Write means workspace edits are allowed; Full Access means the user allowed unrestricted local agent permissions for this turn.`;
}

function updateCodexContextEstimate() {
  const used = estimateCodexContextTokens();
  codexRuntimeSettings = normalizeCodexRuntimeSettings({
    ...codexRuntimeSettings,
    contextUsedTokens: used,
  });
}

function estimateCodexContextTokens() {
  const relevantMessages = state.messages.filter((message) => {
    if (message.authorId === "codex" || message.targetAgentId === "codex") return true;
    const agent = findAgent(message.authorId) || findAgent(message.targetAgentId);
    return agent ? rootAdapterFor(agent).id === "codex" : false;
  });
  const text = relevantMessages.slice(-80).map((message) => message.text || "").join("\n");
  return Math.max(1, Math.ceil(text.length / 4));
}

async function routeMessage(payload) {
  const text = String(payload.text || "").trim();
  const targetAgentId = payload.targetAgentId || "codex";
  const clientMessageId = sanitizeText(payload.clientMessageId, "", 80);
  const conversationId = sanitizeText(payload.conversationId, "", 140);
  const replyId = clientMessageId ? replyIdForClientMessage(clientMessageId) : id();
  const existingReply = clientMessageId ? state.messages.find((message) => message.id === replyId) : null;
  if (existingReply) return existingReply;
  mergeClientConversationContext(payload.conversationContext, targetAgentId, conversationId);
  const team = findTeam(targetAgentId);
  if (team) return await routeTeamMessage(team, payload);
  const agent = findAgent(targetAgentId) || agentDefinitions[0];
  const runtimeContext = runtimeContextFor(agent, payload);
  let userMessage = clientMessageId ? state.messages.find((message) => message.id === clientMessageId) : null;
  if (!userMessage) {
    userMessage = {
      id: clientMessageId || id(),
      authorId: "you",
      kind: "USER",
      text: text || "sent from phone",
      createdAt: Date.now(),
      targetAgentId: agent.id,
      conversationId,
      attachments: payload.attachments || [],
      toolCalls: [],
    };
    state.messages.push(userMessage);
    pruneMessages();
  }

  if (shouldReplyAsync(agent, text)) {
    const reply = pendingReplyFor(agent, text, replyId, runtimeContext);
    state.messages.push(reply);
    pruneMessages();
    heartbeat(agent.name, "Received Android bridge message; agent is thinking.");
    updateCodexContextEstimate();
    schedulePrivateBridgeStateSave();
    console.log(`Message accepted by ${agent.name}: ${text || "(empty)"}`);
    completeReplyAsync(agent, text, reply, { ...runtimeContext, currentMessageId: userMessage.id });
    return reply;
  }

  const response = normalizeAgentResponse(await responseFor(agent, text, { ...runtimeContext, currentMessageId: userMessage.id }));
  const directiveResult = await applyAgentDirectives(agent, response.text);
  const reply = {
    id: replyId,
    authorId: agent.id,
    kind: "AGENT",
    text: directiveResult.text,
    createdAt: Date.now(),
    targetAgentId: "you",
    conversationId,
    attachments: directiveResult.attachments || [],
    toolCalls: completedToolCallsFor(agent, text, [], response.toolCalls, directiveResult),
  };
  state.messages.push(reply);
  pruneMessages();
  heartbeat(agent.name, "Handled Android bridge message.");
  updateCodexContextEstimate();
  schedulePrivateBridgeStateSave();
  console.log(`Message routed to ${agent.name}: ${text || "(empty)"}`);
  return reply;
}

function replyIdForClientMessage(clientMessageId) {
  return `reply-${sanitizeText(clientMessageId, id(), 72)}`;
}

function shouldReplyAsync(agent, text) {
  if (DISABLE_REAL_AGENTS) return false;
  const command = normalizeSlashCommandText(text).command;
  if (command && !agentSlashCommand(agent, command)) return false;
  const adapter = rootAdapterFor(agent);
  return ["codex", "claude", "antigravity", "gemini_cli", "opencode"].includes(adapter.id);
}

function pendingReplyFor(agent, text, replyId = id(), context = {}) {
  const now = Date.now();
  return {
    id: replyId,
    authorId: agent.id,
    kind: "AGENT",
    text: "Thinking...",
    createdAt: now,
    targetAgentId: "you",
    conversationId: sanitizeText(context.conversationId, "", 140),
    attachments: [],
    toolCalls: pendingToolCallsFor(agent, text, context, now),
  };
}

function completeReplyAsync(agent, text, pendingReply, context = {}) {
  const reporter = createProgressReporter(agent, pendingReply, context);
  reporter.start();
  responseFor(agent, text, { ...context, progressReport: reporter.report })
    .then((responseValue) => {
      const response = normalizeAgentResponse(responseValue);
      return applyAgentDirectives(agent, response.text).then((directiveResult) => ({ response, directiveResult }));
    })
    .then(({ response, directiveResult }) => {
      reporter.stop();
      const latestPending = state.messages.find((message) => message.id === pendingReply.id) || pendingReply;
      const completed = {
        ...latestPending,
        text: directiveResult.text,
        attachments: directiveResult.attachments || [],
        toolCalls: completedToolCallsFor(agent, text, latestPending.toolCalls, response.toolCalls, directiveResult),
      };
      replaceMessage(completed);
      heartbeat(agent.name, "Finished Android bridge message.");
      updateCodexContextEstimate();
      schedulePrivateBridgeStateSave();
      broadcast("agent.output", completed);
      console.log(`Message completed by ${agent.name}: ${text || "(empty)"}`);
    })
    .catch((error) => {
      reporter.stop();
      const latestPending = state.messages.find((message) => message.id === pendingReply.id) || pendingReply;
      const failed = {
        ...latestPending,
        text: `${agent.name} failed to reply: ${firstLine(error.message)}`,
        toolCalls: [
          {
            ...(latestPending.toolCalls[0] || {}),
            status: "FAILED",
            output: firstLine(error.message),
          },
        ],
      };
      replaceMessage(failed);
      schedulePrivateBridgeStateSave();
      broadcast("agent.output", failed);
      console.error(`${agent.name} async reply failed: ${error.message}`);
    });
}

function createProgressReporter(agent, pendingReply, context = {}) {
  const adapter = rootAdapterFor(agent);
  let stopped = false;
  let tick = 0;
  const startedAt = Date.now();
  const scheduled = progressMessagesFor(adapter.id, context);
  let timer = null;
  const report = (text, toolCalls = []) => {
    if (stopped) return;
    const current = state.messages.find((message) => message.id === pendingReply.id) || pendingReply;
    const next = {
      ...current,
      text: boundedText(text || current.text || `${agent.name} is working...`, 2000),
      toolCalls: mergeProgressToolCalls(current.toolCalls, normalizeResponseToolCalls(toolCalls)),
    };
    replaceMessage(next);
    schedulePrivateBridgeStateSave();
    broadcast("agent.output", next);
  };
  return {
    start() {
      report(scheduled[0]?.text || `${agent.name} is working...`, scheduled[0]?.toolCalls || []);
      timer = setInterval(() => {
        tick += 1;
        const message = scheduled[Math.min(tick, scheduled.length - 1)];
        const elapsed = Math.round((Date.now() - startedAt) / 1000);
        report(`${message.text} (${elapsed}s)`, message.toolCalls || []);
      }, 12_000);
      timer.unref?.();
    },
    stop() {
      stopped = true;
      if (timer) clearInterval(timer);
    },
    report,
  };
}

function progressMessagesFor(adapterId, context = {}) {
  if (adapterId === "codex") {
    const settings = normalizeCodexRuntimeSettings(context.runtimeSettings || codexRuntimeSettings);
    return [
      {
        text: "Preparing context...",
        toolCalls: [
          toolCall("codex", "codex.memory", "RUNNING", "shared-agent-loop", "reading shared memory"),
          toolCall("codex", "codex.context", "RUNNING", "recent conversation", `${settings.contextUsedTokens}/${settings.contextLimitTokens} tokens`),
        ],
      },
      {
        text: "Starting Codex...",
        toolCalls: [toolCall("codex", "codex.exec", "RUNNING", `codex exec -m ${settings.model}`, "launching CLI")],
      },
      {
        text: "Running...",
        toolCalls: [toolCall("codex", "codex.exec", "RUNNING", `codex exec -m ${settings.model}`, "waiting for model output")],
      },
      {
        text: "Still running...",
        toolCalls: [toolCall("codex", "codex.exec", "RUNNING", `codex exec -m ${settings.model}`, "long-running reply")],
      },
    ];
  }
  if (adapterId === "claude") {
    const settings = runtimeSettingsForAgent({ id: "claude" }, context.runtimeSettings || {});
    return [
      {
        text: "Planning...",
        toolCalls: [
          toolCall("claude", "claude.memory", "RUNNING", "shared-agent-loop", "reading shared memory"),
          toolCall("claude", "claude.plan", "RUNNING", `model=${settings.model}`, "building prompt"),
        ],
      },
      {
        text: "Starting Claude Code...",
        toolCalls: [toolCall("claude", "claude.invoke", "RUNNING", `claude --model ${settings.model}`, "launching CLI")],
      },
      {
        text: "Running...",
        toolCalls: [toolCall("claude", "claude.invoke", "RUNNING", `claude --model ${settings.model}`, "waiting for output")],
      },
      {
        text: "Still running...",
        toolCalls: [toolCall("claude", "claude.invoke", "RUNNING", `claude --model ${settings.model}`, "long-running reply")],
      },
    ];
  }
  if (adapterId === "gemini_cli") {
    const settings = runtimeSettingsForAgent({ id: "gemini_cli" }, context.runtimeSettings || {});
    return [
      {
        text: "Planning...",
        toolCalls: [
          toolCall("gemini_cli", "gemini.memory", "RUNNING", "shared-agent-loop", "reading shared memory"),
          toolCall("gemini_cli", "gemini.plan", "RUNNING", `model=${settings.model}`, "building prompt"),
        ],
      },
      {
        text: "Starting Gemini CLI...",
        toolCalls: [toolCall("gemini_cli", "gemini.invoke", "RUNNING", `gemini --model ${settings.model}`, "launching CLI")],
      },
      {
        text: "Running...",
        toolCalls: [toolCall("gemini_cli", "gemini.invoke", "RUNNING", `gemini --model ${settings.model}`, "waiting for output")],
      },
      {
        text: "Still running...",
        toolCalls: [toolCall("gemini_cli", "gemini.invoke", "RUNNING", `gemini --model ${settings.model}`, "long-running reply")],
      },
    ];
  }
  if (adapterId === "antigravity") {
    const settings = runtimeSettingsForAgent({ id: "antigravity" }, context.runtimeSettings || {});
    return [
      {
        text: "Planning...",
        toolCalls: [
          toolCall("antigravity", "antigravity.memory", "RUNNING", "shared-agent-loop", "reading shared memory"),
          toolCall("antigravity", "antigravity.plan", "RUNNING", `model=${settings.model}`, "building prompt"),
        ],
      },
      {
        text: "Starting Antigravity...",
        toolCalls: [toolCall("antigravity", "antigravity.invoke", "RUNNING", "agent --json", "launching CLI")],
      },
      {
        text: "Running...",
        toolCalls: [toolCall("antigravity", "antigravity.invoke", "RUNNING", "agent --json", "waiting for output")],
      },
      {
        text: "Still running...",
        toolCalls: [toolCall("antigravity", "antigravity.invoke", "RUNNING", "agent --json", "long-running reply")],
      },
    ];
  }
  if (adapterId === "opencode") {
    const settings = runtimeSettingsForAgent({ id: "opencode" }, context.runtimeSettings || {});
    return [
      {
        text: "Planning...",
        toolCalls: [
          toolCall("opencode", "opencode.memory", "RUNNING", "shared-agent-loop", "reading shared memory"),
          toolCall("opencode", "opencode.plan", "RUNNING", `model=${settings.model}`, "building prompt"),
        ],
      },
      {
        text: "Starting OpenCode...",
        toolCalls: [toolCall("opencode", "opencode.run", "RUNNING", `opencode --model ${settings.model}`, "launching CLI")],
      },
      {
        text: "Running...",
        toolCalls: [toolCall("opencode", "opencode.run", "RUNNING", `opencode --model ${settings.model}`, "waiting for output")],
      },
      {
        text: "Still running...",
        toolCalls: [toolCall("opencode", "opencode.run", "RUNNING", `opencode --model ${settings.model}`, "long-running reply")],
      },
    ];
  }
  return [
    {
      text: "Planning...",
      toolCalls: [
        toolCall(adapterId, "agent.memory", "RUNNING", "shared-agent-loop", "reading shared memory"),
        toolCall(adapterId, "agent.plan", "RUNNING", "agent prompt", "building prompt"),
      ],
    },
    {
      text: "Running...",
      toolCalls: [toolCall(adapterId, "agent.invoke", "RUNNING", "agent adapter", "waiting for output")],
    },
    {
      text: "Still running...",
      toolCalls: [toolCall(adapterId, "agent.invoke", "RUNNING", "agent adapter", "long-running reply")],
    },
  ];
}

function mergeProgressToolCalls(existingCalls = [], updateCalls = []) {
  const merged = new Map();
  for (const call of normalizeResponseToolCalls(existingCalls)) {
    merged.set(call.toolName, call);
  }
  for (const call of normalizeResponseToolCalls(updateCalls)) {
    const previous = merged.get(call.toolName);
    merged.set(call.toolName, previous ? { ...previous, ...call, id: previous.id, startedAt: previous.startedAt } : call);
  }
  return [...merged.values()].slice(-10);
}

function normalizeAgentResponse(value) {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return {
      text: String(value.text || ""),
      toolCalls: normalizeResponseToolCalls(value.toolCalls),
    };
  }
  return { text: String(value || ""), toolCalls: [] };
}

function agentResponse(text, toolCalls = []) {
  return {
    text: String(text || ""),
    toolCalls: normalizeResponseToolCalls(toolCalls),
  };
}

function normalizeResponseToolCalls(toolCalls = []) {
  return Array.isArray(toolCalls)
    ? toolCalls
        .filter((toolCall) => toolCall && typeof toolCall === "object")
        .map((toolCall) => ({
          id: sanitizeText(toolCall.id, id(), 80),
          agentId: sanitizeText(toolCall.agentId, "", 80),
          toolName: sanitizeText(toolCall.toolName, "agent.step", 120),
          status: ["QUEUED", "RUNNING", "SUCCESS", "FAILED"].includes(toolCall.status) ? toolCall.status : "SUCCESS",
          input: boundedText(toolCall.input, 1400),
          output: boundedText(toolCall.output, 1400),
          startedAt: finiteTimestamp(toolCall.startedAt, Date.now()),
        }))
    : [];
}

function toolCall(agentId, toolName, status, input = "", output = "", startedAt = Date.now()) {
  return {
    id: id(),
    agentId,
    toolName,
    status,
    input: boundedText(input, 1400),
    output: boundedText(output, 1400),
    startedAt,
  };
}

function pendingToolCallsFor(agent, text, context = {}, now = Date.now()) {
  const adapter = rootAdapterFor(agent);
  const permission = agentPermissionForContext(context);
  if (adapter.id === "codex") {
    const settings = normalizeCodexRuntimeSettings(context.runtimeSettings || codexRuntimeSettings);
    return [
      toolCall(agent.id, "codex.context", "SUCCESS", "recent conversation", `${settings.contextUsedTokens}/${settings.contextLimitTokens} tokens`, now),
      toolCall(agent.id, "codex.memory", "SUCCESS", "shared-agent-loop", "default memory loaded", now),
      toolCall(agent.id, "codex.permission", "SUCCESS", permission.label, permission.sandbox, now),
      toolCall(agent.id, "codex.exec", "RUNNING", `codex exec -m ${settings.model}`, "running desktop Codex", now),
    ];
  }
  if (adapter.id === "claude") {
    const settings = runtimeSettingsForAgent(adapter, context.runtimeSettings || {});
    return [
      toolCall(agent.id, "claude.prompt", "SUCCESS", `model=${settings.model}`, "plan mode, project settings", now),
      toolCall(agent.id, "claude.memory", "SUCCESS", "shared-agent-loop", "default memory loaded", now),
      toolCall(agent.id, "claude.permission", "SUCCESS", permission.label, claudePermissionSummary(permission), now),
      toolCall(agent.id, "claude.invoke", "RUNNING", `claude --model ${settings.model}`, "waiting for Claude Code", now),
    ];
  }
  if (adapter.id === "gemini_cli") {
    const settings = runtimeSettingsForAgent(adapter, context.runtimeSettings || {});
    return [
      toolCall(agent.id, "gemini.prompt", "SUCCESS", `model=${settings.model}`, "approval-mode plan", now),
      toolCall(agent.id, "gemini.memory", "SUCCESS", "shared-agent-loop", "default memory loaded", now),
      toolCall(agent.id, "gemini.permission", "SUCCESS", permission.label, `approval-mode ${geminiApprovalMode(permission)}`, now),
      toolCall(agent.id, "gemini.invoke", "RUNNING", `gemini --model ${settings.model}`, "waiting for Gemini CLI", now),
    ];
  }
  if (adapter.id === "antigravity") {
    const settings = runtimeSettingsForAgent(adapter, context.runtimeSettings || {});
    return [
      toolCall(agent.id, "antigravity.memory", "SUCCESS", "shared-agent-loop", "default memory loaded", now),
      toolCall(agent.id, "antigravity.permission", "SUCCESS", permission.label, "prompt-scoped permission policy", now),
      toolCall(agent.id, "antigravity.agent", "RUNNING", `openclaw model ${settings.model}`, "waiting for Antigravity", now),
    ];
  }
  if (adapter.id === "opencode") {
    const settings = runtimeSettingsForAgent(adapter, context.runtimeSettings || {});
    return [
      toolCall(agent.id, "opencode.memory", "SUCCESS", "shared-agent-loop", "default memory loaded", now),
      toolCall(agent.id, "opencode.permission", "SUCCESS", permission.label, opencodePermissionSummary(permission), now),
      toolCall(agent.id, "opencode.run", "RUNNING", `opencode --model ${settings.model}`, "waiting for OpenCode", now),
    ];
  }
  return [
    toolCall(agent.id, "agent.memory", "SUCCESS", "shared-agent-loop", "default memory loaded", now),
    toolCall(agent.id, "agent.permission", "SUCCESS", permission.label, "prompt-scoped permission policy", now),
    toolCall(agent.id, text.startsWith("/") ? "slash.command" : "agent.adapter", "RUNNING", text || "(empty)", "waiting for output", now),
  ];
}

function completedToolCallsFor(agent, text, pendingCalls = [], responseCalls = [], directiveResult = {}) {
  const byKey = new Map();
  for (const call of normalizeResponseToolCalls(pendingCalls)) {
    const nextStatus = call.status === "RUNNING" || call.status === "QUEUED" ? "SUCCESS" : call.status;
    byKey.set(`${call.toolName}:${call.input}`, {
      ...call,
      status: nextStatus,
      output: call.status === "RUNNING" ? `completed by ${agent.name}` : call.output,
    });
  }
  for (const call of normalizeResponseToolCalls(responseCalls)) {
    byKey.set(`${call.toolName}:${call.input}`, call);
  }
  for (const call of agentTextHookCallsForAgent(agent, directiveResult.text || "", "SUCCESS")) {
    byKey.set(`${call.toolName}:${call.input}`, call);
  }
  for (const transfer of directiveResult.attachments || []) {
    byKey.set(`agent.file:${transfer.name}`, toolCall(
      agent.id,
      "agent.file",
      "SUCCESS",
      transfer.name,
      transfer.mimeType || "file sent to phone",
    ));
  }
  const created = [...(directiveResult.createdAgents || []), ...(directiveResult.createdTeams || [])];
  if (created.length) {
    byKey.set(`agent.create:${created.map((item) => item.name).join(",")}`, toolCall(
      agent.id,
      "agent.create",
      "SUCCESS",
      "agent directive",
      `created ${created.map((item) => item.name).join(", ")}`,
    ));
  }
  const removed = directiveResult.removedAgents || [];
  if (removed.length) {
    byKey.set(`agent.remove:${removed.map((item) => item.agent?.name).join(",")}`, toolCall(
      agent.id,
      "agent.remove",
      "SUCCESS",
      "agent directive",
      `removed ${removed.map((item) => item.agent?.name).filter(Boolean).join(", ")}`,
    ));
  }
  if (!byKey.size) {
    byKey.set("agent.route", toolCall(
      agent.id,
      text.startsWith("/") ? "slash.command" : "agent.adapter",
      "SUCCESS",
      text || "(empty)",
      `routed to ${agent.name}`,
    ));
  }
  return [...byKey.values()].slice(-10);
}

function agentTextHookCallsForAgent(agent, text = "", status = "RUNNING", startedAt = Date.now()) {
  const root = rootAdapterFor(agent);
  const prefix = agentToolPrefix(root.id);
  return agentTextHookCalls(agent.id, prefix, text, status, startedAt);
}

function agentTextHookCalls(agentId, prefix, text = "", status = "RUNNING", startedAt = Date.now()) {
  const cleaned = boundedText(cleanCliReply(text), 1400);
  if (!cleaned) return [];
  const calls = [];
  for (const action of agentActionHintsFromText(cleaned, prefix)) {
    calls.push(toolCall(agentId, action.toolName, status, action.input, action.output, startedAt));
  }
  if (looksLikePlanText(cleaned)) {
    calls.push(toolCall(agentId, `${prefix}.plan`, status, "agent plan", summarizeForAction(cleaned), startedAt));
  }
  if (looksLikeUserQuestionText(cleaned)) {
    calls.push(toolCall(agentId, `${prefix}.ask`, status, "user confirmation", summarizeForAction(cleaned), startedAt));
  }
  return calls;
}

function agentActionHintsFromText(text = "", prefix = "agent") {
  const value = String(text || "");
  const summary = summarizeForAction(value);
  const hints = [];
  const patterns = [
    { regex: /(auto.?compact|compact|compress|compaction|context window|自动压缩|压缩上下文|上下文压缩)/i, toolName: `${prefix}.compact`, input: "context", output: summary },
    { regex: /(search|grep|rg|ripgrep|find|lookup|检索|搜索|查找)/i, toolName: `${prefix}.search`, input: "search", output: summary },
    { regex: /(create|created|new file|mkdir|spawn|生成|创建|新建)/i, toolName: `${prefix}.create`, input: "create", output: summary },
    { regex: /(edit|edited|write|update|patch|save|modify|changed|修改|编辑|写入|更新|保存)/i, toolName: `${prefix}.edit`, input: "edit", output: summary },
    { regex: /(build|assemble|compile|构建|编译)/i, toolName: `${prefix}.build`, input: "build", output: summary },
    { regex: /(test|pytest|junit|测试|单测)/i, toolName: `${prefix}.test`, input: "test", output: summary },
    { regex: /(install|adb|device|simulator|安装|刷机|真机|模拟器)/i, toolName: `${prefix}.install`, input: "install", output: summary },
    { regex: /(run|exec|command|shell|invoke|执行|运行|命令)/i, toolName: `${prefix}.run`, input: "run", output: summary },
    { regex: /(read|inspect|open|list|scan|load|读取|查看|打开|列出|扫描)/i, toolName: `${prefix}.read`, input: "read", output: summary },
  ];
  for (const pattern of patterns) {
    if (pattern.regex.test(value)) hints.push(pattern);
  }
  return hints.slice(0, 4);
}

function firstAgentTextAction(agentId, text = "", status = "RUNNING", startedAt = Date.now()) {
  const prefix = agentToolPrefix(agentId);
  const hooks = agentTextHookCalls(agentId, prefix, text, status, startedAt);
  const hook = hooks[0];
  if (!hook) return null;
  const isAsk = hook.toolName.endsWith(".ask");
  return {
    text: isAsk ? "Asking user..." : "Planning...",
    toolName: hook.toolName,
    input: hook.input,
    output: hook.output,
  };
}

function looksLikePlanText(text = "") {
  const value = String(text || "").trim();
  if (!value) return false;
  const lower = value.toLowerCase();
  return /(^|\n)\s*(plan|implementation plan|proposed plan|todo|next steps?)\s*[:：]/i.test(value) ||
    /(^|\n)\s*(计划|执行计划|实现计划|方案|步骤|下一步|待办)\s*[:：]/i.test(value) ||
    /(^|\n)\s*(\d+[\.)、]|[-*])\s+.{0,36}(检查|实现|修改|运行|验证|提交|deploy|build|test|verify|implement|update|edit|run|ship)/i.test(value) ||
    /\b(i will|i'll|next i(?:'ll| will)|first i(?:'ll| will)|then i(?:'ll| will))\b/i.test(lower) ||
    /(我会|我先|接下来我|然后我|第一步|第二步|最后我).{0,80}(检查|实现|修改|运行|验证|提交|刷|发布)/i.test(value);
}

function looksLikeUserQuestionText(text = "") {
  const value = String(text || "").trim();
  if (!value) return false;
  return /(\?|？)\s*$/m.test(value) ||
    /(请确认|确认后|需要你确认|需要确认|是否继续|是否要|要不要|可以吗|行吗|对吗|选哪个|哪一个|你希望|你要我|我可以继续|我继续|批准|允许|同意)/i.test(value) ||
    /\b(approve|approval|confirm|permission|should i|shall i|do you want me to|would you like me to|which option|continue\?)\b/i.test(value);
}

function replaceMessage(message) {
  const index = state.messages.findIndex((item) => item.id === message.id);
  if (index >= 0) state.messages[index] = message;
  else state.messages.push(message);
  pruneMessages();
}

async function routeTeamMessage(team, payload) {
  const text = String(payload.text || "").trim();
  const conversationId = sanitizeText(payload.conversationId, "", 140);
  mergeClientConversationContext(payload.conversationContext, team.id, conversationId);
  const userMessage = {
    id: id(),
    authorId: "you",
    kind: "USER",
    text: text || "sent to team",
    createdAt: Date.now(),
    targetAgentId: team.id,
    conversationId,
    attachments: payload.attachments || [],
    toolCalls: [],
  };
  state.messages.push(userMessage);
  pruneMessages();

  const speakers = teamSpeakers(team);
  const replies = [];
  for (const speaker of speakers) {
    const response = normalizeAgentResponse(await responseFor(speaker, text, { team, currentMessageId: userMessage.id, ...runtimeContextFor(speaker, payload) }));
    const directiveResult = await applyAgentDirectives(speaker, response.text, { currentTeam: team });
    const reply = {
      id: id(),
      authorId: speaker.id,
      kind: "AGENT",
      text: directiveResult.text,
      createdAt: Date.now(),
      targetAgentId: team.id,
      conversationId,
      attachments: [],
      toolCalls: completedToolCallsFor(
        speaker,
        text,
        [toolCall(speaker.id, text.startsWith("/") ? "team.slash" : "team.group", "SUCCESS", text || "(empty)", `posted to ${team.name}`)],
        response.toolCalls,
        directiveResult,
      ),
    };
    state.messages.push(reply);
    pruneMessages();
    replies.push(reply);
  }

  const finalReply = replies.at(-1) || {
    id: id(),
    authorId: team.adminAgentId,
    kind: "AGENT",
    text: `${team.name} has no available members yet.`,
    createdAt: Date.now(),
    targetAgentId: team.id,
    conversationId,
    attachments: [],
    toolCalls: [],
  };
  if (!replies.length) {
    state.messages.push(finalReply);
    pruneMessages();
  }
  heartbeat(team.name, `Handled group message with ${speakers.length} speaker(s).`);
  updateCodexContextEstimate();
  schedulePrivateBridgeStateSave();
  console.log(`Team message routed to ${team.name}: ${text || "(empty)"}`);
  return finalReply;
}

function mergeClientConversationContext(messages = [], targetId = "", conversationId = "") {
  if (!Array.isArray(messages) || !messages.length) return;
  const normalized = messages
    .map(normalizePersistedMessage)
    .filter(Boolean)
    .filter((message) => messageBelongsToTarget(message, targetId))
    .filter((message) => !conversationId || messageConversationId(message, targetId) === conversationId);
  if (!normalized.length) return;
  const byId = new Map(state.messages.map((message) => [message.id, message]));
  for (const message of normalized) byId.set(message.id, message);
  state.messages = [...byId.values()]
    .sort((left, right) => (left.createdAt || 0) - (right.createdAt || 0) || String(left.id).localeCompare(String(right.id)))
    .slice(-MAX_IN_MEMORY_MESSAGES);
}

function messageBelongsToTarget(message, targetId) {
  if (!targetId) return true;
  if (findTeam(targetId)) return message.targetAgentId === targetId || message.authorId === targetId;
  if (findTeam(message.targetAgentId)) return false;
  if (message.kind === "USER") return message.targetAgentId === targetId;
  if (message.kind === "AGENT") return message.authorId === targetId && (!message.targetAgentId || message.targetAgentId === "you");
  if (message.kind === "SYSTEM") return message.targetAgentId === targetId || (!message.targetAgentId && message.authorId === targetId);
  return false;
}

function messageConversationId(message, targetId = "") {
  return sanitizeText(message?.conversationId, "", 140) || defaultConversationId(targetId);
}

function defaultConversationId(targetId = "") {
  return targetId ? `default:${targetId}` : "";
}

async function performMessage(session, encryptedBody) {
  const decrypted = decryptWithSessionKeys(encryptedBody, session);
  if (!decrypted) {
    return {
      status: 401,
      body: {
        error: "session_key_mismatch",
        detail: "Pairing changed or expired. Forget this computer and pair again.",
      },
    };
  }
  const { payload, key } = decrypted;
  const reply = await routeMessage(payload);
  broadcast("agent.output", reply);
  return {
    status: 200,
    body: encryptJson({ version: VERSION, type: "message.accepted", id: id(), createdAt: Date.now(), payload: reply }, key),
  };
}

async function responseFor(agent, text, extraContext = {}) {
  const slash = normalizeSlashCommandText(text);
  const command = slash.command;
  if (command === "/status") {
    const versions = await Promise.all([
      commandVersion("codex"),
      commandVersion("gemini"),
      commandVersion("opencode"),
      commandVersion("claude"),
      antigravityVersion(),
    ]);
    return versions.join("\n");
  }
  if (command === "/agents") {
    return allAgents()
      .map((item) => `${item.name}: ${item.status}, parent=${item.parentId || "none"}`)
      .join("\n");
  }
  if (command === "/parent") {
    const parent = agent.parentId ? findAgent(agent.parentId) : null;
    return parent
      ? `${agent.name} is under ${parent.name}. Root adapter: ${rootAdapterFor(agent).name}.`
      : `${agent.name} has no parent. Root adapter: ${rootAdapterFor(agent).name}.`;
  }
  if (command === "/team") {
    return allTeams()
      .map((team) => `${team.name}: admin=${agentName(team.adminAgentId)}, members=${team.memberIds.length}, shared=${team.sharedProfile}`)
      .join("\n");
  }
  if (command === "/team-create") {
    const result = await createPersistentTeam(agent, parseTeamCommand(slash.normalizedText, agent));
    return result.created
      ? `Created persistent team: ${result.team.name}. It will appear as a group chat in the Android app.`
      : `Persistent team already exists: ${result.team.name}.`;
  }
  if (command === "/tools") {
    return `${agent.name} tools: ${agent.tools.join(", ")}`;
  }
  if (command === "/model") {
    return runtimeCommandSummary(agent, extraContext, "model");
  }
  if (command === "/reasoning") {
    return runtimeCommandSummary(agent, extraContext, "reasoning");
  }
  if (command === "/permissions") {
    return runtimeCommandSummary(agent, extraContext, "permissions");
  }
  if (command === "/context") {
    const runtime = extraContext.runtimeSettings || runtimeSettingsForAgent(rootAdapterFor(agent));
    return `${agent.name} context: ${runtime.contextUsedTokens || 0} / ${runtime.contextLimitTokens || 0} tokens.`;
  }
  if (command === "/compact") {
    return "Context compaction is automatic on the desktop side. I will keep future prompts compact and scoped to this conversation.";
  }
  if (command === "/diagnostics") {
    return `Bridge ${VERSION}; mode=${RELAY_URL ? "relay" : "direct"}; pairedDevices=${sessions.size}; relayConfigured=${Boolean(RELAY_URL)}; agents=${allAgents().length}.`;
  }
  if (command === "/memory") {
    return await safeRead(join(ROOT, "MEMORY.md"));
  }
  if (command === "/heartbeat") {
    const today = new Date().toISOString().slice(0, 10);
    return await safeRead(join(ROOT, "daily", `${today}.md`));
  }
  if (command === "/api") {
    return "Bridge API online: /v1/pairing-challenge /v1/pair /v1/stream /v1/messages /v1/files /v1/projects/{projectId}/documents/{documentId} /v1/slash-commands";
  }
  if (command === "/files") {
    return "File bridge ready. Attach files from the composer, or agents can send AGENT_CONTROL_SEND_FILE directives back to the phone.";
  }
  if (command === "/photo") {
    return "Photo bridge ready. Use the camera action or attach images/videos; previews and long-press saving are supported in the app.";
  }
  if (command === "/handoff") {
    return `Handoff noted for ${agent.name}. Shared queue ownership should be updated by the desktop agent when work is delegated.`;
  }
  if (command === "/approve") {
    return `Approved ${agent.name} to continue the current task.`;
  }
  if (command === "/pause") {
    return `Pause requested for ${agent.name}.`;
  }
  if (command === "/resume") {
    return `Resume requested for ${agent.name}.`;
  }
  if (command === "/stop") {
    return `Stop requested for ${agent.name}.`;
  }
  if (command === "/new") {
    return "Started a new-command request. For a truly separate phone thread, use the header + button; the bridge already isolates messages by conversation id.";
  }
  if (command === "/clear") {
    return "Clear is local to the phone thread. Use the header + button for a fresh conversation, or the app command registry for local clear.";
  }
  if (command === "/" || command === "/help") {
    return commandHelpText(slash.rest, agent);
  }
  if (command === "/spawn") {
    const result = await createPersistentSubagent(agent, parseSpawnCommand(slash.normalizedText, agent));
    return result.created
      ? `Created persistent subagent: ${result.agent.name}. It will stay in the Android app after bridge restarts.`
      : `Persistent subagent already exists: ${result.agent.name}.`;
  }
  if (command === "/dismiss") {
    const result = await removePersistentSubagent(agent, parseRemoveSubagentCommand(slash.normalizedText, agent));
    return result.removed
      ? `Removed persistent subagent: ${result.agent.name}${result.removedIds.length > 1 ? ` and ${result.removedIds.length - 1} child subagent(s)` : ""}.`
      : `No subagent removed: ${result.reason || "specify a subagent name or id."}`;
  }
  if (command) {
    const agentCommand = agentSlashCommand(agent, command);
    if (!agentCommand) return `Unknown command ${command}. Try /help.`;
  }

  const adapterAgent = rootAdapterFor(agent);
  const context = {
    targetAgent: agent,
    adapterAgent,
    ...(adapterAgent.id === agent.id ? {} : { subagent: agent, adapterAgent }),
    ...extraContext,
  };
  if (adapterAgent.id === "codex" && !DISABLE_REAL_AGENTS) {
    return await codexReply(text, context);
  }
  if (adapterAgent.id === "claude" && !DISABLE_REAL_AGENTS) {
    return await claudeReply(text, context);
  }
  if (adapterAgent.id === "antigravity" && !DISABLE_REAL_AGENTS) {
    return await antigravityReply(text, context);
  }
  if (adapterAgent.id === "gemini_cli" && !DISABLE_REAL_AGENTS) {
    return await geminiReply(text, context);
  }
  if (adapterAgent.id === "opencode" && !DISABLE_REAL_AGENTS) {
    return await opencodeReply(text, context);
  }
  return `${agent.name} received: ${text || "(empty)"}`;
}

function normalizeSlashCommandText(text = "") {
  const raw = String(text || "").trim().replace(/^\uFF0F/u, "/");
  if (!raw.startsWith("/")) return { raw, command: "", rest: "", normalizedText: raw };
  if (raw === "/") return { raw, command: "/", rest: "", normalizedText: "/" };
  const withoutTrailingPunctuation = raw.replace(/[.。!！?？,，;；]+$/u, "");
  const match = withoutTrailingPunctuation.match(/^\/([^\s@.。!！?？,，;；/]+)(?:@[^\s]+)?(?:\s+([\s\S]*))?$/u);
  if (!match) {
    const firstToken = withoutTrailingPunctuation.split(/\s+/u)[0] || "/";
    const command = canonicalSlashCommand(firstToken.split("@")[0].toLowerCase());
    const rest = withoutTrailingPunctuation.slice(firstToken.length).trim();
    return { raw, command, rest, normalizedText: `${command}${rest ? ` ${rest}` : ""}` };
  }
  const command = canonicalSlashCommand(`/${match[1].toLowerCase()}`);
  const rest = String(match[2] || "").trim();
  return { raw, command, rest, normalizedText: `${command}${rest ? ` ${rest}` : ""}` };
}

function canonicalSlashCommand(command = "") {
  const normalized = String(command || "")
    .trim()
    .toLowerCase()
    .replace(/^\/+/, "/")
    .replace(/_/g, "-");
  const aliases = {
    "/start": "/help",
    "/commands": "/help",
    "/cmds": "/help",
    "/ls": "/agents",
    "/agent": "/agents",
    "/who": "/agents",
    "/teams": "/team",
    "/group": "/team",
    "/groups": "/team",
    "/newchat": "/new",
    "/new-chat": "/new",
    "/new-session": "/new",
    "/reset": "/new",
    "/models": "/model",
    "/set-model": "/model",
    "/think": "/reasoning",
    "/thinking": "/reasoning",
    "/reason": "/reasoning",
    "/permission": "/permissions",
    "/perms": "/permissions",
    "/sandbox": "/permissions",
    "/compact-context": "/compact",
    "/memory-compact": "/compact",
    "/health": "/diagnostics",
    "/diag": "/diagnostics",
    "/diagnostic": "/diagnostics",
    "/file": "/files",
    "/upload": "/files",
    "/attach": "/files",
    "/image": "/photo",
    "/pic": "/photo",
    "/photos": "/photo",
    "/tool": "/tools",
    "/continue": "/resume",
    "/cancel": "/stop",
    "/abort": "/stop",
    "/mem": "/memory",
    "/logs": "/heartbeat",
    "/log": "/heartbeat",
    "/docs": "/api",
    "/despawn": "/dismiss",
    "/remove": "/dismiss",
    "/delete": "/dismiss",
    "/retire": "/dismiss",
    "/remove-agent": "/dismiss",
    "/remove-subagent": "/dismiss",
    "/delete-agent": "/dismiss",
    "/delete-subagent": "/dismiss",
    "/dismiss-agent": "/dismiss",
    "/dismiss-subagent": "/dismiss",
  };
  return aliases[normalized] || normalized;
}

function commandHelpText(topic = "", agent = null) {
  const normalizedTopic = topic ? canonicalSlashCommand(String(topic).trim().startsWith("/") ? topic.trim() : `/${topic.trim()}`) : "";
  const descriptions = {
    "/status": "Show bridge and local CLI status.",
    "/agents": "List visible agents and subagents.",
    "/team": "List team chats and shared group context.",
    "/spawn": "Create a persistent subagent: /spawn Name | role.",
    "/dismiss": "Remove a persistent subagent: /dismiss Name, or run /dismiss inside that subagent chat.",
    "/team-create": "Create a persistent team: /team-create Name | purpose.",
    "/model": "Show this agent's current model and model ids.",
    "/reasoning": "Show this agent's reasoning levels.",
    "/permissions": "Show this agent's permission modes.",
    "/context": "Show current context usage.",
    "/compact": "Request compact-context behavior.",
    "/diagnostics": "Show safe bridge diagnostics summary.",
    "/new": "Start a fresh app conversation from the header + button; this command explains the current thread boundary.",
    "/files": "Show file transfer support.",
    "/photo": "Show photo/video attachment support.",
    "/help": "Show commands; supports /help model.",
  };
  if (normalizedTopic && descriptions[normalizedTopic]) return `${normalizedTopic}: ${descriptions[normalizedTopic]}`;
  if (normalizedTopic) {
    const agentCommand = agentSlashCommand(agent, normalizedTopic);
    if (agentCommand) return `${agentCommand.trigger}: ${agentCommand.label || "Agent command"}`;
  }
  return commandsForAgent(agent).map((item) => item.trigger).join(" ");
}

function runtimeCommandSummary(agent, context = {}, field = "model") {
  const runtime = context.runtimeSettings || runtimeSettingsForAgent(rootAdapterFor(agent));
  if (field === "model") {
    return `${agent.name} model: ${runtime.model}. Available: ${optionSummary(runtime.modelOptions)}.`;
  }
  if (field === "reasoning") {
    return `${agent.name} reasoning: ${runtime.reasoningEffort}. Available: ${optionSummary(runtime.reasoningOptions)}.`;
  }
  return `${agent.name} permissions: ${runtime.permissionMode}. Available: ${optionSummary(runtime.permissionOptions)}.`;
}

function optionSummary(options = []) {
  const values = Array.isArray(options) ? options : [];
  return values.map((option) => option?.id || option?.label).filter(Boolean).join(", ") || "none reported";
}

function allAgents() {
  return [...registeredAgentDefinitions(), ...state.dynamicAgents];
}

function registeredAgentDefinitions() {
  return agentDefinitions.filter((agent) => {
    if (agent.id === "codex") return true;
    return agent.status === "ONLINE";
  });
}

function allSlashCommands() {
  return uniqueSlashCommands([
    ...commands,
    ...allAgents().flatMap((agent) => normalizeAgentSlashCommands(agent.slashCommands || agent.commands)),
  ]);
}

function commandsForAgent(agent = null) {
  return uniqueSlashCommands([
    ...commands,
    ...normalizeAgentSlashCommands(agent?.slashCommands || agent?.commands),
  ]);
}

function agentSlashCommand(agent, command) {
  const normalized = canonicalSlashCommand(command);
  return normalizeAgentSlashCommands(agent?.slashCommands || agent?.commands)
    .find((item) => canonicalSlashCommand(item.trigger) === normalized);
}

function uniqueSlashCommands(list = []) {
  const result = [];
  for (const item of list) {
    const normalized = normalizeSlashCommandSpec(item);
    if (!normalized) continue;
    if (!result.some((existing) => existing.trigger === normalized.trigger)) result.push(normalized);
  }
  return result;
}

function findAgent(agentId) {
  return allAgents().find((item) => item.id === agentId);
}

function rootAdapterFor(agent) {
  let current = agent;
  const seen = new Set();
  while (current?.kind === "SUBAGENT" && current.parentId && !seen.has(current.id)) {
    seen.add(current.id);
    current = findAgent(current.parentId) || agentDefinitions[0];
  }
  return current || agentDefinitions[0];
}

function parseSpawnCommand(text, parent) {
  const raw = String(text || "").replace(/%s/gi, " ").replace(/^\/spawn\b/i, "").trim();
  if (raw.startsWith("{")) {
    const parsed = parseJsonSpec(raw);
    if (parsed) return parsed;
  }
  const [namePart, rolePart] = raw.split("|").map((item) => item.trim());
  return {
    name: namePart || `${parent.name} Child`,
    role: rolePart || `persistent helper under ${parent.name}`,
  };
}

function parseRemoveSubagentCommand(text, actor) {
  const raw = String(text || "")
    .replace(/%s/gi, " ")
    .replace(/^\/[^\s]+\s*/u, "")
    .trim();
  if (raw.startsWith("{")) {
    const parsed = parseJsonSpec(raw);
    if (parsed) return parsed;
  }
  if (!raw && actor?.kind === "SUBAGENT") return { id: actor.id };
  return { name: raw };
}

async function applyAgentDirectives(actor, responseText, options = {}) {
  const { text, subagentSpecs, removeSubagentSpecs, teamSpecs, teamMessageSpecs, fileSpecs } = parseAgentDirectives(responseText);
  const createdAgents = [];
  const existingAgents = [];
  const removedAgents = [];
  const removeFailures = [];
  const createdTeams = [];
  const existingTeams = [];
  const postedTeamMessages = [];
  const attachments = [];

  for (const spec of subagentSpecs) {
    const result = await createPersistentSubagent(actor, spec);
    if (result.created) createdAgents.push(result.agent);
    else existingAgents.push(result.agent);
  }
  for (const spec of removeSubagentSpecs) {
    const result = await removePersistentSubagent(actor, spec);
    if (result.removed) removedAgents.push(result);
    else removeFailures.push(result);
  }
  for (const spec of teamSpecs) {
    const result = await createPersistentTeam(actor, spec);
    if (result.created) createdTeams.push(result.team);
    else existingTeams.push(result.team);
  }
  for (const spec of teamMessageSpecs) {
    const message = await addTeamMessage(actor, spec, options.currentTeam);
    if (message) postedTeamMessages.push(message);
  }
  for (const spec of fileSpecs) {
    const transfer = await agentFileTransfer(actor, spec);
    if (transfer) attachments.push(transfer);
  }

  const notes = [
    ...createdAgents.map((child) => `Created persistent subagent: ${child.name}.`),
    ...existingAgents.map((child) => `Persistent subagent already exists: ${child.name}.`),
    ...removedAgents.map((result) => `Removed persistent subagent: ${result.agent.name}${result.removedIds.length > 1 ? ` and ${result.removedIds.length - 1} child subagent(s)` : ""}.`),
    ...removeFailures.map((result) => `No subagent removed: ${result.reason || "not found"}.`),
    ...createdTeams.map((team) => `Created persistent team: ${team.name}.`),
    ...existingTeams.map((team) => `Persistent team already exists: ${team.name}.`),
    ...postedTeamMessages.map((message) => `Posted to team: ${findTeam(message.targetAgentId)?.name || message.targetAgentId}.`),
    ...attachments.map((transfer) => `Sent file: ${transfer.name}.`),
  ];
  return {
    text: [text.trim(), ...notes].filter(Boolean).join("\n\n") || "Done.",
    createdAgents,
    removedAgents,
    createdTeams,
    postedTeamMessages,
    attachments,
  };
}

function parseAgentDirectives(value) {
  const subagentSpecs = [];
  const removeSubagentSpecs = [];
  const teamSpecs = [];
  const teamMessageSpecs = [];
  const fileSpecs = [];
  const lines = String(value || "").split("\n");
  const visible = [];
  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith(SUBAGENT_DIRECTIVE)) {
      const raw = trimmed.slice(SUBAGENT_DIRECTIVE.length).trim();
      const parsed = parseJsonSpec(raw) || { name: raw };
      subagentSpecs.push(parsed);
    } else if (trimmed.startsWith(REMOVE_SUBAGENT_DIRECTIVE)) {
      const raw = trimmed.slice(REMOVE_SUBAGENT_DIRECTIVE.length).trim();
      const parsed = parseJsonSpec(raw) || { name: raw };
      removeSubagentSpecs.push(parsed);
    } else if (trimmed.startsWith(TEAM_DIRECTIVE)) {
      const raw = trimmed.slice(TEAM_DIRECTIVE.length).trim();
      const parsed = parseJsonSpec(raw) || { name: raw };
      teamSpecs.push(parsed);
    } else if (trimmed.startsWith(TEAM_MESSAGE_DIRECTIVE)) {
      const raw = trimmed.slice(TEAM_MESSAGE_DIRECTIVE.length).trim();
      const parsed = parseJsonSpec(raw) || { text: raw };
      teamMessageSpecs.push(parsed);
    } else if (trimmed.startsWith(FILE_DIRECTIVE)) {
      const raw = trimmed.slice(FILE_DIRECTIVE.length).trim();
      const parsed = parseJsonSpec(raw) || { path: raw };
      fileSpecs.push(parsed);
    } else {
      visible.push(line);
    }
  }
  return { text: visible.join("\n"), subagentSpecs, removeSubagentSpecs, teamSpecs, teamMessageSpecs, fileSpecs };
}

async function applySubagentDirectives(parent, responseText) {
  const result = await applyAgentDirectives(parent, responseText);
  return {
    text: result.text,
    created: result.createdAgents,
  };
}

function parseSubagentDirectives(value) {
  const parsed = parseAgentDirectives(value);
  return { text: parsed.text, specs: parsed.subagentSpecs };
}

async function createPersistentSubagent(parent, spec = {}) {
  const name = sanitizeText(spec.name, `${parent.name} Child`, 48);
  const existing = state.dynamicAgents.find((agent) =>
    agent.parentId === parent.id && agent.name.toLowerCase() === name.toLowerCase()
  );
  if (existing) return { agent: existing, created: false };

  const role = sanitizeText(spec.role || spec.description, `persistent helper under ${parent.name}`, 180);
  const tools = sanitizeTools(spec.tools);
  const child = {
    id: uniqueSubagentId(parent.id, name),
    name,
    kind: "SUBAGENT",
    role,
    status: "ONLINE",
    parentId: parent.id,
    teamId: parent.teamId || "core",
    tools: tools.length ? tools : ["direct-chat", "report", "handoff"],
    modelOptions: normalizeOptionalRuntimeOptionList(spec.modelOptions || spec.models, 128000),
    reasoningOptions: normalizeOptionalRuntimeOptionList(spec.reasoningOptions || spec.reasoning, 0),
    permissionOptions: normalizeOptionalRuntimeOptionList(spec.permissionOptions || spec.permissions, 0),
    slashCommands: normalizeAgentSlashCommands(spec.slashCommands || spec.commands),
    canSpawnChildren: true,
  };
  state.dynamicAgents.push(child);
  await savePersistedAgents();
  heartbeat("team", `Spawned persistent subagent ${child.name} under ${parent.name}`);
  broadcast("agent.spawned", child);
  broadcast("team.changed", snapshot());
  return { agent: child, created: true };
}

async function removePersistentSubagent(actor, spec = {}) {
  const target = resolveSubagentRemovalTarget(actor, spec);
  if (!target) {
    return { removed: false, reason: "matching persistent subagent not found", removedIds: [] };
  }
  if (!canRemoveSubagent(actor, target)) {
    return { removed: false, reason: `${actor.name} cannot remove ${target.name}`, agent: target, removedIds: [] };
  }

  const fallbackRoot = rootAdapterFor(actor);
  const removedIds = collectSubagentSubtreeIds(target.id);
  const removeSet = new Set(removedIds);
  state.dynamicAgents = state.dynamicAgents.filter((agent) => !removeSet.has(agent.id));
  await savePersistedAgents();
  const teamsChanged = updateTeamsAfterAgentRemoval(removeSet, fallbackRoot);
  if (teamsChanged) await savePersistedTeams();
  heartbeat("team", `Removed persistent subagent ${target.name}${removedIds.length > 1 ? ` with ${removedIds.length - 1} child subagent(s)` : ""}`);
  broadcast("agent.removed", {
    id: target.id,
    ids: removedIds,
    name: target.name,
    removedByAgentId: actor.id,
  });
  broadcast("team.changed", snapshot());
  return { removed: true, agent: target, removedIds };
}

function resolveSubagentRemovalTarget(actor, spec = {}) {
  const idValue = sanitizeText(spec.id || spec.agentId || spec.subagentId || spec.targetId, "", 80);
  const nameValue = sanitizeText(spec.name || spec.agent || spec.subagent || spec.target || spec.label, "", 80);
  if (!idValue && !nameValue && actor?.kind === "SUBAGENT") return actor;
  const loweredName = nameValue.toLowerCase();
  const slugName = slugify(nameValue);
  const candidates = state.dynamicAgents.filter((agent) => {
    if (idValue && agent.id === idValue) return true;
    if (!nameValue) return false;
    return agent.name.toLowerCase() === loweredName || slugify(agent.name) === slugName;
  });
  return candidates.find((agent) => canRemoveSubagent(actor, agent)) || candidates[0] || null;
}

function canRemoveSubagent(actor, target) {
  if (!actor || !target || target.kind !== "SUBAGENT") return false;
  if (actor.id === target.id) return true;
  if (isSubagentDescendantOf(target, actor.id)) return true;
  return rootAdapterFor(target).id === actor.id;
}

function isSubagentDescendantOf(target, ancestorId) {
  let current = target;
  const seen = new Set();
  while (current?.kind === "SUBAGENT" && current.parentId && !seen.has(current.id)) {
    if (current.parentId === ancestorId || current.id === ancestorId) return true;
    seen.add(current.id);
    current = findAgent(current.parentId);
  }
  return false;
}

function collectSubagentSubtreeIds(rootId) {
  const removeSet = new Set([rootId]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const agent of state.dynamicAgents) {
      if (!removeSet.has(agent.id) && removeSet.has(agent.parentId)) {
        removeSet.add(agent.id);
        changed = true;
      }
    }
  }
  return [...removeSet];
}

function updateTeamsAfterAgentRemoval(removeSet, fallbackRoot) {
  let changed = false;
  state.dynamicTeams = state.dynamicTeams.map((team) => {
    const keptMembers = team.memberIds.filter((memberId) => !removeSet.has(memberId) && findAgent(memberId));
    let adminAgentId = (!removeSet.has(team.adminAgentId) && findAgent(team.adminAgentId))
      ? team.adminAgentId
      : keptMembers[0] || fallbackRoot?.id || "codex";
    if (!findAgent(adminAgentId)) adminAgentId = "codex";
    const memberIds = [...new Set([adminAgentId, ...keptMembers])].filter((memberId) => findAgent(memberId));
    const next = {
      ...team,
      adminAgentId,
      memberIds: memberIds.length ? memberIds : ["codex"],
      createdByAgentId: removeSet.has(team.createdByAgentId) ? adminAgentId : team.createdByAgentId,
      updatedAt: Date.now(),
    };
    const teamChanged = next.adminAgentId !== team.adminAgentId ||
      next.createdByAgentId !== team.createdByAgentId ||
      next.memberIds.join("|") !== team.memberIds.join("|");
    changed = changed || teamChanged;
    return teamChanged ? next : team;
  });
  return changed;
}

async function createPersistentTeam(creator, spec = {}) {
  const name = sanitizeText(spec.name, `${creator.name} Team`, 56);
  const existing = state.dynamicTeams.find((team) => team.name.toLowerCase() === name.toLowerCase());
  if (existing) return { team: existing, created: false };

  const memberIds = normalizeTeamMembers(spec.members || spec.memberIds, creator);
  const now = Date.now();
  const team = {
    id: uniqueTeamId(name),
    name,
    adminAgentId: findAgent(spec.adminAgentId)?.id || creator.id,
    memberIds,
    sharedProfile: sanitizeText(
      spec.sharedProfile || spec.profile || spec.purpose,
      `Shared team room created by ${creator.name}. Members can read this profile, exchange messages, and create follow-up subagents or teams.`,
      260,
    ),
    createdByAgentId: creator.id,
    purpose: sanitizeText(spec.purpose || spec.role || spec.description, "", 220),
    sharedDocuments: sanitizeStringList(spec.sharedDocuments || spec.documents || ["MEMORY.md", "QUEUE.md"], 8, 80),
    createdAt: now,
    updatedAt: now,
    canAgentsPost: spec.canAgentsPost !== false,
  };
  state.dynamicTeams.push(team);
  await savePersistedTeams();
  heartbeat("team", `Created persistent team ${team.name} by ${creator.name}`);
  broadcast("team.created", team);
  broadcast("team.changed", snapshot());
  return { team, created: true };
}

async function addTeamMessage(actor, spec = {}, currentTeam = null) {
  const team = findTeam(spec.teamId) || findTeamByName(spec.teamName || spec.name) || currentTeam || findTeam(actor.teamId) || findTeam("core");
  if (!team) return null;
  const author = findAgent(spec.authorId) || actor;
  const text = sanitizeText(spec.text || spec.message || spec.body, "", 2000);
  if (!text) return null;
  const message = {
    id: id(),
    authorId: author.id,
    kind: "AGENT",
    text,
    createdAt: Date.now(),
    targetAgentId: team.id,
    attachments: [],
    toolCalls: [
      {
        id: id(),
        agentId: actor.id,
        toolName: "team.message",
        status: "SUCCESS",
        input: TEAM_MESSAGE_DIRECTIVE,
        output: `posted to ${team.name}`,
        startedAt: Date.now(),
      },
    ],
  };
  state.messages.push(message);
  pruneMessages();
  schedulePrivateBridgeStateSave();
  broadcast("agent.output", message);
  return message;
}

async function agentFileTransfer(actor, spec = {}) {
  const rawPath = sanitizeText(spec.path || spec.file || spec.uri, "", 1200);
  const name = basename(sanitizeText(spec.name || rawPath || `agent-file-${Date.now()}.txt`, "", 180));
  const mimeType = sanitizeText(spec.mimeType || guessMimeType(name), "application/octet-stream", 120);
  let content = null;
  let uri = rawPath;
  if (spec.base64) {
    content = Buffer.from(String(spec.base64), "base64");
    uri = `inline:${name}`;
  } else if (spec.text || spec.content) {
    content = Buffer.from(String(spec.text || spec.content), "utf8");
    uri = `inline:${name}`;
  } else if (rawPath) {
    try {
      content = readFileSync(rawPath);
    } catch (error) {
      const failed = {
        id: id(),
        name,
        mimeType,
        direction: "DESKTOP_TO_PHONE",
        uri: rawPath,
        sizeLabel: `unavailable: ${firstLine(error.message)}`,
        contentBase64: "",
      };
      state.transfers.push(failed);
      return failed;
    }
  }
  if (!content) return null;
  const transfer = {
    id: id(),
    name,
    mimeType,
    direction: "DESKTOP_TO_PHONE",
    uri,
    sizeLabel: `${content.length} bytes`,
    contentBase64: content.length <= MAX_INLINE_AGENT_FILE_BYTES ? content.toString("base64") : "",
  };
  state.transfers.push(transfer);
  heartbeat(actor.name, `Sent file ${name} to Android.`);
  broadcast("file.available", transfer);
  return transfer;
}

function parseTeamCommand(text, creator) {
  const raw = String(text || "").replace(/%s/gi, " ").replace(/^\/team-create\b/i, "").trim();
  if (raw.startsWith("{")) {
    const parsed = parseJsonSpec(raw);
    if (parsed) return parsed;
  }
  const [namePart, membersPart, purposePart] = raw.split("|").map((item) => item.trim());
  return {
    name: namePart || `${creator.name} Team`,
    members: membersPart ? membersPart.split(",").map((item) => item.trim()) : undefined,
    purpose: purposePart || `Team room started by ${creator.name}`,
  };
}

function teamSpeakers(team) {
  const members = team.memberIds
    .map((memberId) => findAgent(memberId))
    .filter(Boolean);
  const admin = findAgent(team.adminAgentId);
  const ordered = [admin, ...members].filter(Boolean);
  const seenAdapters = new Set();
  const speakers = [];
  for (const member of ordered) {
    const adapter = rootAdapterFor(member);
    const key = adapter.id;
    if (seenAdapters.has(key) && speakers.length >= 1) continue;
    speakers.push(member);
    seenAdapters.add(key);
    if (speakers.length >= TEAM_ROUND_MAX_SPEAKERS) break;
  }
  return speakers;
}

function allTeams() {
  const core = coreTeam();
  return [core, ...state.dynamicTeams.filter((team) => team.id !== core.id)];
}

function coreTeam() {
  const agents = allAgents();
  const now = Date.now();
  return {
    id: "core",
    name: "Local Agent Team",
    adminAgentId: "codex",
    memberIds: agents.map((agent) => agent.id),
    sharedProfile: "One shared roster, one admin, visible agent and subagent tree.",
    createdByAgentId: "codex",
    purpose: "Default coordination room for all local agents.",
    sharedDocuments: ["MEMORY.md", "QUEUE.md", `daily/${new Date().toISOString().slice(0, 10)}.md`],
    createdAt: now,
    updatedAt: now,
    canAgentsPost: true,
  };
}

function findTeam(teamId) {
  return allTeams().find((team) => team.id === teamId);
}

function findTeamByName(name) {
  const lowered = String(name || "").trim().toLowerCase();
  if (!lowered) return null;
  return allTeams().find((team) => team.name.toLowerCase() === lowered);
}

function uniqueTeamId(name) {
  const base = `team-${slugify(name) || id().slice(0, 8)}`.slice(0, 48);
  let candidate = base;
  let index = 2;
  while (findTeam(candidate)) {
    candidate = `${base}-${index}`.slice(0, 56);
    index += 1;
  }
  return candidate;
}

function normalizeTeamMembers(value, creator) {
  const requested = sanitizeStringList(value, 12, 80);
  const defaultCollaborators = requested.length
    ? []
    : ["codex", "claude", "gemini_cli"].filter((agentId) => findAgent(agentId));
  const ids = [creator.id, rootAdapterFor(creator).id, ...defaultCollaborators, ...requested.map(resolveAgentId)].filter(Boolean);
  const unique = [...new Set(ids)];
  return unique.length ? unique : [creator.id];
}

function resolveAgentId(value) {
  const text = String(value || "").trim();
  if (!text) return "";
  const lowered = text.toLowerCase();
  return allAgents().find((agent) =>
    agent.id.toLowerCase() === lowered ||
    agent.name.toLowerCase() === lowered ||
    slugify(agent.name) === slugify(text)
  )?.id || "";
}

function sanitizeStringList(value, maxItems = 8, maxLength = 80) {
  const list = Array.isArray(value)
    ? value
    : typeof value === "string"
      ? value.split(",")
      : [];
  return list
    .map((item) => sanitizeText(item, "", maxLength))
    .filter(Boolean)
    .slice(0, maxItems);
}

function agentName(agentId) {
  return findAgent(agentId)?.name || agentId;
}

function uniqueSubagentId(parentId, name) {
  const base = `sub-${slugify(parentId)}-${slugify(name) || id().slice(0, 8)}`.slice(0, 48);
  let candidate = base;
  let index = 2;
  while (findAgent(candidate)) {
    candidate = `${base}-${index}`.slice(0, 56);
    index += 1;
  }
  return candidate;
}

function slugify(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 24);
}

function parseJsonSpec(raw) {
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function sanitizeText(value, fallback, maxLength) {
  const text = String(value || "").trim().replace(/\s+/g, " ");
  return (text || fallback).slice(0, maxLength);
}

function sanitizeTools(value) {
  const list = Array.isArray(value)
    ? value
    : typeof value === "string"
      ? value.split(",")
      : [];
  return list
    .map((item) => sanitizeText(item, "", 32))
    .filter(Boolean)
    .slice(0, 8);
}

function subagentContextLines(context = {}) {
  const subagent = context.subagent;
  if (!subagent) return [];
  return [
    `You are replying as persistent subagent "${subagent.name}" under ${context.adapterAgent?.name || "its parent agent"}.`,
    `Subagent role: ${subagent.role}`,
  ];
}

function conversationMemoryLines(context = {}) {
  const target = context.subagent || context.targetAgent || context.adapterAgent;
  const messages = recentConversationMessages(context, target);
  if (!messages.length) return [];
  const scope = context.team
    ? `team "${context.team.name}"`
    : `"${target?.name || "this agent"}"`;
  return [
    `Recent Agent Control conversation memory for ${scope} (oldest to newest; use as memory, answer only the current user message):`,
    ...messages.map(formatMemoryMessage),
  ];
}

function sharedAgentMemoryLines() {
  const today = localDateString();
  const yesterday = localDateString(Date.now() - 24 * 60 * 60 * 1000);
  const sections = [
    sharedMemorySection("ROLES.md", join(ROOT, "ROLES.md"), 1800),
    sharedMemorySection("QUEUE.md", join(ROOT, "QUEUE.md"), 3600),
    sharedMemorySection("MEMORY.md", join(ROOT, "MEMORY.md"), 5200),
    sharedMemorySection(`daily/${today}.md`, join(ROOT, "daily", `${today}.md`), 3200),
    sharedMemorySection(`daily/${yesterday}.md`, join(ROOT, "daily", `${yesterday}.md`), 1600),
  ].filter(Boolean);
  if (!sections.length) return [];
  return [
    "Shared agent memory bootstrap (read before answering; use this as durable local context, but do not reveal private memory unless it directly helps the user):",
    ...sections,
    "End shared agent memory bootstrap.",
  ];
}

function sharedMemorySection(label, path, maxChars = MAX_SHARED_FILE_PROMPT_CHARS) {
  const raw = readSharedMemoryFile(path);
  if (!raw) return "";
  return `--- ${label} ---\n${middleCompactForPrompt(raw, maxChars)}`;
}

function readSharedMemoryFile(path) {
  try {
    return readFileSync(path, "utf8")
      .replace(/\u0000/g, "")
      .trim();
  } catch {
    return "";
  }
}

function middleCompactForPrompt(value = "", maxChars = MAX_SHARED_FILE_PROMPT_CHARS) {
  const text = String(value || "").replace(/\r\n/g, "\n").trim();
  if (text.length <= maxChars) return text;
  const headLength = Math.floor(maxChars * 0.55);
  const tailLength = maxChars - headLength;
  return `${text.slice(0, headLength).trim()}\n...\n${text.slice(-tailLength).trim()}`;
}

function localDateString(timestamp = Date.now()) {
  const date = new Date(timestamp);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function recentConversationMessages(context = {}, target = null) {
  const currentMessageId = context.currentMessageId || "";
  const teamId = context.team?.id || "";
  const targetId = target?.id || "";
  const conversationId = sanitizeText(context.conversationId, "", 140);
  return state.messages
    .filter((message) => {
      if (!message?.text || message.id === currentMessageId) return false;
      if (teamId) {
        if (conversationId && messageConversationId(message, teamId) !== conversationId) return false;
        return message.targetAgentId === teamId || message.authorId === teamId;
      }
      if (!targetId) return false;
      if (conversationId && messageConversationId(message, targetId) !== conversationId) return false;
      return messageBelongsToTarget(message, targetId);
    })
    .slice(-MAX_PROMPT_MEMORY_MESSAGES);
}

function formatMemoryMessage(message) {
  const timestamp = new Date(finiteTimestamp(message.createdAt, Date.now())).toISOString();
  return `- ${timestamp} ${participantName(message.authorId)}: ${compactForPrompt(message.text)}`;
}

function participantName(authorId) {
  if (authorId === "you") return "User";
  if (authorId === "system") return "System";
  const team = findTeam(authorId);
  return team?.name || agentName(authorId);
}

function compactForPrompt(value) {
  const text = String(value || "").replace(/\s+/g, " ").trim();
  return text.length > MAX_PROMPT_MEMORY_CHARS
    ? `${text.slice(0, MAX_PROMPT_MEMORY_CHARS)}...`
    : text;
}

function subagentDirectiveLines() {
  return [
    "Persistent subagent protocol: if a durable specialist should be created and shown in the Android app, include one separate single-line directive:",
    `${SUBAGENT_DIRECTIVE} {"name":"Short specialist name","role":"specific responsibility","tools":["direct-chat","report"],"modelOptions":[{"id":"model/id","label":"Model label"}],"permissionOptions":[{"id":"read-only","label":"Read Only"}],"slashCommands":[{"trigger":"/review","label":"Review"}]}`,
    `Persistent subagent removal protocol: if your own durable subagent should be withdrawn from the Android app, include one separate single-line directive: ${REMOVE_SUBAGENT_DIRECTIVE} {"id":"subagent-id-or-name","reason":"short reason"}`,
    "The bridge hides these directives from chat, persists roster changes, and includes them in future app snapshots. Optional runtime options and slashCommands become the exact phone app choices for that agent. Create at most two unless the user asks for more.",
  ];
}

function agentSlashCommandLines(context = {}) {
  const target = context.subagent || context.targetAgent || context.adapterAgent;
  const agentCommands = normalizeAgentSlashCommands(target?.slashCommands || target?.commands);
  if (!agentCommands.length) return [];
  return [
    `Agent-specific slash command manifest for ${target.name}: ${agentCommands.map((item) => `${item.trigger}=${item.label}`).join(", ")}`,
    "If the current phone message begins with one of these commands, treat it as a command invocation for this agent and execute the matching intent. Do not merely explain the command unless the user asked for help.",
  ];
}

function teamContextLines(context = {}) {
  const team = context.team;
  if (!team) return [];
  const memberNames = team.memberIds.map(agentName).join(", ");
  const recent = state.messages
    .filter((message) => message.targetAgentId === team.id)
    .slice(-6)
    .map((message) => `${agentName(message.authorId)}: ${message.text}`)
    .join("\n");
  return [
    `You are replying inside persistent team group chat "${team.name}".`,
    `Team purpose/profile: ${team.purpose || team.sharedProfile}`,
    `Team members: ${memberNames}`,
    `Shared team documents/material: ${(team.sharedDocuments || []).join(", ") || "none listed"}`,
    recent ? `Recent team conversation:\n${recent}` : "Recent team conversation: none yet.",
    "Reply as your own agent identity, briefly, and you may address other team members by name.",
  ];
}

function teamDirectiveLines() {
  return [
    "Persistent team protocol: if a durable group chat/team should be created and shown in the Android app, include one separate single-line directive:",
    `${TEAM_DIRECTIVE} {"name":"Short team name","purpose":"specific shared mission","members":["codex","claude"],"sharedProfile":"shared team context","sharedDocuments":["MEMORY.md","QUEUE.md"]}`,
    "Agent-to-team message protocol: to post a hidden side message into a team group chat, include one separate single-line directive:",
    `${TEAM_MESSAGE_DIRECTIVE} {"teamId":"core","text":"short message to the team"}`,
    "The bridge hides directives from visible chat, persists teams, and includes them in future app snapshots.",
  ];
}

function fileDirectiveLines() {
  return [
    "File-to-user protocol: if you need to send a generated or existing file to the Android user, include one separate single-line directive:",
    `${FILE_DIRECTIVE} {"path":"/absolute/path/to/file","name":"optional-name.ext","mimeType":"application/octet-stream"}`,
    `For small text-only files you may use: ${FILE_DIRECTIVE} {"name":"note.txt","mimeType":"text/plain","text":"file content"}`,
    "The bridge hides the directive from chat and shows the file as an attachment in the Android conversation. Do not send secrets or private credentials.",
  ];
}

async function loadPersistedAgents() {
  const raw = await safeRead(AGENT_ROSTER_FILE);
  if (!raw.trim()) return;
  try {
    const parsed = JSON.parse(raw);
    const agents = Array.isArray(parsed) ? parsed : parsed.agents;
    if (!Array.isArray(agents)) return;
    state.dynamicAgents = agents
      .map(normalizePersistedAgent)
      .filter(Boolean)
      .filter((agent, index, list) => list.findIndex((item) => item.id === agent.id) === index);
  } catch (error) {
    console.error(`failed to load persisted subagents: ${error.message}`);
  }
}

async function savePersistedAgents() {
  await mkdir(ROOT, { recursive: true });
  await writeFile(AGENT_ROSTER_FILE, JSON.stringify({
    version: 1,
    updatedAt: Date.now(),
    agents: state.dynamicAgents,
  }, null, 2), "utf8");
}

async function loadPersistedTeams() {
  const raw = await safeRead(TEAM_ROSTER_FILE);
  if (!raw.trim()) return;
  try {
    const parsed = JSON.parse(raw);
    const teams = Array.isArray(parsed) ? parsed : parsed.teams;
    if (!Array.isArray(teams)) return;
    state.dynamicTeams = teams
      .map(normalizePersistedTeam)
      .filter(Boolean)
      .filter((team, index, list) => list.findIndex((item) => item.id === team.id) === index);
  } catch (error) {
    console.error(`failed to load persisted teams: ${error.message}`);
  }
}

async function savePersistedTeams() {
  await mkdir(ROOT, { recursive: true });
  await writeFile(TEAM_ROSTER_FILE, JSON.stringify({
    version: 1,
    updatedAt: Date.now(),
    teams: state.dynamicTeams,
  }, null, 2), "utf8");
}

function normalizePersistedTeam(team) {
  if (!team || typeof team !== "object") return null;
  const idValue = sanitizeText(team.id, "", 80);
  const name = sanitizeText(team.name, "", 56);
  if (!idValue || !name || idValue === "core") return null;
  const memberIds = sanitizeStringList(team.memberIds || team.members, 24, 80)
    .map(resolveAgentId)
    .filter(Boolean);
  const adminAgentId = resolveAgentId(team.adminAgentId) || memberIds[0] || "codex";
  return {
    id: idValue,
    name,
    adminAgentId,
    memberIds: memberIds.length ? [...new Set([adminAgentId, ...memberIds])] : [adminAgentId],
    sharedProfile: sanitizeText(team.sharedProfile || team.profile || team.purpose, `Shared team room ${name}.`, 260),
    createdByAgentId: resolveAgentId(team.createdByAgentId) || adminAgentId,
    purpose: sanitizeText(team.purpose || team.role || team.description, "", 220),
    sharedDocuments: sanitizeStringList(team.sharedDocuments || team.documents, 12, 80),
    createdAt: Number(team.createdAt || Date.now()),
    updatedAt: Number(team.updatedAt || Date.now()),
    canAgentsPost: team.canAgentsPost !== false,
  };
}

function normalizePersistedAgent(agent) {
  if (!agent || typeof agent !== "object") return null;
  const idValue = sanitizeText(agent.id, "", 80);
  const parentId = sanitizeText(agent.parentId, "", 80);
  const name = sanitizeText(agent.name, "", 48);
  if (!idValue || !parentId || !name) return null;
  return {
    id: idValue,
    name,
    kind: "SUBAGENT",
    role: sanitizeText(agent.role, `persistent helper under ${parentId}`, 180),
    status: ["ONLINE", "BUSY", "IDLE", "PAUSED"].includes(agent.status) ? agent.status : "ONLINE",
    parentId,
    teamId: sanitizeText(agent.teamId, "core", 32),
    tools: sanitizeTools(agent.tools),
    modelOptions: normalizeOptionalRuntimeOptionList(agent.modelOptions || agent.models, 128000),
    reasoningOptions: normalizeOptionalRuntimeOptionList(agent.reasoningOptions || agent.reasoning, 0),
    permissionOptions: normalizeOptionalRuntimeOptionList(agent.permissionOptions || agent.permissions, 0),
    slashCommands: normalizeAgentSlashCommands(agent.slashCommands || agent.commands),
    canSpawnChildren: true,
  };
}

async function claudeReply(text, context = {}) {
  const startedAt = Date.now();
  const progressReport = typeof context.progressReport === "function" ? context.progressReport : () => {};
  const streamProgress = createClaudeStreamProgress(progressReport, startedAt);
  const permission = agentPermissionForContext(context);
  const settings = runtimeSettingsForAgent({ id: "claude" }, context.runtimeSettings || {});
  const prompt = [
    "You are Claude Code replying through the Agent Control Android bridge.",
    "API contract: Android sends encrypted POST /v1/messages with payload { text, targetAgentId, attachments }; the bridge routes targetAgentId=claude here and returns one plain chat reply in the encrypted message.accepted envelope.",
    "Reply directly and concisely. Use Chinese unless the user clearly asks otherwise.",
    "Do not edit files or run tools for ordinary chat; if the user explicitly asks for actions, respect the current Agent Control permissions.",
    agentPermissionPromptLine(context),
    ...sharedAgentMemoryLines(),
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...agentSlashCommandLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
    ...fileDirectiveLines(),
    "",
    `User message: ${text || "(empty)"}`,
  ].join("\n");

  if (Date.now() <= claudeDirectDisabledUntil) {
    setAgentStatus("claude", "PAUSED");
    return agentResponse(claudeAuthUnavailableReply(), [
      toolCall("claude", "claude.auth", "FAILED", "Claude Code auth", "paused after recent 401", startedAt),
    ]);
  }

  try {
    progressReport("Planning...", [
      toolCall("claude", "claude.plan", "RUNNING", `model=${settings.model}`, `permission=${permission.id}`, startedAt),
    ]);
    const { stdout, stderr } = await spawnFilePromise("claude", [
      "-p",
      "--model",
      settings.model,
      "--effort",
      settings.reasoningEffort,
      "--output-format",
      "text",
      ...claudePermissionArgs(permission),
      "--disable-slash-commands",
      "--setting-sources",
      "project",
      "--no-session-persistence",
      prompt,
    ], {
      timeout: 45000,
      maxBuffer: 1024 * 1024 * 6,
      env: strippedAgentEnv(),
      onStdout: (chunk) => streamProgress(chunk, "stdout"),
      onStderr: (chunk) => streamProgress(chunk, "stderr"),
    });
    const reply = cleanCliReply(stdout);
    if (!reply) {
      console.error(`Claude empty reply. stdout=${tailText(stdout)} stderr=${tailText(stderr)}`);
      return agentResponse("Claude Code CLI finished but returned no text.", [
        toolCall("claude", "claude.invoke", "FAILED", "claude -p", "empty output", startedAt),
      ]);
    }
    setAgentStatus("claude", "ONLINE");
    return agentResponse(reply, [
      toolCall("claude", "claude.permission", "SUCCESS", permission.label, claudePermissionSummary(permission), startedAt),
      toolCall("claude", "claude.invoke", "SUCCESS", `claude --model ${settings.model}`, "Claude Code returned output", startedAt),
      ...claudeToolCallsFromRun(stdout, stderr, startedAt),
      toolCall("claude", "claude.answer", "SUCCESS", "final response", "ready", Date.now()),
    ]);
  } catch (error) {
    console.error(`Claude direct reply failed: ${error.message}`);
    if (isClaudeAuthError(error)) {
      claudeDirectDisabledUntil = Date.now() + 10 * 60 * 1000;
      setAgentStatus("claude", "PAUSED");
      return agentResponse(claudeAuthUnavailableReply(), [
        toolCall("claude", "claude.auth", "FAILED", "Claude Code auth", firstLine(error.message), startedAt),
      ]);
    }
    return agentResponse(`Claude Code failed to reply: ${firstLine(error.message)}`, [
      toolCall("claude", "claude.invoke", "FAILED", "claude -p", firstLine(error.message), startedAt),
    ]);
  }
}

function claudeAuthUnavailableReply() {
  return "Claude Code CLI auth is failing with 401. Please re-login to Claude Code on this computer; Agent Control will not use another agent as a Claude fallback.";
}

function claudePermissionArgs(permission) {
  if (permission.id === "full-access") {
    return ["--dangerously-skip-permissions", "--tools", "default"];
  }
  if (permission.id === "workspace-write") {
    return ["--permission-mode", "acceptEdits", "--tools", "default"];
  }
  return ["--permission-mode", "plan", "--tools", ""];
}

function claudePermissionSummary(permission) {
  if (permission.id === "full-access") return "dangerously-skip-permissions, default tools";
  if (permission.id === "workspace-write") return "permission-mode acceptEdits, default tools";
  return "permission-mode plan, tools disabled";
}

function geminiApprovalMode(permission) {
  if (permission.id === "full-access") return "yolo";
  if (permission.id === "workspace-write") return "auto_edit";
  return "plan";
}

function opencodePermissionArgs(permission) {
  return permission.id === "full-access" ? ["--dangerously-skip-permissions"] : [];
}

function opencodePermissionSummary(permission) {
  if (permission.id === "full-access") return "dangerously-skip-permissions";
  if (permission.id === "workspace-write") return "prompt-scoped workspace-write policy";
  return "prompt-scoped read-only policy";
}

function isClaudeAuthError(error) {
  const message = String(error?.message || "");
  return (
    message.includes("401") ||
    /authenticat|login|logged in|unauthorized/i.test(message)
  );
}

function createClaudeStreamProgress(progressReport, startedAt) {
  const seen = new Set();
  return (chunk, stream = "stdout") => {
    const text = stripAnsi(chunk);
    const line = firstLine(text);
    const action = claudeActionFromText(text || line, stream);
    const key = `${action.toolName}:${action.input}`;
    if (seen.has(key)) return;
    seen.add(key);
    progressReport(action.text, [
      toolCall("claude", action.toolName, "RUNNING", action.input, action.output, startedAt),
    ]);
  };
}

function claudeToolCallsFromRun(stdout = "", stderr = "", startedAt = Date.now()) {
  const calls = [];
  void stdout;
  for (const line of String(stderr || "").split("\n").map((item) => item.trim()).filter(Boolean)) {
    const action = claudeActionFromText(line, "final");
    calls.push(toolCall("claude", action.toolName, "SUCCESS", action.input, action.output, startedAt));
  }
  return collapseToolCalls(calls).slice(-6);
}

function claudeActionFromText(text = "", stream = "stdout") {
  const line = firstLine(text) || stream;
  const textAction = firstAgentTextAction("claude", text, "RUNNING");
  if (textAction) return textAction;
  if (/edit|write|update|patch|save|modify|changed|修改|编辑|写入|更新|保存/i.test(line)) {
    return { text: "Editing...", toolName: "claude.edit", input: line, output: "file changes" };
  }
  if (/create|mkdir|new file|spawn|生成|创建|新建/i.test(line)) {
    return { text: "Creating...", toolName: "claude.create", input: line, output: "new item" };
  }
  if (/bash|shell|run|command|npm|gradle|pytest|test|执行|运行|命令/i.test(line)) {
    return { text: "Running...", toolName: "claude.run", input: line, output: "command" };
  }
  if (/search|grep|rg|ripgrep|find|query|lookup|检索|搜索|查找/i.test(line)) {
    return { text: "Searching...", toolName: "claude.search", input: line, output: "search" };
  }
  if (/read|inspect|open|list|scan|load|读取|查看|打开|列出|扫描/i.test(line)) {
    return { text: "Reading...", toolName: "claude.read", input: line, output: "context" };
  }
  if (/auto.?compact|compact|compress|compaction|context window|自动压缩|压缩上下文|上下文压缩/i.test(line)) {
    return { text: "Compressing context...", toolName: "claude.compact", input: line, output: "context" };
  }
  if (/plan|todo|think/i.test(line)) {
    return { text: "Planning...", toolName: "claude.plan", input: line, output: "planning" };
  }
  return { text: "Writing reply...", toolName: "claude.answer", input: line, output: "stdout received" };
}

function createGenericAgentStreamProgress(agentId, progressReport, startedAt) {
  const seen = new Set();
  return {
    onStdout: createLineProgressSink((line) => {
      handleGenericAgentProgressLine(agentId, line, "stdout", progressReport, startedAt, seen);
    }),
    onStderr: createLineProgressSink((line) => {
      handleGenericAgentProgressLine(agentId, line, "stderr", progressReport, startedAt, seen);
    }),
  };
}

function createLineProgressSink(onLine) {
  let buffer = "";
  return (chunk) => {
    buffer += stripAnsi(chunk);
    let newlineIndex = buffer.indexOf("\n");
    while (newlineIndex >= 0) {
      const line = buffer.slice(0, newlineIndex).trim();
      buffer = buffer.slice(newlineIndex + 1);
      if (line) onLine(line);
      newlineIndex = buffer.indexOf("\n");
    }
  };
}

function handleGenericAgentProgressLine(agentId, line, stream, progressReport, startedAt, seen) {
  const event = parseCodexJsonLine(line);
  if (event?.type && handleGenericJsonProgressEvent(agentId, event, progressReport, startedAt, seen)) return;
  const prefix = agentToolPrefix(agentId);
  const hooks = agentTextHookCalls(agentId, prefix, line, "RUNNING", startedAt);
  if (hooks.length) {
    const key = `${stream}:text-hooks:${hashText(line)}`;
    if (seen.has(key)) return;
    seen.add(key);
    progressReport(boundedText(cleanCliReply(line), 1200), hooks);
    return;
  }
  const action = genericAgentActionFromText(agentId, line, stream);
  const key = `${stream}:${action.toolName}:${action.input}`;
  if (seen.has(key)) return;
  seen.add(key);
  progressReport(action.text, [
    toolCall(agentId, action.toolName, "RUNNING", action.input, action.output, startedAt),
  ]);
}

function handleGenericJsonProgressEvent(agentId, event, progressReport, startedAt, seen) {
  const item = event.item || {};
  const command = item.command || event.command || item.input;
  if (event.type === "item.started" && item.type === "command_execution") {
    const action = genericAgentCommandAction(agentId, command || "command");
    const key = `json:start:${action.toolName}:${action.input}`;
    if (!seen.has(key)) {
      seen.add(key);
      progressReport(action.text, [
        toolCall(agentId, action.toolName, "RUNNING", action.input, action.output, startedAt),
      ]);
    }
    return true;
  }
  if (event.type === "item.completed" && item.type === "command_execution") {
    const action = genericAgentCommandAction(agentId, command || "command");
    const status = Number(item.exit_code || 0) === 0 ? "SUCCESS" : "FAILED";
    const key = `json:done:${status}:${action.toolName}:${action.input}`;
    if (!seen.has(key)) {
      seen.add(key);
      progressReport(completedActionText(action.text, status), [
        toolCall(agentId, action.toolName, status, action.input, summarizeForAction(item.aggregated_output || action.output), startedAt),
      ]);
    }
    return true;
  }
  const text = cleanCliReply(event.message || event.text || event.status || item.text || "");
  if (!text || event.type === "final" || event.type === "result") return false;
  const key = `json:text:${event.type}:${hashText(text)}`;
  if (!seen.has(key)) {
    seen.add(key);
    const prefix = agentToolPrefix(agentId);
    const hooks = agentTextHookCalls(agentId, prefix, text, "RUNNING", startedAt);
    progressReport(text, hooks.length ? hooks : [
      toolCall(agentId, `${prefix}.progress`, "RUNNING", event.type, summarizeForAction(text), startedAt),
    ]);
  }
  return true;
}

function genericAgentToolCallsFromRun(agentId, stdout = "", stderr = "", startedAt = Date.now()) {
  const calls = [];
  for (const line of `${stdout}\n${stderr}`.split("\n").map((item) => item.trim()).filter(Boolean)) {
    const event = parseCodexJsonLine(line);
    const item = event?.item || {};
    if (event?.type === "item.completed" && item.type === "command_execution") {
      const action = genericAgentCommandAction(agentId, item.command || "command");
      const status = Number(item.exit_code || 0) === 0 ? "SUCCESS" : "FAILED";
      calls.push(toolCall(agentId, action.toolName, status, action.input, summarizeForAction(item.aggregated_output || action.output), startedAt));
      continue;
    }
    if (line.startsWith("{") && !event?.type) continue;
    const action = genericAgentActionFromText(agentId, line, "final");
    if (action.toolName.endsWith(".answer")) continue;
    calls.push(toolCall(agentId, action.toolName, "SUCCESS", action.input, action.output, startedAt));
  }
  return collapseToolCalls(calls).slice(-6);
}

function genericAgentCommandAction(agentId, commandLine = "") {
  const command = String(commandLine || "").trim();
  const prefix = agentToolPrefix(agentId);
  if (/(\bgradlew\b|npm\s+(run\s+)?test|pytest|testDebugUnitTest|xcodebuild\s+test)/i.test(command)) {
    return { text: "Testing...", toolName: `${prefix}.test`, input: command, output: "test command" };
  }
  if (/(\bgradlew\b.*assemble|npm\s+(run\s+)?build|xcodebuild\s+build|swift\s+build)/i.test(command)) {
    return { text: "Building...", toolName: `${prefix}.build`, input: command, output: "build command" };
  }
  if (/\badb\b.*(install|push)|simctl\s+install/i.test(command)) {
    return { text: "Installing...", toolName: `${prefix}.install`, input: command, output: "device install" };
  }
  if (/\b(rg|grep|find|fd|ack)\b/i.test(command)) {
    return { text: "Searching...", toolName: `${prefix}.search`, input: command, output: "search command" };
  }
  if (/\b(sed|ls|cat|nl|git\s+(status|show|diff|log))\b/i.test(command)) {
    return { text: "Reading...", toolName: `${prefix}.read`, input: command, output: "repo context" };
  }
  if (/\b(mkdir|touch|cp|mv|git\s+clone|gh\s+repo\s+create)\b/i.test(command)) {
    return { text: "Creating...", toolName: `${prefix}.create`, input: command, output: "filesystem command" };
  }
  return { text: "Running...", toolName: `${prefix}.run`, input: command || "command", output: "command" };
}

function genericAgentActionFromText(agentId, text = "", stream = "stdout") {
  const line = boundedText(firstLine(text) || stream, 260);
  const prefix = agentToolPrefix(agentId);
  const textAction = firstAgentTextAction(agentId, text, "RUNNING");
  if (textAction) return textAction;
  if (stream === "stdout" && !looksLikeProgressLine(line)) {
    return { text: "Writing reply...", toolName: `${prefix}.answer`, input: "stdout received", output: "writing" };
  }
  if (/auth|login|unauthorized|401/i.test(line)) {
    return { text: "Checking auth...", toolName: `${prefix}.auth`, input: line, output: "auth" };
  }
  if (/model|capacity|rate.?limit|quota|retry|fallback|unavailable|waiting/i.test(line)) {
    return { text: "Waiting for model...", toolName: `${prefix}.model`, input: line, output: "model channel" };
  }
  if (/edit|write|update|patch|save|modify|changed|diff|修改|编辑|写入|更新|保存/i.test(line)) {
    return { text: "Editing...", toolName: `${prefix}.edit`, input: line, output: "file changes" };
  }
  if (/create|mkdir|new file|spawn|team|生成|创建|新建/i.test(line)) {
    return { text: "Creating...", toolName: `${prefix}.create`, input: line, output: "new item" };
  }
  if (/build|assemble|compile|构建|编译/i.test(line)) {
    return { text: "Building...", toolName: `${prefix}.build`, input: line, output: "build" };
  }
  if (/test|pytest|junit|gradle test|测试|单测/i.test(line)) {
    return { text: "Testing...", toolName: `${prefix}.test`, input: line, output: "test" };
  }
  if (/install|adb|device|simulator|安装|刷机|真机|模拟器/i.test(line)) {
    return { text: "Installing...", toolName: `${prefix}.install`, input: line, output: "device" };
  }
  if (/search|grep|rg|ripgrep|find|query|lookup|检索|搜索|查找/i.test(line)) {
    return { text: "Searching...", toolName: `${prefix}.search`, input: line, output: "search" };
  }
  if (/read|inspect|open|list|scan|load|context|读取|查看|打开|列出|扫描/i.test(line)) {
    return { text: "Reading...", toolName: `${prefix}.read`, input: line, output: "context" };
  }
  if (/auto.?compact|compact|compress|compaction|context window|自动压缩|压缩上下文|上下文压缩/i.test(line)) {
    return { text: "Compressing context...", toolName: `${prefix}.compact`, input: line, output: "context" };
  }
  if (/plan|todo|think|reason|approval/i.test(line)) {
    return { text: "Planning...", toolName: `${prefix}.plan`, input: line, output: "planning" };
  }
  if (/run|command|shell|invoke|tool|call/i.test(line)) {
    return { text: "Running...", toolName: `${prefix}.run`, input: line, output: "command" };
  }
  return { text: stream === "stderr" ? "Running..." : "Writing reply...", toolName: stream === "stderr" ? `${prefix}.run` : `${prefix}.answer`, input: line, output: stream };
}

function looksLikeProgressLine(line = "") {
  return /^(>|info|warn|error|debug|running|starting|loading|using|calling|tool|thinking|planning|reading|writing|editing|created|updated|executing|failed|success|done|计划|读取|搜索|查找|创建|编辑|修改|运行|执行|测试|构建|安装|压缩|✓|✗|✔|⠋|⠙|⠹)/i.test(line) ||
    /(\b(rg|sed|grep|find|ls|cat|git|npm|gradle|pytest|adb|claude|gemini|opencode)\b|\.kt|\.js|\.mjs|\.md|\.json|\.gradle)/i.test(line);
}

function agentToolPrefix(agentId = "agent") {
  if (agentId === "gemini_cli") return "gemini";
  if (agentId === "claude") return "claude";
  if (agentId === "antigravity") return "antigravity";
  if (agentId === "opencode") return "opencode";
  if (agentId === "codex") return "codex";
  return "agent";
}

function setAgentStatus(agentId, status) {
  const agent = findAgent(agentId);
  if (agent) agent.status = status;
}

async function antigravityReply(text, context = {}) {
  const reply = await antigravityAgentReply({
    visibleName: "Antigravity",
    agentId: "main",
    text,
    timeoutSeconds: 180,
    context,
  });
  return reply || agentResponse("Antigravity finished but returned no text.", [
    toolCall("antigravity", "antigravity.agent", "FAILED", "agent --json", "empty output"),
  ]);
}

async function antigravityAgentReply({ visibleName, agentId, text, timeoutSeconds, context = {} }) {
  const command = antigravityCommand();
  const startedAt = Date.now();
  const progressReport = typeof context.progressReport === "function" ? context.progressReport : () => {};
  const streamProgress = createGenericAgentStreamProgress("antigravity", progressReport, startedAt);
  const permission = agentPermissionForContext(context);
  const settings = runtimeSettingsForAgent({ id: "antigravity" }, context.runtimeSettings || {});
  if (!command) return agentResponse(`${visibleName} CLI is not available on this desktop.`, [
    toolCall("antigravity", "antigravity.agent", "FAILED", "locate CLI", "command not found", startedAt),
  ]);
  const prompt = [
    `You are ${visibleName} replying through the Agent Control Android bridge.`,
    `API contract: Android sends encrypted POST /v1/messages with payload { text, targetAgentId, attachments }; the bridge routes it to ${visibleName} and returns one plain chat reply in the encrypted message.accepted envelope.`,
    "Reply directly and concisely. Use Chinese unless the user clearly asks otherwise.",
    "Do not edit files or run tools for ordinary chat; if the user explicitly asks for actions, respect the current Agent Control permissions.",
    `Current Antigravity/OpenClaw model id: ${settings.model}.`,
    "Ignore prior probe prompts or stale sentinel strings in any reused desktop agent session. Answer only the current user message below.",
    agentPermissionPromptLine(context),
    ...sharedAgentMemoryLines(),
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...agentSlashCommandLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
    ...fileDirectiveLines(),
    "",
    `User message: ${text || "(empty)"}`,
  ].join("\n");

  try {
    if (command === "openclaw") {
      await spawnFilePromise(command, ["models", "set", settings.model], {
        timeout: 8000,
        maxBuffer: 1024 * 1024,
        env: {
          ...process.env,
          NO_COLOR: "1",
        },
      });
    }
    progressReport(`Starting ${visibleName}...`, [
      toolCall("antigravity", "antigravity.invoke", "RUNNING", `model=${settings.model}`, `permission=${permission.id}`, startedAt),
    ]);
    const { stdout, stderr } = await spawnFilePromise(command, [
      "agent",
      "--agent",
      agentId,
      "--json",
      "--thinking",
      settings.reasoningEffort,
      "--timeout",
      String(timeoutSeconds),
      "--message",
      prompt,
    ], {
      timeout: (timeoutSeconds + 20) * 1000,
      maxBuffer: 1024 * 1024 * 10,
      env: {
        ...process.env,
        NO_COLOR: "1",
      },
      onStdout: streamProgress.onStdout,
      onStderr: streamProgress.onStderr,
    });
    const reply = extractAgentJsonReply(stdout);
    if (!reply) {
      console.error(`${visibleName} empty reply. stdout=${tailText(stdout)} stderr=${tailText(stderr)}`);
      return agentResponse("", [
        toolCall("antigravity", "antigravity.agent", "FAILED", `${command} agent --json`, "empty output", startedAt),
      ]);
    }
    return agentResponse(reply, [
      toolCall("antigravity", "antigravity.permission", "SUCCESS", permission.label, "prompt-scoped permission policy", startedAt),
      toolCall("antigravity", "antigravity.invoke", "SUCCESS", `${command} agent --model ${settings.model}`, "agent returned output", startedAt),
      ...genericAgentToolCallsFromRun("antigravity", stdout, stderr, startedAt),
      toolCall("antigravity", "antigravity.answer", "SUCCESS", "final response", "ready", Date.now()),
    ]);
  } catch (error) {
    console.error(`${visibleName} reply failed: ${error.message}`);
    return agentResponse(`${visibleName} failed to reply: ${firstLine(error.message)}`, [
      toolCall("antigravity", "antigravity.agent", "FAILED", `${command} agent --json`, firstLine(error.message), startedAt),
    ]);
  }
}

async function geminiReply(text, context = {}) {
  const startedAt = Date.now();
  const progressReport = typeof context.progressReport === "function" ? context.progressReport : () => {};
  const streamProgress = createGenericAgentStreamProgress("gemini_cli", progressReport, startedAt);
  const permission = agentPermissionForContext(context);
  const approvalMode = geminiApprovalMode(permission);
  const settings = runtimeSettingsForAgent({ id: "gemini_cli" }, context.runtimeSettings || {});
  const prompt = [
    "You are Gemini CLI replying through the Agent Control Android bridge.",
    "API contract: Android sends encrypted POST /v1/messages with payload { text, targetAgentId, attachments }; the bridge routes targetAgentId=gemini_cli here and returns one plain chat reply in the encrypted message.accepted envelope.",
    "Reply directly and concisely. Use Chinese unless the user clearly asks otherwise.",
    "Do not run tools or edit files for ordinary chat; if the user explicitly asks for actions, respect the current Agent Control permissions.",
    agentPermissionPromptLine(context),
    ...sharedAgentMemoryLines(),
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...agentSlashCommandLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
    ...fileDirectiveLines(),
    "",
    `User message: ${text || "(empty)"}`,
  ].join("\n");

  try {
    progressReport("Starting Gemini CLI...", [
      toolCall("gemini_cli", "gemini.invoke", "RUNNING", `model=${settings.model}`, `approval-mode ${approvalMode}`, startedAt),
    ]);
    const { stdout, stderr } = await spawnFilePromise("/bin/zsh", [
      "-lc",
      'source "$HOME/.zshrc"; gemini --prompt "$1" --approval-mode "$2" --model "$3" --output-format text',
      "agent-control-gemini",
      prompt,
      approvalMode,
      settings.model,
    ], {
      timeout: 180000,
      maxBuffer: 1024 * 1024 * 6,
      env: {
        ...process.env,
        NO_COLOR: "1",
      },
      onStdout: streamProgress.onStdout,
      onStderr: streamProgress.onStderr,
    });
    const reply = cleanCliReply(stdout);
    if (!reply) {
      console.error(`Gemini empty reply. stdout=${tailText(stdout)} stderr=${tailText(stderr)}`);
      return agentResponse("Gemini CLI finished but returned no text.", [
        toolCall("gemini_cli", "gemini.invoke", "FAILED", "gemini --prompt", "empty output", startedAt),
      ]);
    }
    return agentResponse(reply, [
      toolCall("gemini_cli", "gemini.permission", "SUCCESS", permission.label, `approval-mode ${approvalMode}`, startedAt),
      toolCall("gemini_cli", "gemini.invoke", "SUCCESS", `gemini --model ${settings.model}`, "Gemini CLI returned output", startedAt),
      ...genericAgentToolCallsFromRun("gemini_cli", stdout, stderr, startedAt),
      toolCall("gemini_cli", "gemini.answer", "SUCCESS", "final response", "ready", Date.now()),
    ]);
  } catch (error) {
    console.error(`Gemini reply failed: ${error.message}`);
    return agentResponse(`Gemini CLI failed to reply: ${firstLine(error.message)}`, [
      toolCall("gemini_cli", "gemini.invoke", "FAILED", "gemini --prompt", firstLine(error.message), startedAt),
    ]);
  }
}

async function opencodeReply(text, context = {}) {
  const startedAt = Date.now();
  const progressReport = typeof context.progressReport === "function" ? context.progressReport : () => {};
  const streamProgress = createGenericAgentStreamProgress("opencode", progressReport, startedAt);
  const permission = agentPermissionForContext(context);
  const settings = runtimeSettingsForAgent({ id: "opencode" }, context.runtimeSettings || {});
  const prompt = [
    "You are OpenCode replying through the Agent Control Android bridge.",
    "API contract: Android sends encrypted POST /v1/messages with payload { text, targetAgentId, attachments }; the bridge routes targetAgentId=opencode here and returns one plain chat reply in the encrypted message.accepted envelope.",
    `Use the selected OpenCode model id: ${settings.model}.`,
    "Reply directly and concisely. Use Chinese unless the user clearly asks otherwise.",
    "Do not edit files or run tools for ordinary chat; if the user explicitly asks for actions, respect the current Agent Control permissions.",
    agentPermissionPromptLine(context),
    ...sharedAgentMemoryLines(),
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...agentSlashCommandLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
    ...fileDirectiveLines(),
    "",
    `User message: ${text || "(empty)"}`,
  ].join("\n");

  try {
    progressReport("Starting OpenCode...", [
      toolCall("opencode", "opencode.run", "RUNNING", `model=${settings.model}`, `permission=${permission.id}`, startedAt),
    ]);
    const variantArgs = settings.reasoningEffort && settings.reasoningEffort !== "default"
      ? ["--variant", settings.reasoningEffort]
      : [];
    const { stdout, stderr } = await spawnFilePromise("opencode", [
      "run",
      ...opencodePermissionArgs(permission),
      "--model",
      settings.model,
      ...variantArgs,
      "--format",
      "default",
      prompt,
    ], {
      timeout: 180000,
      maxBuffer: 1024 * 1024 * 8,
      env: {
        ...process.env,
        NO_COLOR: "1",
      },
      onStdout: streamProgress.onStdout,
      onStderr: streamProgress.onStderr,
    });
    const reply = cleanOpenCodeReply(stdout);
    if (!reply) {
      console.error(`OpenCode empty reply. stdout=${tailText(stdout)} stderr=${tailText(stderr)}`);
      return agentResponse("OpenCode CLI finished but returned no text.", [
        toolCall("opencode", "opencode.run", "FAILED", "opencode run", "empty output", startedAt),
      ]);
    }
    setAgentStatus("opencode", "ONLINE");
    return agentResponse(reply, [
      toolCall("opencode", "opencode.permission", "SUCCESS", permission.label, opencodePermissionSummary(permission), startedAt),
      toolCall("opencode", "opencode.run", "SUCCESS", `opencode run --model ${settings.model}`, "OpenCode returned output", startedAt),
      ...genericAgentToolCallsFromRun("opencode", stdout, stderr, startedAt),
      toolCall("opencode", "opencode.answer", "SUCCESS", "final response", "ready", Date.now()),
    ]);
  } catch (error) {
    console.error(`OpenCode reply failed: ${error.message}`);
    setAgentStatus("opencode", "PAUSED");
    return agentResponse(`OpenCode failed to reply: ${firstLine(error.message)}`, [
      toolCall("opencode", "opencode.run", "FAILED", "opencode run", firstLine(error.message), startedAt),
    ]);
  }
}

async function codexReply(text, context = {}) {
  const settings = normalizeCodexRuntimeSettings(context.runtimeSettings || codexRuntimeSettings);
  const permission = CODEX_PERMISSION_OPTIONS.find((option) => option.id === settings.permissionMode) || CODEX_PERMISSION_OPTIONS[0];
  const progressReport = typeof context.progressReport === "function" ? context.progressReport : () => {};
  const prompt = [
    "You are Codex replying to the user from the Agent Control Android app.",
    "Reply directly and concisely. Use Chinese unless the user clearly asks otherwise.",
    "Do not run tools for ordinary chat. If the user asks for code or actions, respect the current Codex mobile runtime permissions.",
    `Current Codex mobile runtime: model=${settings.model}, reasoning=${settings.reasoningEffort}, permissions=${settings.permissionMode}, context=${settings.contextUsedTokens}/${settings.contextLimitTokens} estimated tokens.`,
    ...sharedAgentMemoryLines(),
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...agentSlashCommandLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
    ...fileDirectiveLines(),
    "",
    `User message: ${text || "(empty)"}`,
  ].join("\n");

  const attempts = [];
  const fallbackCalls = [];
  for (const model of codexModelCandidates(settings.model)) {
    const modelInfo = CODEX_MODEL_OPTIONS.find((option) => option.id === model) || CODEX_MODEL_OPTIONS[0];
    const attemptSettings = {
      ...settings,
      model,
      contextLimitTokens: modelInfo.contextLimitTokens,
    };
    try {
      progressReport("Starting Codex...", [
        toolCall("codex", "codex.exec", "RUNNING", `codex exec -m ${model}`, `${permission.sandbox}, ${settings.reasoningEffort}`),
      ]);
      const result = await runCodexCli(prompt, attemptSettings, permission, progressReport);
      if (model !== settings.model) {
        codexRuntimeSettings = normalizeCodexRuntimeSettings({ ...codexRuntimeSettings, model });
        schedulePrivateBridgeStateSave();
      }
      const modelCalls = model !== settings.model
        ? [toolCall("codex", "codex.model_fallback", "SUCCESS", settings.model, `switched to ${model}`)]
        : [];
      return agentResponse(result.text, [...fallbackCalls, ...modelCalls, ...result.toolCalls]);
    } catch (error) {
      attempts.push(`${model}: ${firstLine(error.message)}`);
      if (!isRetriableCodexModelError(error)) {
        console.error(`Codex reply failed on ${model}: ${error.message}`);
        throw error;
      }
      fallbackCalls.push(toolCall("codex", "codex.model_fallback", "FAILED", model, firstLine(error.message)));
      progressReport("Switching model...", [
        toolCall("codex", "codex.model_fallback", "FAILED", model, firstLine(error.message)),
      ]);
      console.error(`Codex model ${model} unavailable, trying fallback: ${firstLine(error.message)}`);
    }
  }
  throw new Error(`all Codex models failed (${attempts.join("; ")})`);
}

async function runCodexCli(prompt, settings, permission, progressReport = () => {}) {
  const outputFile = join("/tmp", `agent-control-codex-${id()}.txt`);
  const startedAt = Date.now();
  const stdoutProgress = createCodexStdoutProgress(progressReport, startedAt);
  try {
    const { stdout, stderr } = await spawnFilePromise("codex", [
      "-a",
      permission.approval,
      "--disable",
      "plugins",
      "--disable",
      "apps",
      "--disable",
      "browser_use",
      "--disable",
      "computer_use",
      "exec",
      "--ignore-rules",
      "--ephemeral",
      "--color",
      "never",
      "-s",
      permission.sandbox,
      "-m",
      settings.model,
      "-c",
      `model_reasoning_effort=${settings.reasoningEffort}`,
      "--skip-git-repo-check",
      "--json",
      "--cd",
      homedir(),
      "--output-last-message",
      outputFile,
      "-",
    ], {
      input: prompt,
      timeout: 180000,
      maxBuffer: 1024 * 1024 * 6,
      env: strippedAgentEnv(),
      onStdout: stdoutProgress,
      onStderr: (chunk) => {
        const line = firstLine(stripAnsi(chunk));
        if (/reconnecting|model|capacity|unavailable/i.test(line)) {
          progressReport("Waiting for model...", [
            toolCall("codex", "codex.exec", "RUNNING", `codex exec -m ${settings.model}`, line, startedAt),
          ]);
        }
      },
    });
    const fileReply = (await readFile(outputFile, "utf8").catch(() => "")).trim();
    const stdoutReply = extractCodexReplyFromStdout(stdout);
    const reply = fileReply || stdoutReply;
    if (!reply) {
      console.error(`Codex empty reply. stdout=${tailText(stdout)} stderr=${tailText(stderr)}`);
      throw new Error("Codex CLI finished but returned no text.");
    }
    progressReport("Writing reply...", [
      toolCall("codex", "codex.answer", "RUNNING", "final response", "formatting reply", startedAt),
    ]);
    return {
      text: reply,
      toolCalls: codexToolCallsFromRun(stdout, stderr, settings, permission, startedAt),
    };
  } catch (error) {
    throw error;
  } finally {
    unlink(outputFile).catch(() => {});
  }
}

function createCodexStdoutProgress(progressReport, startedAt) {
  let buffer = "";
  const seen = new Set();
  return (chunk) => {
    buffer += stripAnsi(chunk);
    let newlineIndex = buffer.indexOf("\n");
    while (newlineIndex >= 0) {
      const line = buffer.slice(0, newlineIndex).trim();
      buffer = buffer.slice(newlineIndex + 1);
      handleCodexProgressLine(line, progressReport, startedAt, seen);
      newlineIndex = buffer.indexOf("\n");
    }
  };
}

function handleCodexProgressLine(line, progressReport, startedAt, seen) {
  if (!line) return;
  const event = parseCodexJsonLine(line);
  if (event) {
    handleCodexJsonProgressEvent(event, progressReport, startedAt, seen);
    return;
  }
  handleCodexLegacyProgressLine(line, progressReport, startedAt, seen);
}

function handleCodexJsonProgressEvent(event, progressReport, startedAt, seen) {
  const item = event.item || {};
  if (event.type === "turn.started" && !seen.has("turn.started")) {
    seen.add("turn.started");
    progressReport("Preparing context...", [
      toolCall("codex", "codex.context", "RUNNING", "conversation and workspace", "turn started", startedAt),
    ]);
    return;
  }
  if (event.type === "item.started" && item.type === "command_execution") {
    reportCodexCommandProgress(item.command, "RUNNING", item.aggregated_output, progressReport, startedAt, seen);
    return;
  }
  if (event.type === "item.completed" && item.type === "command_execution") {
    const status = Number(item.exit_code || 0) === 0 ? "SUCCESS" : "FAILED";
    reportCodexCommandProgress(item.command, status, item.aggregated_output, progressReport, startedAt, seen);
    return;
  }
  if (event.type === "item.completed" && item.type === "agent_message") {
    const text = boundedText(cleanCliReply(item.text || ""), 1200);
    if (!text) return;
    const key = `agent_message:${hashText(text)}`;
    if (seen.has(key)) return;
    seen.add(key);
    const hooks = agentTextHookCalls("codex", "codex", text, "RUNNING", startedAt);
    progressReport(text, hooks.length ? hooks : [
      toolCall("codex", "codex.progress", "RUNNING", "agent message", summarizeForAction(text), startedAt),
    ]);
    return;
  }
  if (event.type === "turn.completed" && !seen.has("turn.completed")) {
    seen.add("turn.completed");
    progressReport("Writing reply...", [
      toolCall("codex", "codex.answer", "RUNNING", "final response", usageSummary(event.usage), startedAt),
    ]);
  }
}

function reportCodexCommandProgress(commandLine = "", status = "RUNNING", output = "", progressReport, startedAt, seen) {
  const command = String(commandLine || "").trim();
  if (!command) return;
  const action = codexCommandAction(command);
  const key = `command:${status}:${command}`;
  if (seen.has(key)) return;
  seen.add(key);
  progressReport(status === "RUNNING" ? action.text : completedActionText(action.text, status), [
    toolCall("codex", action.toolName, status, command, status === "RUNNING" ? action.output : summarizeForAction(output || action.output), startedAt),
  ]);
}

function handleCodexLegacyProgressLine(marker, progressReport, startedAt, seen) {
  if (marker === "codex" && !seen.has("answer")) {
    seen.add("answer");
    progressReport("Writing reply...", [
      toolCall("codex", "codex.answer", "RUNNING", "final response", "writing", startedAt),
    ]);
  }
  if (/compact|compressing context|context compaction|自动压缩|压缩上下文|上下文压缩/i.test(marker) && !seen.has("compact")) {
    seen.add("compact");
    progressReport("Compressing context...", [
      toolCall("codex", "codex.compact", "RUNNING", "context", marker, startedAt),
    ]);
  }
}

function codexToolCallsFromRun(stdout, stderr, settings, permission, startedAt) {
  const calls = [
    toolCall("codex", "codex.context", "SUCCESS", "recent conversation", `${settings.contextUsedTokens}/${settings.contextLimitTokens} tokens`, startedAt),
    toolCall("codex", "codex.exec", "SUCCESS", `codex exec -m ${settings.model}`, `${permission.sandbox}, ${settings.reasoningEffort}`, startedAt),
  ];
  calls.push(...parseCodexStdoutActions(stdout, startedAt));
  if (/compact|自动压缩|压缩上下文|上下文压缩/i.test(stdout) || /compact|自动压缩|压缩上下文|上下文压缩/i.test(stderr)) {
    calls.push(toolCall("codex", "codex.compact", "SUCCESS", "context", "auto-compaction noted by Codex CLI", startedAt));
  }
  calls.push(toolCall("codex", "codex.answer", "SUCCESS", "final response", "ready", Date.now()));
  return collapseToolCalls(calls).slice(-10);
}

function parseCodexStdoutActions(stdout = "", startedAt = Date.now()) {
  const lines = String(stdout || "").split("\n");
  const jsonActions = codexJsonActionsFromLines(lines, startedAt);
  if (jsonActions.length) return jsonActions;
  const actions = [];
  for (let index = 0; index < lines.length; index += 1) {
    const marker = lines[index].trim();
    if (marker === "exec") {
      const commandLine = (lines[index + 1] || "").trim();
      const statusLine = (lines[index + 2] || "").trim();
      if (commandLine) {
        const action = codexCommandAction(commandLine);
        actions.push(toolCall("codex", action.toolName, statusLine.includes("failed") ? "FAILED" : "SUCCESS", commandLine, statusLine || action.output, startedAt));
      }
    }
    if (marker === "apply_patch") {
      const action = codexPatchAction(lines, index);
      actions.push(toolCall("codex", action.toolName, "SUCCESS", action.input, "patch applied", startedAt));
    }
  }
  return actions;
}

function codexJsonActionsFromLines(lines = [], startedAt = Date.now()) {
  const actions = [];
  for (const line of lines) {
    const event = parseCodexJsonLine(line);
    const item = event?.item || {};
    if (event?.type === "item.completed" && item.type === "command_execution") {
      const command = String(item.command || "").trim();
      if (!command) continue;
      const action = codexCommandAction(command);
      const status = Number(item.exit_code || 0) === 0 ? "SUCCESS" : "FAILED";
      actions.push(toolCall("codex", action.toolName, status, command, summarizeForAction(item.aggregated_output || action.output), startedAt));
    }
  }
  return actions;
}

function collapseToolCalls(calls = []) {
  const collapsed = [];
  const seen = new Set();
  for (const call of normalizeResponseToolCalls(calls)) {
    const key = `${call.toolName}:${call.status}:${call.input}:${call.output}`;
    if (seen.has(key)) continue;
    seen.add(key);
    collapsed.push(call);
  }
  return collapsed;
}

function codexModelCandidates(requestedModel) {
  const ordered = [requestedModel, ...CODEX_MODEL_OPTIONS.map((option) => option.id)]
    .filter(Boolean)
    .filter((model, index, models) => models.indexOf(model) === index);
  return ordered.length ? ordered : [CODEX_DEFAULT_MODEL];
}

function isRetriableCodexModelError(error) {
  const message = String(error?.message || "");
  return /No available channel|503 Service Unavailable|MODEL_CAPACITY|model .*not.*available|unsupported model|model_not_found|404 Not Found/i.test(message);
}

function extractCodexReplyFromStdout(stdout = "") {
  const jsonReply = lastCodexJsonAgentMessage(stdout);
  if (jsonReply) return jsonReply;
  const marker = "\ncodex\n";
  const start = stdout.lastIndexOf(marker);
  let reply = start >= 0 ? stdout.slice(start + marker.length) : stdout;
  const tokenIndex = reply.indexOf("\ntokens used");
  if (tokenIndex >= 0) reply = reply.slice(0, tokenIndex);
  return cleanCliReply(reply);
}

function parseCodexJsonLine(line = "") {
  const trimmed = String(line || "").trim();
  if (!trimmed.startsWith("{")) return null;
  try {
    return JSON.parse(trimmed);
  } catch {
    return null;
  }
}

function lastCodexJsonAgentMessage(stdout = "") {
  let reply = "";
  for (const line of String(stdout || "").split("\n")) {
    const event = parseCodexJsonLine(line);
    if (event?.type === "item.completed" && event.item?.type === "agent_message") {
      reply = cleanCliReply(event.item.text || "");
    }
  }
  return reply;
}

function usageSummary(usage = {}) {
  const input = Number(usage.input_tokens || 0);
  const output = Number(usage.output_tokens || 0);
  if (!input && !output) return "ready";
  return `${input}/${output} tokens`;
}

function completedActionText(text = "", status = "SUCCESS") {
  const base = String(text || "Running...").replace(/\.\.\.$/, "");
  return status === "FAILED" ? `${base} failed` : base;
}

function summarizeForAction(value = "") {
  const text = cleanCliReply(value)
    .replace(/\s+/g, " ")
    .trim();
  return boundedText(text || "done", 160);
}

function hashText(value = "") {
  return createHash("sha256").update(String(value || "")).digest("hex").slice(0, 16);
}

function cleanCliReply(value = "") {
  return stripAnsi(value)
    .split("\n")
    .filter((line) => {
      const trimmed = line.trim();
      return trimmed &&
        !trimmed.startsWith("Data collection is disabled") &&
        !trimmed.startsWith("Loaded cached credentials") &&
        !trimmed.startsWith("Reading additional input from stdin");
    })
    .join("\n")
    .trim();
}

function codexCommandAction(commandLine = "") {
  const command = String(commandLine || "").trim();
  if (/(\bgradlew\b|npm\s+(run\s+)?test|pytest|testDebugUnitTest|xcodebuild\s+test)/i.test(command)) {
    return { text: "Testing...", toolName: "codex.test", output: "test command" };
  }
  if (/(\bgradlew\b.*assemble|npm\s+(run\s+)?build|xcodebuild\s+build|swift\s+build)/i.test(command)) {
    return { text: "Building...", toolName: "codex.build", output: "build command" };
  }
  if (/\badb\b.*(install|push)|simctl\s+install/i.test(command)) {
    return { text: "Installing...", toolName: "codex.install", output: "device install" };
  }
  if (/\b(rg|sed|grep|find|ls|cat|nl|git\s+(status|show|diff|log))\b/i.test(command)) {
    return { text: "Reading...", toolName: "codex.read", output: "repo context" };
  }
  if (/\b(mkdir|touch|cp|mv|git\s+clone|gh\s+repo\s+create)\b/i.test(command)) {
    return { text: "Creating...", toolName: "codex.create", output: "filesystem command" };
  }
  return { text: "Running...", toolName: "codex.run", output: "command in progress" };
}

function codexPatchAction(lines = [], index = 0) {
  const preview = lines.slice(index + 1, index + 12).join("\n");
  const add = preview.match(/^\*\*\* Add File:\s*(.+)$/m);
  if (add) return { text: "Creating...", toolName: "codex.create", input: add[1].trim(), output: "patch in progress" };
  const del = preview.match(/^\*\*\* Delete File:\s*(.+)$/m);
  if (del) return { text: "Editing...", toolName: "codex.edit", input: del[1].trim(), output: "delete patch" };
  const update = preview.match(/^\*\*\* Update File:\s*(.+)$/m);
  if (update) return { text: "Editing...", toolName: "codex.edit", input: update[1].trim(), output: "patch in progress" };
  const move = preview.match(/^\*\*\* Move to:\s*(.+)$/m);
  if (move) return { text: "Editing...", toolName: "codex.edit", input: move[1].trim(), output: "move patch" };
  const fallback = (lines[index + 1] || "").trim() || "apply_patch";
  return { text: "Editing...", toolName: "codex.edit", input: fallback, output: "patch in progress" };
}

function stripAnsi(value = "") {
  return String(value || "").replace(/\u001B\[[0-?]*[ -/]*[@-~]/g, "");
}

function cleanOpenCodeReply(value = "") {
  return cleanCliReply(value)
    .split("\n")
    .filter((line) => {
      const trimmed = line.trim();
      return trimmed && !/^>\s+\S+\s+·\s+/i.test(trimmed);
    })
    .join("\n")
    .trim();
}

function extractAgentJsonReply(stdout = "") {
  const raw = cleanCliReply(stdout);
  if (!raw) return "";
  try {
    const data = JSON.parse(raw);
    const payloads = data?.result?.payloads;
    if (Array.isArray(payloads)) {
      return payloads
        .map((payload) => payload?.text || "")
        .filter(Boolean)
        .join("\n")
        .trim();
    }
  } catch {
    // Fall through to raw text; some CLI failures write plain text despite --json.
  }
  return raw;
}

function tailText(value = "", size = 4000) {
  return String(value || "").slice(-size).trim();
}

function firstLine(value = "") {
  return String(value || "").trim().split("\n")[0] || "unknown error";
}

function strippedAgentEnv() {
  const env = { ...process.env, NO_COLOR: "1" };
  for (const key of [
    "ANTHROPIC_API_KEY",
    "ANTHROPIC_AUTH_TOKEN",
    "ANTHROPIC_BASE_URL",
    "ANTHROPIC_MODEL",
    "ANTHROPIC_SMALL_FAST_MODEL",
    "CLAUDE_OPENROUTER_KEY",
    "OPENAI_API_KEY",
    "OPENAI_BASE_URL",
    "OPENROUTER_API_KEY",
  ]) {
    delete env[key];
  }
  return env;
}

function execFilePromise(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    execFile(command, args, options, (error, stdout, stderr) => {
      if (error) {
        error.message = `${error.message}\n${stdout || ""}${stderr || ""}`.trim();
        reject(error);
        return;
      }
      resolve({ stdout, stderr });
    });
  });
}

function spawnFilePromise(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const maxBuffer = options.maxBuffer || 1024 * 1024;
    const child = spawn(command, args, {
      env: options.env,
      stdio: ["pipe", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";
    let settled = false;
    const finish = (callback) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      callback();
    };
    const append = (target, chunk, callback) => {
      const text = chunk.toString("utf8");
      callback?.(text);
      const next = target + text;
      if (next.length > maxBuffer) {
        child.kill("SIGTERM");
        finish(() => reject(new Error(`maxBuffer exceeded for ${command}`)));
        return target;
      }
      return next;
    };
    const timer = options.timeout
      ? setTimeout(() => {
          child.kill("SIGTERM");
          setTimeout(() => child.kill("SIGKILL"), 1500).unref?.();
          finish(() => reject(new Error(`${command} timed out after ${options.timeout}ms\n${stdout || ""}${stderr || ""}`.trim())));
        }, options.timeout)
      : null;
    child.stdout.on("data", (chunk) => {
      stdout = append(stdout, chunk, options.onStdout);
    });
    child.stderr.on("data", (chunk) => {
      stderr = append(stderr, chunk, options.onStderr);
    });
    child.on("error", (error) => finish(() => reject(error)));
    child.on("close", (code, signal) => {
      finish(() => {
        if (code === 0) {
          resolve({ stdout, stderr });
        } else {
          reject(new Error(`${command} exited with ${signal || code}\n${stdout || ""}${stderr || ""}`.trim()));
        }
      });
    });
    if (options.input != null) {
      child.stdin.end(options.input);
    } else {
      child.stdin.end();
    }
  });
}

async function commandVersion(command) {
  if (!commandExists(command)) return `${command}: not found`;
  return new Promise((resolve) => {
    const child = execFile(command, ["--version"], { timeout: 2000 }, (error, stdout, stderr) => {
      const output = `${stdout || stderr}`.trim().split("\n")[0];
      resolve(`${command}: ${error ? "available" : output || "available"}`);
    });
    child.on("error", () => resolve(`${command}: available`));
  });
}

function commandExists(command) {
  const paths = String(process.env.PATH || "").split(":");
  return paths.some((item) => existsSync(join(item, command)));
}

function antigravityCommand() {
  if (commandExists("antigravity")) return "antigravity";
  if (commandExists("openclaw")) return "openclaw";
  return "";
}

async function antigravityVersion() {
  const command = antigravityCommand();
  if (!command) return "Antigravity: not found";
  return new Promise((resolve) => {
    const child = execFile(command, ["--version"], { timeout: 4000 }, (error, stdout, stderr) => {
      const output = `${stdout || stderr}`.trim().split("\n")[0];
      const version = output
        ? output.replace(/^OpenClaw\b/i, "Antigravity")
        : "available";
      resolve(`Antigravity: ${error ? "available" : version}`);
    });
    child.on("error", () => resolve("Antigravity: available"));
  });
}

async function saveUpload(payload) {
  await mkdir(UPLOAD_ROOT, { recursive: true });
  const name = basename(payload.name || `upload-${Date.now()}`);
  const file = join(UPLOAD_ROOT, name);
  const content = payload.base64 ? Buffer.from(payload.base64, "base64") : Buffer.from(payload.text || "", "utf8");
  await writeFile(file, content);
  const transfer = {
    id: id(),
    name,
    mimeType: payload.mimeType || "application/octet-stream",
    direction: "PHONE_TO_DESKTOP",
    uri: file,
    sizeLabel: `${content.length} bytes`,
  };
  state.transfers.push(transfer);
  return transfer;
}

function guessMimeType(name = "") {
  const lowered = String(name || "").toLowerCase();
  if (lowered.endsWith(".png")) return "image/png";
  if (lowered.endsWith(".jpg") || lowered.endsWith(".jpeg")) return "image/jpeg";
  if (lowered.endsWith(".gif")) return "image/gif";
  if (lowered.endsWith(".webp")) return "image/webp";
  if (lowered.endsWith(".mp4")) return "video/mp4";
  if (lowered.endsWith(".mov")) return "video/quicktime";
  if (lowered.endsWith(".webm")) return "video/webm";
  if (lowered.endsWith(".pdf")) return "application/pdf";
  if (lowered.endsWith(".json")) return "application/json";
  if (lowered.endsWith(".md") || lowered.endsWith(".txt") || lowered.endsWith(".log")) return "text/plain";
  return "application/octet-stream";
}

async function readDocuments() {
  return allAgents().map(agentWorkReportDocument);
}

function agentWorkReportDocument(agent) {
  const messages = state.messages
    .filter((message) => agentReportBelongsToAgent(message, agent))
    .slice(-8);
  const toolCalls = messages.flatMap((message) => Array.isArray(message.toolCalls) ? message.toolCalls : []);
  const lastTool = toolCalls.at(-1);
  const failedTool = [...toolCalls].reverse().find((toolCall) => toolCall?.status === "FAILED");
  const updatedAt = finiteTimestamp(messages.at(-1)?.createdAt, Date.now());
  const recentLines = messages.length
    ? messages.map((message) => {
        const actor = message.authorId === "you" ? "User" : participantName(message.authorId);
        const summary = reportSafeText(message.text || (message.attachments?.length ? "shared attachment" : ""));
        return `- ${new Date(finiteTimestamp(message.createdAt, Date.now())).toISOString()} ${actor}: ${summary || "activity recorded"}`;
      })
    : ["- No recent work reported yet."];
  const content = [
    `${String(agent.status || "UNKNOWN").toLowerCase()} · ${agent.role || "agent"}`,
    `Tools: ${Array.isArray(agent.tools) && agent.tools.length ? agent.tools.join(", ") : "none reported"}`,
    `Last action: ${reportToolLabel(lastTool)}`,
    `Recent error: ${failedTool ? reportSafeText(failedTool.output || failedTool.input || "failed") : "none"}`,
    "Recent work:",
    ...recentLines,
  ].join("\n");
  return {
    id: `agent-report-${agent.id}`,
    title: agent.name,
    path: `agent-report://${agent.id}`,
    content,
    editable: false,
    updatedAt,
  };
}

function agentReportBelongsToAgent(message, agent) {
  if (!message || !agent?.id) return false;
  return message.authorId === agent.id || message.targetAgentId === agent.id;
}

function reportToolLabel(toolCall) {
  if (!toolCall?.toolName) return "none";
  const status = String(toolCall.status || "").toLowerCase();
  return `${toolCall.toolName}${status ? ` (${status})` : ""}`;
}

function reportSafeText(value = "") {
  return boundedText(String(value || "")
    .replace(/\b\d{4}\s?\d{4}\b/g, "**** ****")
    .replace(/\b(bearer|token|secret|key|password)=([^,\s]+)/gi, "$1=<redacted>")
    .replace(/\s+/g, " "), 220);
}

async function writeProjectDocument(documentId, content) {
  const today = new Date().toISOString().slice(0, 10);
  const allowed = {
    memory: join(ROOT, "MEMORY.md"),
    queue: join(ROOT, "QUEUE.md"),
    heartbeat: join(ROOT, "daily", `${today}.md`),
  };
  const path = allowed[documentId];
  if (!path) throw new Error(`document ${documentId} is not editable`);
  await writeFile(path, content, "utf8");
  return {
    id: documentId,
    title: documentId,
    path,
    content,
    editable: true,
    updatedAt: Date.now(),
  };
}

async function safeRead(path) {
  try {
    return await readFile(path, "utf8");
  } catch {
    return "";
  }
}

function handleStream(req, res, session) {
  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
    "Access-Control-Allow-Origin": "*",
  });
  const client = { res, session };
  clients.add(client);
  sendSse(client, "snapshot", snapshot());
  const interval = setInterval(() => {
    heartbeat("bridge", "stream heartbeat");
    sendSse(client, "heartbeat", state.heartbeats[0]);
  }, 15000);
  req.on("close", () => {
    clearInterval(interval);
    clients.delete(client);
  });
}

function broadcast(type, payload) {
  for (const client of clients) sendSse(client, type, payload);
}

async function sendSse(client, type, payloadOrPromise) {
  const payload = await payloadOrPromise;
  const encrypted = encryptJson({ version: VERSION, type, id: id(), createdAt: Date.now(), payload }, client.session.key);
  client.res.write(`event: ${type}\n`);
  client.res.write(`data: ${JSON.stringify(encrypted)}\n\n`);
}

function requireSession(req, res) {
  const deviceId = req.headers["x-device-id"];
  const session = sessions.get(String(deviceId || ""));
  if (!session) {
    sendJson(res, 401, { error: "not_paired" });
    return null;
  }
  return session;
}

function deriveSessionKey(sharedSecret, localPublicDer, remotePublicDer) {
  const salt = orderedDigest(localPublicDer, remotePublicDer);
  return Buffer.from(hkdfSync("sha256", sharedSecret, salt, Buffer.from("agent-control pairing v1"), 32));
}

function encryptJson(value, key) {
  const nonce = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, nonce);
  const ciphertext = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return {
    nonce: base64UrlEncode(nonce),
    cipherText: base64UrlEncode(Buffer.concat([ciphertext, tag])),
  };
}

function encryptForSession(session, type, payload) {
  return encryptJson({ version: VERSION, type, id: id(), createdAt: Date.now(), payload }, session.key);
}

function decryptJson(payload, key) {
  const nonce = base64UrlDecode(payload.nonce);
  const encrypted = base64UrlDecode(payload.cipherText);
  const tag = encrypted.subarray(encrypted.length - 16);
  const ciphertext = encrypted.subarray(0, encrypted.length - 16);
  const decipher = createDecipheriv("aes-256-gcm", key, nonce);
  decipher.setAuthTag(tag);
  const plain = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  const envelope = JSON.parse(plain.toString("utf8"));
  return envelope.payload ?? envelope;
}

function decryptWithSessionKeys(payload, session) {
  const keys = [session.key, ...(session.oldKeys || [])];
  for (const key of keys) {
    try {
      return { payload: decryptJson(payload, key), key };
    } catch {
      // Try the next recent key for this paired device.
    }
  }
  return null;
}

function orderedDigest(left, right) {
  const leftFirst = Buffer.compare(left, right) <= 0;
  return createHash("sha256")
    .update(leftFirst ? left : right)
    .update(leftFirst ? right : left)
    .digest();
}

function fingerprint(bytes) {
  return createHash("sha256").update(bytes).digest().subarray(0, 12).toString("hex").match(/../g).join(":").toUpperCase();
}

function heartbeat(source, text) {
  state.heartbeats.unshift({ id: id(), source, text, createdAt: Date.now() });
  state.heartbeats.splice(30);
}

function seedMessages() {
  state.messages.push({
    id: id(),
    authorId: "system",
    kind: "SYSTEM",
    text: BRIDGE_ONLINE_MESSAGE,
    createdAt: Date.now(),
    targetAgentId: null,
    attachments: [],
    toolCalls: [],
  });
  pruneMessages();
  heartbeat("bridge", "daemon started");
}

function localAddresses() {
  const addresses = [];
  for (const values of Object.values(networkInterfaces())) {
    for (const item of values || []) {
      if (item.family === "IPv4" && !item.internal) addresses.push(item.address);
    }
  }
  return addresses;
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.setEncoding("utf8");
    req.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > 50 * 1024 * 1024) reject(new Error("request too large"));
    });
    req.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (error) {
        reject(error);
      }
    });
  });
}

function sendEncrypted(res, session, type, payload) {
  sendJson(res, 200, encryptForSession(session, type, payload));
}

function sendHtml(res, value) {
  res.writeHead(200, {
    "Content-Type": "text/html; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
  });
  res.end(value);
}

function sendJson(res, status, value) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
  });
  if (status === 204) return res.end();
  res.end(JSON.stringify(value));
}

function setCors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,PATCH,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "content-type,x-device-id");
}

function base64UrlEncode(bytes) {
  return Buffer.from(bytes).toString("base64url");
}

function base64UrlDecode(text) {
  return Buffer.from(text, "base64url");
}

function id() {
  return randomBytes(16).toString("hex");
}
