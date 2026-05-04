const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,PATCH,OPTIONS",
  "access-control-allow-headers": "authorization,content-type,x-device-id,x-pairing-key",
};
const DESKTOP_STALE_MS = 120_000;
const DESKTOP_JOB_LEASE_MS = 30_000;
const JOB_RETENTION_MS = 10 * 60 * 1000;

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: JSON_HEADERS });
    const id = env.ROOMS.idFromName("global");
    return env.ROOMS.get(id).fetch(request);
  },
};

export class AgentControlRoom {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.waiters = new Map();
  }

  async fetch(request) {
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/v1/health") {
        return json(200, { ok: true, service: "agent-control-relay", mode: "digital-key" });
      }
      if (request.method === "POST" && url.pathname === "/v1/desktop/offer") {
        return this.desktopOffer(request);
      }
      if (request.method === "GET" && url.pathname === "/v1/desktop/poll") {
        return this.desktopPoll(request, url);
      }
      if (request.method === "POST" && url.pathname === "/v1/desktop/respond") {
        return this.desktopRespond(request);
      }
      if (request.method === "GET" && url.pathname === "/v1/pairing-challenge") {
        return this.pairingChallenge(request, url);
      }
      if (request.method === "POST" && url.pathname === "/v1/pair") {
        return this.enqueuePhoneRequest("pair", request);
      }
      if (request.method === "POST" && url.pathname === "/v1/messages") {
        return this.enqueuePhoneRequest("message", request);
      }
      if (request.method === "GET" && url.pathname === "/v1/snapshot") {
        return this.enqueuePhoneRequest("snapshot", request);
      }
      if (request.method === "GET" && url.pathname === "/v1/slash-commands") {
        return this.enqueuePhoneRequest("slashCommands", request);
      }
      if (request.method === "POST" && url.pathname === "/v1/files") {
        return this.enqueuePhoneRequest("file", request);
      }
      const projectPatch = url.pathname.match(/^\/v1\/projects\/([^/]+)\/documents\/([^/]+)$/);
      if (request.method === "PATCH" && projectPatch) {
        return this.enqueuePhoneRequest("projectPatch", request, {
          projectId: projectPatch[1],
          documentId: projectPatch[2],
        });
      }
      return json(404, { error: "not_found" });
    } catch (error) {
      return json(500, { error: "relay_error", detail: error.message });
    }
  }

  async desktopOffer(request) {
    const body = await readJson(request);
    const desktopId = String(body.desktopId || "");
    const secret = bearer(request);
    const pairingKey = normalizePairingKey(body.pairingKey || "");
    if (!desktopId || !secret || pairingKey.length !== 8 || !body.challenge?.sessionId) {
      return json(400, { error: "invalid_offer" });
    }
    const expiresAt = Number(body.expiresAt || body.challenge.expiresAt || 0);
    if (expiresAt <= Date.now()) return json(410, { error: "offer_expired" });

    const offer = {
      desktopId,
      secret,
      pairingKey,
      challenge: body.challenge,
      expiresAt,
      updatedAt: Date.now(),
    };
    await this.state.storage.put(`desktop:${desktopId}`, { secret, updatedAt: Date.now() });
    await this.state.storage.put(`offer:key:${pairingKey}`, offer);
    await this.state.storage.put(`offer:session:${body.challenge.sessionId}`, offer);
    const pairedDeviceIds = Array.isArray(body.pairedDeviceIds) ? body.pairedDeviceIds : [];
    await Promise.all(
      pairedDeviceIds
        .map((value) => String(value || "").trim())
        .filter((value) => value.length >= 16 && value.length <= 200)
        .slice(0, 100)
        .map((deviceId) => this.state.storage.put(`device:${deviceId}`, desktopId)),
    );
    await this.state.storage.setAlarm(Date.now() + 10 * 60 * 1000);
    return json(200, { ok: true, expiresAt });
  }

  async desktopPoll(request, url) {
    const desktopId = String(url.searchParams.get("desktopId") || "");
    const desktop = await this.requireDesktop(request, desktopId);
    if (desktop instanceof Response) return desktop;
    await this.state.storage.put(`desktop:${desktopId}`, { ...desktop, updatedAt: Date.now() });

    const queueKey = `queue:${desktopId}`;
    const queue = (await this.state.storage.get(queueKey)) || [];
    const now = Date.now();
    const activeQueue = queue.filter((job) => Number(job.createdAt || 0) + JOB_RETENTION_MS > now);
    const jobs = [];
    const leasedQueue = activeQueue.map((job) => {
      if (jobs.length >= 5) return job;
      if (Number(job.leasedUntil || 0) > now) return job;
      const leased = {
        ...job,
        attempts: Number(job.attempts || 0) + 1,
        leasedUntil: now + DESKTOP_JOB_LEASE_MS,
      };
      jobs.push(leased);
      return leased;
    });
    if (leasedQueue.length !== queue.length || jobs.length > 0) {
      await this.state.storage.put(queueKey, leasedQueue);
    }
    return json(200, { jobs });
  }

  async desktopRespond(request) {
    const body = await readJson(request);
    const desktopId = String(body.desktopId || "");
    const desktop = await this.requireDesktop(request, desktopId);
    if (desktop instanceof Response) return desktop;

    const requestId = String(body.requestId || "");
    if (!requestId) return json(400, { error: "missing_request_id" });
    const response = {
      status: Number(body.status || 500),
      body: body.body || {},
      createdAt: Date.now(),
    };
    await this.state.storage.put(`response:${requestId}`, response);
    await this.removeQueuedJob(desktopId, requestId);
    await this.state.storage.put(`desktop:${desktopId}`, { ...desktop, updatedAt: Date.now() });

    if (body.body?.accepted && body.body?.deviceId) {
      await this.state.storage.put(`device:${body.body.deviceId}`, desktopId);
    }

    const waiter = this.waiters.get(requestId);
    if (waiter) {
      this.waiters.delete(requestId);
      waiter(response);
    }
    return json(200, { ok: true });
  }

  async removeQueuedJob(desktopId, requestId) {
    const queueKey = `queue:${desktopId}`;
    const queue = (await this.state.storage.get(queueKey)) || [];
    const next = queue.filter((job) => job.requestId !== requestId);
    if (next.length !== queue.length) await this.state.storage.put(queueKey, next);
  }

  async pairingChallenge(request, url) {
    const key = normalizePairingKey(request.headers.get("x-pairing-key") || url.searchParams.get("key") || "");
    if (key.length !== 8) return json(400, { error: "missing_pairing_key" });
    const offer = await this.state.storage.get(`offer:key:${key}`);
    if (!offer || offer.expiresAt <= Date.now()) return json(404, { error: "pairing_key_not_found" });
    return json(200, offer.challenge);
  }

  async enqueuePhoneRequest(kind, request, metadata = {}) {
    const body = await readJson(request);
    let desktopId = "";
    let deviceId = "";
    if (kind === "pair") {
      const offer = await this.state.storage.get(`offer:session:${body.sessionId}`);
      if (!offer || offer.expiresAt <= Date.now()) return json(404, { accepted: false, error: "pairing_challenge_not_found" });
      desktopId = offer.desktopId;
    } else {
      deviceId = String(request.headers.get("x-device-id") || "");
      desktopId = (await this.state.storage.get(`device:${deviceId}`)) || "";
      if (!desktopId) return json(401, { error: "not_paired" });
      const desktop = await this.state.storage.get(`desktop:${desktopId}`);
      if (!desktop || Number(desktop.updatedAt || 0) + DESKTOP_STALE_MS <= Date.now()) {
        return json(503, { error: "desktop_offline" });
      }
    }

    const requestId = crypto.randomUUID();
    const job = { requestId, kind, deviceId, body, ...metadata, createdAt: Date.now(), attempts: 0, leasedUntil: 0 };
    const queueKey = `queue:${desktopId}`;
    const queue = (await this.state.storage.get(queueKey)) || [];
    queue.push(job);
    await this.state.storage.put(queueKey, queue.slice(-100));

    const response = await this.waitForResponse(requestId, responseTimeoutMs(kind));
    if (!response) return json(504, { error: "desktop_timeout" });
    return json(response.status, response.body);
  }

  waitForResponse(requestId, timeoutMs) {
    return new Promise((resolve) => {
      const timer = setTimeout(async () => {
        this.waiters.delete(requestId);
        const stored = await this.state.storage.get(`response:${requestId}`);
        resolve(stored || null);
      }, timeoutMs);
      this.waiters.set(requestId, (response) => {
        clearTimeout(timer);
        resolve(response);
      });
    });
  }

  async requireDesktop(request, desktopId) {
    if (!desktopId) return json(400, { error: "missing_desktop_id" });
    const desktop = await this.state.storage.get(`desktop:${desktopId}`);
    if (!desktop || desktop.secret !== bearer(request)) return json(401, { error: "desktop_not_authorized" });
    return desktop;
  }

  async alarm() {
    const now = Date.now();
    const offers = await this.state.storage.list({ prefix: "offer:" });
    const deletions = [];
    for (const [key, offer] of offers) {
      if (!offer || offer.expiresAt <= now) deletions.push(this.state.storage.delete(key));
    }
    const responses = await this.state.storage.list({ prefix: "response:" });
    for (const [key, response] of responses) {
      if (!response || Number(response.createdAt || 0) + 5 * 60 * 1000 <= now) deletions.push(this.state.storage.delete(key));
    }
    const desktops = await this.state.storage.list({ prefix: "desktop:" });
    for (const [key, desktop] of desktops) {
      if (!desktop || Number(desktop.updatedAt || 0) + DESKTOP_STALE_MS <= now) deletions.push(this.state.storage.delete(key));
    }
    const queues = await this.state.storage.list({ prefix: "queue:" });
    for (const [key, queue] of queues) {
      const retained = Array.isArray(queue)
        ? queue.filter((job) => Number(job.createdAt || 0) + JOB_RETENTION_MS > now)
        : [];
      deletions.push(retained.length ? this.state.storage.put(key, retained) : this.state.storage.delete(key));
    }
    await Promise.all(deletions);
    await this.state.storage.setAlarm(Date.now() + 10 * 60 * 1000);
  }
}

function bearer(request) {
  const header = request.headers.get("authorization") || "";
  return header.startsWith("Bearer ") ? header.slice("Bearer ".length).trim() : "";
}

function normalizePairingKey(value) {
  return String(value || "").replace(/\D/g, "");
}

async function readJson(request) {
  const text = await request.text();
  return text ? JSON.parse(text) : {};
}

function json(status, body) {
  return new Response(status === 204 ? null : JSON.stringify(body), { status, headers: JSON_HEADERS });
}

function responseTimeoutMs(kind) {
  return kind === "message" || kind === "projectPatch" || kind === "file" ? 300_000 : 45_000;
}
