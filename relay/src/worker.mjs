const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,PATCH,OPTIONS",
  "access-control-allow-headers": "authorization,content-type,x-device-id,x-pairing-key",
};
const DESKTOP_STALE_MS = 120_000;
const DESKTOP_JOB_LEASE_MS = 30_000;
const DESKTOP_POLL_WAIT_MS = 25_000;
const JOB_RETENTION_MS = 10 * 60 * 1000;
const RESPONSE_RETENTION_MS = 5 * 60 * 1000;

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
    this.desktops = new Map();
    this.offersByKey = new Map();
    this.offersBySession = new Map();
    this.deviceToDesktop = new Map();
    this.queues = new Map();
    this.responses = new Map();
    this.waiters = new Map();
    this.desktopPollWaiters = new Map();
  }

  async fetch(request) {
    const url = new URL(request.url);
    try {
      this.cleanupExpired();
      if (request.method === "GET" && url.pathname === "/v1/health") {
        return json(200, { ok: true, service: "agent-control-relay", mode: "digital-key" });
      }
      if (request.method === "POST" && url.pathname === "/v1/desktop/offer") {
        return await this.desktopOffer(request);
      }
      if (request.method === "GET" && url.pathname === "/v1/desktop/poll") {
        return await this.desktopPoll(request, url);
      }
      if (request.method === "POST" && url.pathname === "/v1/desktop/respond") {
        return await this.desktopRespond(request);
      }
      if (request.method === "GET" && url.pathname === "/v1/pairing-challenge") {
        return await this.pairingChallenge(request, url);
      }
      if (request.method === "POST" && url.pathname === "/v1/pair") {
        return await this.enqueuePhoneRequest("pair", request);
      }
      if (request.method === "POST" && url.pathname === "/v1/messages") {
        return await this.enqueuePhoneRequest("message", request);
      }
      if (request.method === "GET" && url.pathname === "/v1/snapshot") {
        return await this.enqueuePhoneRequest("snapshot", request);
      }
      if (request.method === "GET" && url.pathname === "/v1/diagnostics") {
        return await this.enqueuePhoneRequest("diagnostics", request);
      }
      if (request.method === "GET" && url.pathname === "/v1/slash-commands") {
        return await this.enqueuePhoneRequest("slashCommands", request);
      }
      if (request.method === "POST" && url.pathname === "/v1/files") {
        return await this.enqueuePhoneRequest("file", request);
      }
      const projectPatch = url.pathname.match(/^\/v1\/projects\/([^/]+)\/documents\/([^/]+)$/);
      if (request.method === "PATCH" && projectPatch) {
        return await this.enqueuePhoneRequest("projectPatch", request, {
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
    this.desktops.set(desktopId, { secret, updatedAt: Date.now() });
    this.offersByKey.set(pairingKey, offer);
    this.offersBySession.set(body.challenge.sessionId, offer);
    const pairedDeviceIds = Array.isArray(body.pairedDeviceIds) ? body.pairedDeviceIds : [];
    pairedDeviceIds
      .map((value) => String(value || "").trim())
      .filter((value) => value.length >= 16 && value.length <= 200)
      .slice(0, 100)
      .forEach((deviceId) => this.deviceToDesktop.set(deviceId, desktopId));
    return json(200, { ok: true, expiresAt });
  }

  async desktopPoll(request, url) {
    const desktopId = String(url.searchParams.get("desktopId") || "");
    const desktop = await this.requireDesktop(request, desktopId);
    if (isResponseLike(desktop)) return desktop;
    this.desktops.set(desktopId, { ...desktop, updatedAt: Date.now() });

    const payload = this.leaseJobs(desktopId);
    if (payload.jobs.length > 0) return json(200, payload);
    return json(200, await this.waitForDesktopJobs(desktopId));
  }

  leaseJobs(desktopId) {
    const queue = this.queues.get(desktopId) || [];
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
      this.queues.set(desktopId, leasedQueue);
    }
    return { jobs };
  }

  waitForDesktopJobs(desktopId) {
    return new Promise((resolve) => {
      const entry = {
        timer: null,
        resolve: () => {
          clearTimeout(entry.timer);
          this.removeDesktopPollWaiter(desktopId, entry);
          resolve(this.leaseJobs(desktopId));
        },
      };
      entry.timer = setTimeout(() => {
        this.removeDesktopPollWaiter(desktopId, entry);
        resolve({ jobs: [] });
      }, DESKTOP_POLL_WAIT_MS);
      const waiters = this.desktopPollWaiters.get(desktopId) || [];
      waiters.push(entry);
      this.desktopPollWaiters.set(desktopId, waiters);
    });
  }

  removeDesktopPollWaiter(desktopId, entry) {
    const waiters = this.desktopPollWaiters.get(desktopId) || [];
    const next = waiters.filter((waiter) => waiter !== entry);
    if (next.length) this.desktopPollWaiters.set(desktopId, next);
    else this.desktopPollWaiters.delete(desktopId);
  }

  wakeDesktopPollers(desktopId) {
    const waiters = this.desktopPollWaiters.get(desktopId) || [];
    if (!waiters.length) return;
    this.desktopPollWaiters.delete(desktopId);
    waiters.forEach((entry) => entry.resolve());
  }

  async desktopRespond(request) {
    const body = await readJson(request);
    const desktopId = String(body.desktopId || "");
    const desktop = await this.requireDesktop(request, desktopId);
    if (isResponseLike(desktop)) return desktop;

    const requestId = String(body.requestId || "");
    if (!requestId) return json(400, { error: "missing_request_id" });
    const response = {
      status: Number(body.status || 500),
      body: body.body || {},
      createdAt: Date.now(),
    };
    this.responses.set(requestId, response);
    await this.removeQueuedJob(desktopId, requestId);
    this.desktops.set(desktopId, { ...desktop, updatedAt: Date.now() });

    if (body.body?.accepted && body.body?.deviceId) {
      this.deviceToDesktop.set(body.body.deviceId, desktopId);
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
    const queue = this.queues.get(desktopId) || [];
    const next = queue.filter((job) => job.requestId !== requestId);
    if (next.length !== queue.length) this.queues.set(desktopId, next);
  }

  async pairingChallenge(request, url) {
    const key = normalizePairingKey(request.headers.get("x-pairing-key") || url.searchParams.get("key") || "");
    if (key.length !== 8) return json(400, { error: "missing_pairing_key" });
    const offer = this.offersByKey.get(key);
    if (!offer || offer.expiresAt <= Date.now()) return json(404, { error: "pairing_key_not_found" });
    return json(200, offer.challenge);
  }

  async enqueuePhoneRequest(kind, request, metadata = {}) {
    const body = await readJson(request);
    let desktopId = "";
    let deviceId = "";
    if (kind === "pair") {
      const offer = this.offersBySession.get(body.sessionId);
      if (!offer || offer.expiresAt <= Date.now()) return json(404, { accepted: false, error: "pairing_challenge_not_found" });
      desktopId = offer.desktopId;
    } else {
      deviceId = String(request.headers.get("x-device-id") || "");
      desktopId = this.deviceToDesktop.get(deviceId) || "";
      if (!desktopId) return json(401, { error: "not_paired" });
      const desktop = this.desktops.get(desktopId);
      if (!desktop || Number(desktop.updatedAt || 0) + DESKTOP_STALE_MS <= Date.now()) {
        return json(503, { error: "desktop_offline" });
      }
    }

    const requestId = crypto.randomUUID();
    const job = { requestId, kind, deviceId, body, ...metadata, createdAt: Date.now(), attempts: 0, leasedUntil: 0 };
    const queueKey = `queue:${desktopId}`;
    const queue = this.queues.get(desktopId) || [];
    queue.push(job);
    this.queues.set(desktopId, queue.slice(-100));
    this.wakeDesktopPollers(desktopId);

    const response = await this.waitForResponse(requestId, responseTimeoutMs(kind));
    if (!response) return json(504, { error: "desktop_timeout" });
    return json(response.status, response.body);
  }

  waitForResponse(requestId, timeoutMs) {
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        this.waiters.delete(requestId);
        const stored = this.responses.get(requestId);
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
    const desktop = this.desktops.get(desktopId);
    if (!desktop || desktop.secret !== bearer(request)) return json(401, { error: "desktop_not_authorized" });
    return desktop;
  }

  async alarm() {
    this.cleanupExpired();
  }

  cleanupExpired() {
    const now = Date.now();
    for (const [key, offer] of this.offersByKey) {
      if (!offer || Number(offer.expiresAt || 0) <= now) this.offersByKey.delete(key);
    }
    for (const [key, offer] of this.offersBySession) {
      if (!offer || Number(offer.expiresAt || 0) <= now) this.offersBySession.delete(key);
    }
    for (const [key, response] of this.responses) {
      if (!response || Number(response.createdAt || 0) + RESPONSE_RETENTION_MS <= now) this.responses.delete(key);
    }
    for (const [key, desktop] of this.desktops) {
      if (!desktop || Number(desktop.updatedAt || 0) + DESKTOP_STALE_MS <= now) this.desktops.delete(key);
    }
    for (const [key, queue] of this.queues) {
      const retained = Array.isArray(queue)
        ? queue.filter((job) => Number(job.createdAt || 0) + JOB_RETENTION_MS > now)
        : [];
      if (retained.length) this.queues.set(key, retained);
      else this.queues.delete(key);
    }
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

function isResponseLike(value) {
  return value && typeof value === "object" && typeof value.arrayBuffer === "function" && typeof value.status === "number";
}

function responseTimeoutMs(kind) {
  return kind === "message" || kind === "projectPatch" || kind === "file" ? 300_000 : 45_000;
}
