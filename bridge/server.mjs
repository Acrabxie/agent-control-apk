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
const RELAY_POLL_MS = 1500;
const RELAY_OFFER_REFRESH_MS = 30_000;
const RELAY_FETCH_ATTEMPTS = 3;
const RELAY_FETCH_TIMEOUT_MS = 8_000;
const RELAY_CURL_TIMEOUT_SECONDS = 12;
const DISABLE_REAL_AGENTS = process.env.AGENT_CONTROL_DISABLE_REAL_AGENTS === "1";
const MAX_IN_MEMORY_MESSAGES = 800;
const MAX_PERSISTED_MESSAGES = 500;
const MAX_PROMPT_MEMORY_MESSAGES = 24;
const MAX_PROMPT_MEMORY_CHARS = 1200;
const BRIDGE_ONLINE_MESSAGE = "Desktop bridge online. Pair the phone, then stream encrypted agent events.";
const CODEX_DEFAULT_MODEL = "gpt-5.5";
const CODEX_MODEL_OPTIONS = [
  { id: "gpt-5.5", label: "5.5", contextLimitTokens: 400000 },
  { id: "gpt-5.4", label: "5.4", contextLimitTokens: 400000 },
  { id: "gpt-5.3-codex", label: "5.3 Codex", contextLimitTokens: 200000 },
  { id: "gpt-5.2", label: "5.2", contextLimitTokens: 200000 },
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
const TEAM_DIRECTIVE = "AGENT_CONTROL_CREATE_TEAM";
const TEAM_MESSAGE_DIRECTIVE = "AGENT_CONTROL_TEAM_MESSAGE";
const TEAM_ROUND_MAX_SPEAKERS = 2;

const commands = [
  ["/status", "Status", "team"],
  ["/agents", "Agents", "team"],
  ["/spawn", "Spawn", "selected"],
  ["/team", "Team", "team"],
  ["/team-create", "New team", "team"],
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

    if (req.method === "GET" && url.pathname === "/v1/slash-commands") {
      const session = requireSession(req, res);
      if (!session) return;
      return sendEncrypted(res, session, "slash.commands", commands);
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
  const addressLabel = RELAY_URL ? "Relay address" : "Phone address";
  const deepLink = pairingDeepLink({ phoneAddress, pairingKey: key.key });
  const qrSvg = loopback ? await pairingQrSvg(deepLink) : "";
  const keyMarkup = loopback
    ? `<div class="qr">${qrSvg}</div><div class="key">${escapeHtml(formatPairingKey(key.key))}</div><p>Scan the QR code with your phone camera, or enter this 8-digit key in the Android app. It expires at ${escapeHtml(new Date(key.expiresAt).toLocaleTimeString())}.</p>`
    : "<p>Open this page on the desktop to see the pairing key.</p>";
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
.key{font-size:48px;font-weight:750;letter-spacing:.12em;margin:22px 0 6px;color:#7dd3fc}
.qr{width:232px;height:232px;border-radius:18px;background:#fff;padding:14px;margin:20px 0 12px;box-sizing:border-box}
.qr svg{display:block;width:100%;height:100%}
.meta{margin-top:18px;padding-top:16px;border-top:1px solid #334155;font-size:14px;color:#94a3b8}
code{color:#b5e48c}
</style>
</head>
<body>
<main>
<div class="lock">LOCK</div>
<h1>Agent Control pairing</h1>
${keyMarkup}
<div class="meta">
<div>Computer: <code>${escapeHtml(hostname())}</code></div>
<div>${escapeHtml(addressLabel)}: <code>${escapeHtml(phoneAddress)}</code></div>
${RELAY_URL ? `<div>LAN fallback: <code>http://${escapeHtml(address)}:${PORT}</code></div>` : ""}
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
  if (job.kind === "slashCommands") {
    await cacheAndPostRelayResponse(job.requestId, 200, encryptForSession(session, "slash.commands", commands));
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
  for (const route of routes) {
    for (let attempt = 1; attempt <= RELAY_FETCH_ATTEMPTS; attempt += 1) {
      try {
        const fetchInit = route.dispatcher ? { ...init, dispatcher: route.dispatcher } : init;
        return await fetchWithTimeout(url, fetchInit, RELAY_FETCH_TIMEOUT_MS);
      } catch (error) {
        errors.push(`${route.name}: ${describeError(error)}`);
        if (attempt < RELAY_FETCH_ATTEMPTS) await delay(650 * attempt);
      }
    }
  }
  for (const route of routes) {
    try {
      console.error(`relay fetch falling back to curl ${route.name}: ${errors.at(-1) || "fetch failed"}`);
      return await curlRelayFetch(url, init, { useProxy: route.useProxy });
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
  const args = [
    "-sS",
    "--connect-timeout",
    "20",
    "--max-time",
    String(RELAY_CURL_TIMEOUT_SECONDS),
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
    timeout: (RELAY_CURL_TIMEOUT_SECONDS + 5) * 1000,
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
  return {
    id: sanitizeText(message.id, id(), 80),
    authorId: sanitizeText(message.authorId, "system", 80),
    kind: ["USER", "AGENT", "SYSTEM"].includes(message.kind) ? message.kind : "AGENT",
    text,
    createdAt: finiteTimestamp(message.createdAt, now),
    targetAgentId: message.targetAgentId == null ? null : sanitizeText(message.targetAgentId, "", 80) || null,
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
    commands,
    messages: state.messages,
    transfers: state.transfers,
    documents,
    heartbeats: state.heartbeats.slice(0, 30),
    runtimeSettings: {
      codex: codexRuntimeSnapshot(),
    },
  };
}

function runtimeContextFor(agent, payload) {
  const rootAgent = rootAdapterFor(agent);
  if (rootAgent.id !== "codex") return {};
  if (payload?.runtimeOptions) {
    codexRuntimeSettings = normalizeCodexRuntimeSettings({
      ...codexRuntimeSettings,
      ...payload.runtimeOptions,
    });
    schedulePrivateBridgeStateSave();
  }
  updateCodexContextEstimate();
  return { runtimeSettings: codexRuntimeSettings };
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
  const replyId = clientMessageId ? replyIdForClientMessage(clientMessageId) : id();
  const existingReply = clientMessageId ? state.messages.find((message) => message.id === replyId) : null;
  if (existingReply) return existingReply;
  mergeClientConversationContext(payload.conversationContext, targetAgentId);
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
    attachments: [],
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
  if (DISABLE_REAL_AGENTS || text.startsWith("/")) return false;
  const adapter = rootAdapterFor(agent);
  return ["codex", "claude", "antigravity", "gemini_cli", "opencode"].includes(adapter.id);
}

function pendingReplyFor(agent, text, replyId = id(), context = {}) {
  const now = Date.now();
  return {
    id: replyId,
    authorId: agent.id,
    kind: "AGENT",
    text: `${agent.name} received your message and is thinking...`,
    createdAt: now,
    targetAgentId: "you",
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
  if (!["codex", "claude"].includes(adapter.id)) {
    return { start() {}, stop() {}, report() {} };
  }
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
        text: "Preparing Codex context...",
        toolCalls: [toolCall("codex", "codex.context", "RUNNING", "recent conversation", `${settings.contextUsedTokens}/${settings.contextLimitTokens} tokens`)],
      },
      {
        text: "Starting desktop Codex...",
        toolCalls: [toolCall("codex", "codex.exec", "RUNNING", `codex exec -m ${settings.model}`, "launching CLI")],
      },
      {
        text: "Codex is working through the request...",
        toolCalls: [toolCall("codex", "codex.exec", "RUNNING", `codex exec -m ${settings.model}`, "waiting for model output")],
      },
      {
        text: "Still running; waiting for Codex to finish...",
        toolCalls: [toolCall("codex", "codex.exec", "RUNNING", `codex exec -m ${settings.model}`, "long-running reply")],
      },
    ];
  }
  return [
    {
      text: "Preparing Claude Code prompt...",
      toolCalls: [toolCall("claude", "claude.plan", "RUNNING", "permission-mode plan", "building prompt")],
    },
    {
      text: "Starting Claude Code...",
      toolCalls: [toolCall("claude", "claude.invoke", "RUNNING", "claude -p", "launching CLI")],
    },
    {
      text: "Claude Code is still working...",
      toolCalls: [toolCall("claude", "claude.invoke", "RUNNING", "claude -p", "waiting for output")],
    },
    {
      text: "Still waiting for Claude Code...",
      toolCalls: [toolCall("claude", "claude.invoke", "RUNNING", "claude -p", "long-running reply")],
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
  if (adapter.id === "codex") {
    const settings = normalizeCodexRuntimeSettings(context.runtimeSettings || codexRuntimeSettings);
    return [
      toolCall(agent.id, "codex.context", "SUCCESS", "recent conversation", `${settings.contextUsedTokens}/${settings.contextLimitTokens} tokens`, now),
      toolCall(agent.id, "codex.exec", "RUNNING", `codex exec -m ${settings.model}`, "running desktop Codex", now),
    ];
  }
  if (adapter.id === "claude") {
    return [
      toolCall(agent.id, "claude.prompt", "SUCCESS", "Claude Code prompt", "plan mode, project settings", now),
      toolCall(agent.id, "claude.invoke", "RUNNING", "claude -p", "waiting for Claude Code", now),
    ];
  }
  if (adapter.id === "gemini_cli") {
    return [
      toolCall(agent.id, "gemini.prompt", "SUCCESS", "Gemini CLI prompt", "approval-mode plan", now),
      toolCall(agent.id, "gemini.invoke", "RUNNING", "gemini --prompt", "waiting for Gemini CLI", now),
    ];
  }
  if (adapter.id === "antigravity") {
    return [
      toolCall(agent.id, "antigravity.agent", "RUNNING", "antigravity agent --json", "waiting for Antigravity", now),
    ];
  }
  if (adapter.id === "opencode") {
    return [
      toolCall(agent.id, "opencode.run", "RUNNING", "opencode run", "waiting for OpenCode", now),
    ];
  }
  return [toolCall(agent.id, text.startsWith("/") ? "slash.command" : "agent.adapter", "RUNNING", text || "(empty)", "waiting for output", now)];
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

function replaceMessage(message) {
  const index = state.messages.findIndex((item) => item.id === message.id);
  if (index >= 0) state.messages[index] = message;
  else state.messages.push(message);
  pruneMessages();
}

async function routeTeamMessage(team, payload) {
  const text = String(payload.text || "").trim();
  mergeClientConversationContext(payload.conversationContext, team.id);
  const userMessage = {
    id: id(),
    authorId: "you",
    kind: "USER",
    text: text || "sent to team",
    createdAt: Date.now(),
    targetAgentId: team.id,
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

function mergeClientConversationContext(messages = [], targetId = "") {
  if (!Array.isArray(messages) || !messages.length) return;
  const normalized = messages
    .map(normalizePersistedMessage)
    .filter(Boolean)
    .filter((message) => messageBelongsToTarget(message, targetId));
  if (!normalized.length) return;
  const byId = new Map(state.messages.map((message) => [message.id, message]));
  for (const message of normalized) byId.set(message.id, message);
  state.messages = [...byId.values()]
    .sort((left, right) => (left.createdAt || 0) - (right.createdAt || 0) || String(left.id).localeCompare(String(right.id)))
    .slice(-MAX_IN_MEMORY_MESSAGES);
}

function messageBelongsToTarget(message, targetId) {
  if (!targetId) return true;
  return message.targetAgentId === targetId || message.authorId === targetId;
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
  if (text === "/status") {
    const versions = await Promise.all([
      commandVersion("codex"),
      commandVersion("gemini"),
      commandVersion("opencode"),
      commandVersion("claude"),
      antigravityVersion(),
    ]);
    return versions.join("\n");
  }
  if (text === "/agents") {
    return allAgents()
      .map((item) => `${item.name}: ${item.status}, parent=${item.parentId || "none"}`)
      .join("\n");
  }
  if (text === "/team") {
    return allTeams()
      .map((team) => `${team.name}: admin=${agentName(team.adminAgentId)}, members=${team.memberIds.length}, shared=${team.sharedProfile}`)
      .join("\n");
  }
  if (text.startsWith("/team-create")) {
    const result = await createPersistentTeam(agent, parseTeamCommand(text, agent));
    return result.created
      ? `Created persistent team: ${result.team.name}. It will appear as a group chat in the Android app.`
      : `Persistent team already exists: ${result.team.name}.`;
  }
  if (text === "/tools") {
    return `${agent.name} tools: ${agent.tools.join(", ")}`;
  }
  if (text === "/memory") {
    return await safeRead(join(ROOT, "MEMORY.md"));
  }
  if (text === "/heartbeat") {
    const today = new Date().toISOString().slice(0, 10);
    return await safeRead(join(ROOT, "daily", `${today}.md`));
  }
  if (text === "/api") {
    return "Bridge API online: /v1/pairing-challenge /v1/pair /v1/stream /v1/messages /v1/files /v1/projects/{projectId}/documents/{documentId} /v1/slash-commands";
  }
  if (text === "/help") {
    return commands.map((command) => command.trigger).join(" ");
  }
  if (text.startsWith("/spawn")) {
    const result = await createPersistentSubagent(agent, parseSpawnCommand(text, agent));
    return result.created
      ? `Created persistent subagent: ${result.agent.name}. It will stay in the Android app after bridge restarts.`
      : `Persistent subagent already exists: ${result.agent.name}.`;
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

function allAgents() {
  return [...agentDefinitions, ...state.dynamicAgents];
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

async function applyAgentDirectives(actor, responseText, options = {}) {
  const { text, subagentSpecs, teamSpecs, teamMessageSpecs } = parseAgentDirectives(responseText);
  const createdAgents = [];
  const existingAgents = [];
  const createdTeams = [];
  const existingTeams = [];
  const postedTeamMessages = [];

  for (const spec of subagentSpecs) {
    const result = await createPersistentSubagent(actor, spec);
    if (result.created) createdAgents.push(result.agent);
    else existingAgents.push(result.agent);
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

  const notes = [
    ...createdAgents.map((child) => `Created persistent subagent: ${child.name}.`),
    ...existingAgents.map((child) => `Persistent subagent already exists: ${child.name}.`),
    ...createdTeams.map((team) => `Created persistent team: ${team.name}.`),
    ...existingTeams.map((team) => `Persistent team already exists: ${team.name}.`),
    ...postedTeamMessages.map((message) => `Posted to team: ${findTeam(message.targetAgentId)?.name || message.targetAgentId}.`),
  ];
  return {
    text: [text.trim(), ...notes].filter(Boolean).join("\n\n") || "Done.",
    createdAgents,
    createdTeams,
    postedTeamMessages,
  };
}

function parseAgentDirectives(value) {
  const subagentSpecs = [];
  const teamSpecs = [];
  const teamMessageSpecs = [];
  const lines = String(value || "").split("\n");
  const visible = [];
  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith(SUBAGENT_DIRECTIVE)) {
      const raw = trimmed.slice(SUBAGENT_DIRECTIVE.length).trim();
      const parsed = parseJsonSpec(raw) || { name: raw };
      subagentSpecs.push(parsed);
    } else if (trimmed.startsWith(TEAM_DIRECTIVE)) {
      const raw = trimmed.slice(TEAM_DIRECTIVE.length).trim();
      const parsed = parseJsonSpec(raw) || { name: raw };
      teamSpecs.push(parsed);
    } else if (trimmed.startsWith(TEAM_MESSAGE_DIRECTIVE)) {
      const raw = trimmed.slice(TEAM_MESSAGE_DIRECTIVE.length).trim();
      const parsed = parseJsonSpec(raw) || { text: raw };
      teamMessageSpecs.push(parsed);
    } else {
      visible.push(line);
    }
  }
  return { text: visible.join("\n"), subagentSpecs, teamSpecs, teamMessageSpecs };
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
    canSpawnChildren: true,
  };
  state.dynamicAgents.push(child);
  await savePersistedAgents();
  heartbeat("team", `Spawned persistent subagent ${child.name} under ${parent.name}`);
  broadcast("agent.spawned", child);
  broadcast("team.changed", snapshot());
  return { agent: child, created: true };
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

function recentConversationMessages(context = {}, target = null) {
  const currentMessageId = context.currentMessageId || "";
  const teamId = context.team?.id || "";
  const targetId = target?.id || "";
  return state.messages
    .filter((message) => {
      if (!message?.text || message.id === currentMessageId) return false;
      if (teamId) return message.targetAgentId === teamId || message.authorId === teamId;
      if (!targetId) return false;
      return message.targetAgentId === targetId || message.authorId === targetId;
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
    `${SUBAGENT_DIRECTIVE} {"name":"Short specialist name","role":"specific responsibility","tools":["direct-chat","report"]}`,
    "The bridge hides that directive from chat, persists the subagent, and includes it in future app snapshots. Create at most two unless the user asks for more.",
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
    canSpawnChildren: true,
  };
}

async function claudeReply(text, context = {}) {
  const startedAt = Date.now();
  const progressReport = typeof context.progressReport === "function" ? context.progressReport : () => {};
  const prompt = [
    "You are Claude Code replying through the Agent Control Android bridge.",
    "API contract: Android sends encrypted POST /v1/messages with payload { text, targetAgentId, attachments }; the bridge routes targetAgentId=claude here and returns one plain chat reply in the encrypted message.accepted envelope.",
    "Reply directly and concisely. Use Chinese unless the user clearly asks otherwise.",
    "Do not edit files or run tools for ordinary chat; this bridge call is a single-turn agent reply.",
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
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
    progressReport("Claude Code is reading the prompt...", [
      toolCall("claude", "claude.plan", "RUNNING", "project settings", "preparing CLI prompt", startedAt),
    ]);
    const { stdout, stderr } = await spawnFilePromise("claude", [
      "-p",
      "--output-format",
      "text",
      "--permission-mode",
      "plan",
      "--tools",
      "",
      "--disable-slash-commands",
      "--setting-sources",
      "project",
      "--no-session-persistence",
      prompt,
    ], {
      timeout: 45000,
      maxBuffer: 1024 * 1024 * 6,
      env: strippedAgentEnv(),
      onStdout: () => progressReport("Claude Code produced output; preparing reply...", [
        toolCall("claude", "claude.invoke", "RUNNING", "claude -p", "stdout received", startedAt),
      ]),
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
      toolCall("claude", "claude.plan", "SUCCESS", "permission-mode plan", "prepared Claude Code prompt", startedAt),
      toolCall("claude", "claude.invoke", "SUCCESS", "claude -p --output-format text", "Claude Code returned output", startedAt),
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

function isClaudeAuthError(error) {
  const message = String(error?.message || "");
  return message.includes("401") || /authenticat|login|unauthorized/i.test(message);
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
  if (!command) return agentResponse(`${visibleName} CLI is not available on this desktop.`, [
    toolCall("antigravity", "antigravity.agent", "FAILED", "locate CLI", "command not found", startedAt),
  ]);
  const prompt = [
    `You are ${visibleName} replying through the Agent Control Android bridge.`,
    `API contract: Android sends encrypted POST /v1/messages with payload { text, targetAgentId, attachments }; the bridge routes it to ${visibleName} and returns one plain chat reply in the encrypted message.accepted envelope.`,
    "Reply directly and concisely. Use Chinese unless the user clearly asks otherwise.",
    "Do not edit files or run tools for ordinary chat; this bridge call is a single-turn agent reply.",
    "Ignore prior probe prompts or stale sentinel strings in any reused desktop agent session. Answer only the current user message below.",
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
    "",
    `User message: ${text || "(empty)"}`,
  ].join("\n");

  try {
    const { stdout, stderr } = await spawnFilePromise(command, [
      "agent",
      "--agent",
      agentId,
      "--json",
      "--thinking",
      "off",
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
    });
    const reply = extractAgentJsonReply(stdout);
    if (!reply) {
      console.error(`${visibleName} empty reply. stdout=${tailText(stdout)} stderr=${tailText(stderr)}`);
      return agentResponse("", [
        toolCall("antigravity", "antigravity.agent", "FAILED", `${command} agent --json`, "empty output", startedAt),
      ]);
    }
    return agentResponse(reply, [
      toolCall("antigravity", "antigravity.agent", "SUCCESS", `${command} agent --json`, "agent returned output", startedAt),
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
  const prompt = [
    "You are Gemini CLI replying through the Agent Control Android bridge.",
    "API contract: Android sends encrypted POST /v1/messages with payload { text, targetAgentId, attachments }; the bridge routes targetAgentId=gemini_cli here and returns one plain chat reply in the encrypted message.accepted envelope.",
    "Reply directly and concisely. Use Chinese unless the user clearly asks otherwise.",
    "Do not run tools or edit files for ordinary chat; this bridge call is a single-turn agent reply.",
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
    "",
    `User message: ${text || "(empty)"}`,
  ].join("\n");

  try {
    const { stdout, stderr } = await spawnFilePromise("/bin/zsh", [
      "-lc",
      'source "$HOME/.zshrc"; gemini --prompt "$1" --approval-mode plan --output-format text',
      "agent-control-gemini",
      prompt,
    ], {
      timeout: 180000,
      maxBuffer: 1024 * 1024 * 6,
      env: {
        ...process.env,
        NO_COLOR: "1",
      },
    });
    const reply = cleanCliReply(stdout);
    if (!reply) {
      console.error(`Gemini empty reply. stdout=${tailText(stdout)} stderr=${tailText(stderr)}`);
      return agentResponse("Gemini CLI finished but returned no text.", [
        toolCall("gemini_cli", "gemini.invoke", "FAILED", "gemini --prompt", "empty output", startedAt),
      ]);
    }
    return agentResponse(reply, [
      toolCall("gemini_cli", "gemini.prompt", "SUCCESS", "approval-mode plan", "prepared Gemini prompt", startedAt),
      toolCall("gemini_cli", "gemini.invoke", "SUCCESS", "gemini --prompt", "Gemini CLI returned output", startedAt),
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
  const prompt = [
    "You are OpenCode replying through the Agent Control Android bridge.",
    "API contract: Android sends encrypted POST /v1/messages with payload { text, targetAgentId, attachments }; the bridge routes targetAgentId=opencode here and returns one plain chat reply in the encrypted message.accepted envelope.",
    "Use the configured OpenCode default provider/model, DeepSeek V4-Pro, unless the bridge command explicitly overrides it.",
    "Reply directly and concisely. Use Chinese unless the user clearly asks otherwise.",
    "Do not edit files or run tools for ordinary chat; this bridge call is a single-turn agent reply.",
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
    "",
    `User message: ${text || "(empty)"}`,
  ].join("\n");

  try {
    const { stdout, stderr } = await spawnFilePromise("opencode", [
      "run",
      "--model",
      "deepseek/deepseek-v4-pro",
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
      toolCall("opencode", "opencode.run", "SUCCESS", "opencode run --model deepseek/deepseek-v4-pro", "OpenCode returned output", startedAt),
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
    ...conversationMemoryLines(context),
    ...subagentContextLines(context),
    ...teamContextLines(context),
    ...subagentDirectiveLines(),
    ...teamDirectiveLines(),
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
      progressReport(`Starting Codex with ${model}...`, [
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
      progressReport(`Codex model ${model} is unavailable; trying fallback...`, [
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
          progressReport("Codex is waiting on the model channel...", [
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
    progressReport("Codex finished; preparing final answer...", [
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
    const lines = buffer.split("\n");
    for (let index = 0; index < lines.length; index += 1) {
      const marker = lines[index].trim();
      if (marker === "exec") {
        const commandLine = (lines[index + 1] || "").trim();
        if (commandLine && !seen.has(`exec:${commandLine}`)) {
          seen.add(`exec:${commandLine}`);
          progressReport("Codex is running a command...", [
            toolCall("codex", "codex.run", "RUNNING", commandLine, "command in progress", startedAt),
          ]);
        }
      }
      if (marker === "apply_patch") {
        const patchLine = (lines[index + 1] || "").trim() || "apply_patch";
        if (!seen.has(`patch:${patchLine}`)) {
          seen.add(`patch:${patchLine}`);
          progressReport("Codex is editing files...", [
            toolCall("codex", "codex.edit", "RUNNING", patchLine, "patch in progress", startedAt),
          ]);
        }
      }
      if (marker === "codex" && !seen.has("answer")) {
        seen.add("answer");
        progressReport("Codex is writing the reply...", [
          toolCall("codex", "codex.answer", "RUNNING", "final response", "writing", startedAt),
        ]);
      }
    }
  };
}

function codexToolCallsFromRun(stdout, stderr, settings, permission, startedAt) {
  const calls = [
    toolCall("codex", "codex.context", "SUCCESS", "recent conversation", `${settings.contextUsedTokens}/${settings.contextLimitTokens} tokens`, startedAt),
    toolCall("codex", "codex.exec", "SUCCESS", `codex exec -m ${settings.model}`, `${permission.sandbox}, ${settings.reasoningEffort}`, startedAt),
  ];
  calls.push(...parseCodexStdoutActions(stdout, startedAt));
  if (/compact/i.test(stdout) || /compact/i.test(stderr)) {
    calls.push(toolCall("codex", "codex.compact", "SUCCESS", "context", "auto-compaction noted by Codex CLI", startedAt));
  }
  calls.push(toolCall("codex", "codex.answer", "SUCCESS", "final response", "ready", Date.now()));
  return collapseToolCalls(calls).slice(-10);
}

function parseCodexStdoutActions(stdout = "", startedAt = Date.now()) {
  const lines = String(stdout || "").split("\n");
  const actions = [];
  for (let index = 0; index < lines.length; index += 1) {
    const marker = lines[index].trim();
    if (marker === "exec") {
      const commandLine = (lines[index + 1] || "").trim();
      const statusLine = (lines[index + 2] || "").trim();
      if (commandLine) {
        actions.push(toolCall("codex", "codex.run", statusLine.includes("failed") ? "FAILED" : "SUCCESS", commandLine, statusLine || "command finished", startedAt));
      }
    }
    if (marker === "apply_patch") {
      const patchLine = (lines[index + 1] || "").trim();
      actions.push(toolCall("codex", "codex.edit", "SUCCESS", patchLine || "apply_patch", "patch applied", startedAt));
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
  const marker = "\ncodex\n";
  const start = stdout.lastIndexOf(marker);
  let reply = start >= 0 ? stdout.slice(start + marker.length) : stdout;
  const tokenIndex = reply.indexOf("\ntokens used");
  if (tokenIndex >= 0) reply = reply.slice(0, tokenIndex);
  return cleanCliReply(reply);
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

async function readDocuments() {
  const today = new Date().toISOString().slice(0, 10);
  const docs = [
    ["memory", "MEMORY.md", join(ROOT, "MEMORY.md")],
    ["queue", "QUEUE.md", join(ROOT, "QUEUE.md")],
    ["heartbeat", "heartbeat", join(ROOT, "daily", `${today}.md`)],
    ["api", "Bridge API", join(PROJECT_ROOT, "README.md")],
  ];
  return Promise.all(
    docs.map(async ([idValue, title, path]) => ({
      id: idValue,
      title,
      path,
      content: await safeRead(path),
      editable: idValue !== "api",
      updatedAt: Date.now(),
    })),
  );
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
