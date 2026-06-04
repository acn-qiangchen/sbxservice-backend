# HTTP 502/503/504 Demo — Handbook

## What Was Built

Two Spring Boot microservices in `services/`:

| Service | Port | Role |
|---------|------|------|
| **service-b** | 8082 | Backend — responds immediately or with a configurable delay |
| **service-a** | 8081 | Gateway — calls service-b via HTTP client, returns 502/503/504 on failure |

---

## Part 1 — Initial Setup (one-time)

### 1.1 Install Colima (Docker Desktop replacement)

```bash
brew install colima docker docker-compose
```

Register docker-compose as a Docker plugin:

```bash
mkdir -p ~/.docker
cat > ~/.docker/config.json <<'EOF'
{
  "cliPluginsExtraDirs": [
    "/opt/homebrew/lib/docker/cli-plugins"
  ]
}
EOF
```

### 1.2 Start Colima

```bash
colima start --cpu 2 --memory 4
```

You only need to do this once per reboot. To stop Colima when done:

```bash
colima stop
```

---

## Part 2 — Start the Services

```bash
cd services/

# First time: builds Docker images (~5 min — downloads Maven + JDK base images)
docker compose up --build -d

# Subsequent times: reuses cached layers (much faster)
docker compose up -d
```

Wait for both containers to become healthy:

```bash
docker compose ps
```

Both should show `(healthy)` before running any demo (~30 seconds after start).

---

## Part 3 — Verify Everything Works

```bash
# service-a health
curl http://localhost:8081/actuator/health

# service-b health
curl http://localhost:8082/actuator/health

# Read active configuration — do this first before any demo
curl http://localhost:8081/api/config
```

The `/api/config` response shows the active timeouts and explains which curl command triggers each error. Key values:

| Setting | Default | Meaning |
|---------|---------|---------|
| `readTimeoutMs` | 5000 | service-a waits 5s for a response before giving up |
| `keepAlive.idleTimeoutMs` | 30000 | service-a holds connections open for 30s |
| `automaticRetries` | DISABLED | stale connections surface as 502, not silently retried |

---

## Part 4 — Happy Path (200 OK)

```bash
curl http://localhost:8081/api/call
```

service-a calls service-b `/api/data`, gets an immediate response, and proxies it back:

```json
{
  "status": "ok",
  "upstream": "http://service-b:8082/api/data",
  "upstreamData": {
    "status": "ok",
    "message": "Data returned immediately",
    "delayMs": 0,
    "serverInstance": "abc123def456"
  },
  "elapsedMs": 45
}
```

---

## Part 5 — Scenario 1: 504 Gateway Timeout

**What happens:** service-b sleeps longer than service-a's read timeout.
Apache HttpClient raises `SocketTimeoutException` after 5000ms → service-a returns 504.

### Trigger

```bash
# Always quote the URL in zsh — ? is a glob character
curl -v "http://localhost:8081/api/call-slow?delayMs=10000"
```

Hangs for exactly 5 seconds, then returns:

```json
{
  "httpStatus": 504,
  "error": "504 Gateway Timeout",
  "scenario": "TIMEOUT",
  "message": "service-b did not respond within 5000ms..."
}
```

### Try the boundary

```bash
# 4900ms — just under the 5000ms timeout → 200 OK
curl "http://localhost:8081/api/call-slow?delayMs=4900"

# 5100ms — just over → 504
curl "http://localhost:8081/api/call-slow?delayMs=5100"
```

### Lower the timeout without rebuilding

```bash
docker compose stop service-a

docker compose run --rm \
  -e APP_HTTP_CLIENT_READ_TIMEOUT_MS=2000 \
  -p 8081:8081 \
  service-a
```

Now a 3-second delay triggers 504:

```bash
curl "http://localhost:8081/api/call-slow?delayMs=3000"
```

Restore: press `Ctrl+C`, then `docker compose up -d service-a`

---

## Part 6 — Scenario 2: 503 Service Unavailable

**What happens:** service-a cannot reach service-b at all (connection refused / host unreachable) → 503.

### Method A — Stop service-b

```bash
docker compose stop service-b

curl "http://localhost:8081/api/call"
```

Expected:

```json
{
  "httpStatus": 503,
  "error": "503 Service Unavailable",
  "scenario": "UNREACHABLE",
  "message": "Could not establish a connection to service-b..."
}
```

Restore:

```bash
docker compose start service-b
# Wait ~20 seconds for service-b to be healthy before the next test
```

### Method B — Wrong base URL (no container stop needed)

```bash
docker compose stop service-a

docker compose run --rm \
  -e APP_HTTP_CLIENT_BASE_URL=http://nonexistent-host:9999 \
  -p 8081:8081 \
  service-a
```

In another terminal:

```bash
curl "http://localhost:8081/api/call"
```

Returns 503 immediately — DNS lookup fails, TCP connection is never attempted.

Restore: `Ctrl+C`, then `docker compose up -d service-a`

---

## Part 7 — Scenario 3: 502 Bad Gateway (Stale Keep-Alive)

**What happens:** This is the subtlest scenario. Three settings work together to create it:

1. service-b closes idle TCP connections after **5 seconds** (`server.tomcat.keep-alive-timeout: 5000`)
2. service-a's pool is told to hold connections for **30 seconds** (`keep-alive.idle-timeout-ms: 30000`) — and critically, it **ignores** service-b's `Keep-Alive: timeout=5` response header (otherwise the pool would expire the connection correctly and open a fresh one, silently returning 200)
3. Automatic retries are **disabled** — so when service-a tries to write on the dead socket and gets `NoHttpResponseException`, it surfaces as 502 instead of being silently retried on a fresh connection

### Step-by-step

**Step 1 — Warm the connection pool and note the `clientPort`:**

```bash
curl "http://localhost:8081/api/call"
```

Look for `clientPort` inside `upstreamData`. This is the ephemeral TCP port service-a used for the outgoing connection. Same port = same TCP connection. Different port = new connection.

**Step 2 — Call again immediately and confirm the port is unchanged (connection reused):**

```bash
curl "http://localhost:8081/api/call"
```

`clientPort` should be identical to Step 1 — Apache HttpClient pulled the same socket from its pool.

**Step 3 — Wait 6 seconds** (service-b closes the idle connection at 5s):

```bash
sleep 6
```

**Step 4 — Call again** (service-a tries to reuse the now-stale connection):

```bash
curl -v "http://localhost:8081/api/call"
```

Expected:

```json
{
  "httpStatus": 502,
  "error": "502 Bad Gateway",
  "scenario": "STALE_CONNECTION",
  "message": "service-b closed the keep-alive connection before service-a could reuse it..."
}
```

### The "fix" — teaching moment

The standard production fix is to re-enable automatic retries:

1. Edit `service-a/src/main/java/com/sbxservice/servicea/config/HttpClientConfig.java`
2. Comment out `.disableAutomaticRetries()` (around line 80)
3. Rebuild: `docker compose up --build -d service-a`
4. Repeat steps 1–3 above → you now get **200** instead of 502

Apache HttpClient silently retried on a fresh connection. The 502 is only visible because retries are intentionally disabled in this demo.

### Tweak keep-alive to make 502 easier or harder to trigger

| Change | Effect |
|--------|--------|
| Raise `APP_HTTP_CLIENT_KEEP_ALIVE_IDLE_TIMEOUT_MS` to e.g. 60000 | Pool holds longer → 502 easier to trigger |
| Lower it below 5000 | Pool evicts before service-b does → 502 never happens |
| Set `APP_HTTP_CLIENT_KEEP_ALIVE_ENABLED=false` | One connection per request → no stale connections → no 502 |

---

## Part 8 — Swagger UI

Both services expose Swagger UI for interactive exploration:

| URL | Service |
|-----|---------|
| `http://localhost:8081/swagger-ui.html` | service-a — gateway endpoints |
| `http://localhost:8082/swagger-ui.html` | service-b — backend endpoints |

You can call service-b's `/api/slow` endpoint directly from its Swagger UI to observe the delay without going through service-a.

---

## Part 9 — Useful Commands

```bash
# Live logs from both services
docker compose logs -f

# Logs from service-a only
docker compose logs -f service-a

# Check service-b keep-alive settings
curl http://localhost:8082/api/status

# Restart all containers
docker compose restart

# Stop all containers (keeps images)
docker compose down

# Rebuild and restart a single service
docker compose up --build -d service-a
```

---

## Part 10 — Configuration Reference

All `app.http-client.*` values can be overridden at runtime via environment variables — no rebuild needed.

| application.yml key | Environment variable | Default | What it controls | Error when exceeded |
|---------------------|---------------------|---------|-----------------|---------------------|
| `app.http-client.base-url` | `APP_HTTP_CLIENT_BASE_URL` | `http://service-b:8082` | Where service-a sends requests | 503 if host is unreachable |
| `app.http-client.connect-timeout-ms` | `APP_HTTP_CLIENT_CONNECT_TIMEOUT_MS` | `3000` | TCP handshake timeout (see below) | **504** Gateway Timeout |
| `app.http-client.read-timeout-ms` | `APP_HTTP_CLIENT_READ_TIMEOUT_MS` | `5000` | Time to wait for first response byte after connected | **504** Gateway Timeout |
| `app.http-client.pool.max-total` | `APP_HTTP_CLIENT_POOL_MAX_TOTAL` | `50` | Max connections across all routes | **503** when pool exhausted and request-timeout fires |
| `app.http-client.keep-alive.enabled` | `APP_HTTP_CLIENT_KEEP_ALIVE_ENABLED` | `true` | Toggle keep-alive on/off | — |
| `app.http-client.keep-alive.idle-timeout-ms` | `APP_HTTP_CLIENT_KEEP_ALIVE_IDLE_TIMEOUT_MS` | `30000` | How long idle connections stay in pool | **502** when set longer than server's keep-alive timeout |

### What happens when `connect-timeout-ms` is exceeded (TCP handshake timeout)

`connect-timeout-ms` controls how long service-a waits for the **TCP 3-way handshake** to complete — the `SYN → SYN-ACK → ACK` exchange that happens before any HTTP byte is sent.

```
service-a                        service-b
    |  ── SYN ──────────────────→  |
    |  ← SYN-ACK ────────────────  |   ← must arrive within connect-timeout-ms
    |  ── ACK ──────────────────→  |
    |                              |   ← HTTP request starts only after this
```

When `connect-timeout-ms` fires before `SYN-ACK` arrives, Apache HttpClient 5 throws `ConnectTimeoutException`, which extends `java.net.SocketTimeoutException`. Our `GlobalExceptionHandler` catches `SocketTimeoutException` and returns **504 Gateway Timeout**.

**Why 504 and not 503?**

Semantically, a connect timeout feels like "service unavailable" — but the HTTP spec defines 503 as "the server is temporarily unable to handle the request" (implying the server was reached) and 504 as "the gateway did not receive a timely response from an upstream server". A connect timeout fits 504 more precisely: service-a (the gateway) did not receive any response from service-b within the allowed time.

**Common causes of connect timeout in production:**

| Cause | What is happening |
|-------|------------------|
| Upstream is overloaded | TCP backlog is full — `SYN` packets are queued or dropped |
| Wrong host / port | The packet is routed into a black hole — no `RST`, just silence |
| Firewall drops SYN | Same effect — the handshake never completes |
| Network partition | Packets between the two services are lost |

**Contrast with connection refused (503):**
If the upstream is completely down, the OS returns an immediate `RST` instead of silence — the handshake fails instantly rather than timing out. That throws `ConnectException` (not `SocketTimeoutException`), which our handler maps to **503**.

```
connect-timeout fires (silence)  → ConnectTimeoutException → SocketTimeoutException → 504
connection refused   (immediate) → ConnectException        → SocketException        → 503
```

service-b's keep-alive timeout lives in `service-b/src/main/resources/application.yml`:

```yaml
server:
  tomcat:
    keep-alive-timeout: 5000   # milliseconds — change requires rebuild
```

---

## Quick Reference

```bash
# Start
cd services && docker compose up -d

# Health check
curl http://localhost:8081/actuator/health

# Read config
curl http://localhost:8081/api/config

# 200 OK
curl "http://localhost:8081/api/call"

# 504 Gateway Timeout
curl "http://localhost:8081/api/call-slow?delayMs=10000"

# 503 Service Unavailable
docker compose stop service-b
curl "http://localhost:8081/api/call"
docker compose start service-b

# 502 Bad Gateway
curl "http://localhost:8081/api/call" && sleep 6 && curl "http://localhost:8081/api/call"

# Stop all
docker compose down
```

---

## Part 11 — How to Prevent 502 Bad Gateway in Production

The demo deliberately breaks three rules to make the 502 visible. Each broken rule is a real-world best practice. Here they are, most impactful first.

---

### Rule 1 — Client idle timeout must be shorter than server keep-alive timeout

This is the fundamental rule. A 502 from a stale connection happens because **the server closed the TCP connection before the client stopped using it**.

```
Server keep-alive timeout:  5s   ← service-b in the demo
Client pool idle timeout:  30s   ← service-a in the demo  (WRONG: longer than server)
```

The client pool held a connection it thought was valid for 30s, but the server closed it at 5s.

**The fix:** Always set the client's idle timeout shorter than the server's keep-alive timeout. A safe margin is 20–30%:

```yaml
# service-b (server) closes idle connections after 60s (production default)
server:
  tomcat:
    keep-alive-timeout: 60000   # 60s

# service-a (client) should retire connections before the server does
app:
  http-client:
    keep-alive:
      idle-timeout-ms: 45000    # 45s — 25% shorter than server's 60s ✓
```

In our demo, the equivalent correct setting would be `idle-timeout-ms: 3000` (shorter than service-b's 5s). With that, the pool would retire the connection cleanly and open a fresh one — never hitting a stale socket.

---

### Rule 2 — Honour the server's `Keep-Alive: timeout=N` response header

When a server sends `Keep-Alive: timeout=5` in its HTTP response, it is explicitly advertising how long it will keep the connection open. A well-behaved client reads this and schedules connection expiry accordingly.

In our demo, we **deliberately ignored** this header so the demo would work. The keep-alive strategy in `HttpClientConfig` was changed to:

```java
// DEMO ONLY — intentionally ignores server's Keep-Alive header
return TimeValue.ofMilliseconds(props.getKeepAlive().getIdleTimeoutMs()); // always 30s
```

In production, restore the header-aware strategy:

```java
// PRODUCTION — honour the server's advertised timeout
for (Header header : response.getHeaders("Keep-Alive")) {
    for (String part : header.getValue().split(",")) {
        if (part.trim().toLowerCase().startsWith("timeout=")) {
            long secs = Long.parseLong(part.trim().substring(8));
            return TimeValue.of(secs, TimeUnit.SECONDS);
        }
    }
}
return TimeValue.ofMilliseconds(props.getKeepAlive().getIdleTimeoutMs()); // fallback
```

This means the client always retires connections at or before the server's stated deadline — the stale-connection window closes to near zero.

---

### Rule 3 — Enable automatic retries for idempotent requests

Even with correct timeout settings, a race condition can still occur: the server closes the connection at exactly the moment the client sends a request. This is called a **race close** and cannot be eliminated — it is an inherent property of HTTP/1.1 keep-alive.

The standard defence is a **single automatic retry on `NoHttpResponseException`** for idempotent methods (GET, HEAD, PUT, DELETE). Apache HttpClient 5 does this by default. We disabled it in the demo specifically to expose the raw 502.

In production, **do not call `.disableAutomaticRetries()`**:

```java
return HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setDefaultRequestConfig(requestConfig)
        .setKeepAliveStrategy(keepAliveStrategy)
        // .disableAutomaticRetries()  ← remove this line in production
        .build();
```

With retries enabled, the race-close scenario is transparently handled: HC5 retries once on a fresh connection and the caller sees a normal 200.

> **Why retries are safe for GET but not always for POST:** Retrying a GET returns the same data — no side effects. Retrying a POST might create a duplicate record. For non-idempotent methods, disable retries and handle 502 at the caller level (e.g. with a circuit breaker) instead.

---

### Rule 4 — Enable connection validation before reuse

In our demo we set `validateAfterInactivity(-1)` to disable HC5's built-in connection health check. In production, leave it at the default (2 seconds) or set it explicitly:

```java
cm.setValidateAfterInactivity(TimeValue.ofSeconds(2));
```

With this enabled, HC5 silently checks whether a connection is still alive before sending a request on it, if the connection has been idle for more than 2 seconds. If the check fails, it opens a fresh connection. This adds a tiny overhead (one TCP probe) but eliminates most stale-connection 502s without needing a retry.

---

### Rule 5 — Set a hard connection TTL

Even a connection with a perfect idle timeout can become stale if it has been open for a very long time (servers can be restarted, load balancers can reset long-lived connections silently). Set a maximum connection lifetime:

```java
PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
        .setConnectionTimeToLive(TimeValue.ofMinutes(5))   // retire all connections after 5 min
        .build();
```

This limits how long any single TCP connection lives regardless of activity, giving you a hard safety net against all forms of silent connection closure.

---

### Summary Table

| Rule | What we broke in the demo | Production setting |
|------|--------------------------|-------------------|
| Client idle timeout < server keep-alive | `idle-timeout-ms: 30000` > server's 5s | Set client timeout shorter than server's |
| Honour server's `Keep-Alive` header | Ignored the header entirely | Parse and respect `Keep-Alive: timeout=N` |
| Enable automatic retries | `.disableAutomaticRetries()` | Remove that call (default = retry once) |
| Enable connection validation | `validateAfterInactivity(-1)` | Default 2s, or set explicitly |
| Set connection TTL | Not set | `setConnectionTimeToLive(5 minutes)` |

Applying Rules 1 + 2 eliminates the problem by design.
Rules 3 + 4 are defence-in-depth for the unavoidable race-close edge case.
Rule 5 is a long-term safety net against anything else that silently closes TCP connections (NAT timeouts, load balancer resets, server restarts).
