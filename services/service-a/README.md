# service-a — Gateway Service

service-a is a Spring Boot 3.2 gateway that accepts HTTP requests from clients and proxies them to [service-b](../service-b/README.md) using Apache HttpClient 5. Its purpose is to demonstrate how **502 Bad Gateway**, **503 Service Unavailable**, and **504 Gateway Timeout** errors are produced and how they can be prevented.

---

## Architecture

```
Client
  │
  ▼
service-a :8081          (this service)
  │   RestClient + Apache HttpClient 5
  │   configurable: connect timeout, read timeout, keep-alive, pool size
  ▼
service-b :8082          (see ../service-b)
  │   endpoints: /api/data (fast), /api/slow (configurable delay)
  │   Tomcat keep-alive-timeout: 5s
```

The design intentionally creates a **mismatch** between the client and server:

| Setting | Value | Why |
|---------|-------|-----|
| service-b keep-alive-timeout | 5s | Server closes idle connections after 5s |
| service-a pool idle-timeout | 30s | Client holds connections for 30s → stale connection → 502 |
| service-a read-timeout | 5s | Client waits 5s for response → exceeded by slow endpoint → 504 |

---

## Quick Start

```bash
# From services/ directory
docker compose up --build -d
curl http://localhost:8081/api/config   # see active settings
curl http://localhost:8081/api/call     # happy path → 200
```

See [DEMO-HANDBOOK.md](../DEMO-HANDBOOK.md) for full demo instructions.

---

## Key Source Files

### 1. `HttpClientProperties.java` — configuration model

```
src/main/java/com/sbxservice/servicea/config/HttpClientProperties.java
```

A `@ConfigurationProperties` class that binds `application.yml` values and makes them injectable. Every timeout in the system flows through this class.

```java
@ConfigurationProperties(prefix = "app.http-client")
public class HttpClientProperties {
    private String baseUrl;
    private int connectTimeoutMs;   // TCP handshake timeout
    private int readTimeoutMs;      // response wait timeout
    private Pool pool;
    private KeepAlive keepAlive;
}
```

**Why this matters:** All timeouts are externalised — no rebuild is needed to change them. You can override any value with an environment variable at container start (e.g. `APP_HTTP_CLIENT_READ_TIMEOUT_MS=2000`). This is what makes the demo configurable without touching code.

---

### 2. `HttpClientConfig.java` — the HTTP client bean

```
src/main/java/com/sbxservice/servicea/config/HttpClientConfig.java
```

This is the most important file. It wires Apache HttpClient 5 into a Spring `RestClient` bean with three deliberate decisions that make the error scenarios reproducible.

#### Decision 1 — Two layers of timeout

```java
// Layer 1: per-connection defaults in the pool
ConnectionConfig connConfig = ConnectionConfig.custom()
    .setConnectTimeout(Timeout.ofMilliseconds(props.getConnectTimeoutMs()))
    .setSocketTimeout(Timeout.ofMilliseconds(props.getReadTimeoutMs()))
    .build();

// Layer 2: per-request override (takes precedence)
RequestConfig requestConfig = RequestConfig.custom()
    .setResponseTimeout(Timeout.ofMilliseconds(props.getReadTimeoutMs()))
    .build();
```

HC5 applies the per-request config over the per-connection default. Both are set to the same value here for clarity, but in production they can differ (e.g. a longer default with shorter overrides per endpoint).

#### Decision 2 — Keep-alive strategy ignores the server's header (demo only)

```java
var keepAliveStrategy = (ConnectionKeepAliveStrategy) (response, context) -> {
    if (!props.getKeepAlive().isEnabled()) {
        return TimeValue.ZERO_MILLISECONDS;
    }
    // Intentionally ignores Keep-Alive: timeout=5 from service-b.
    // If we honoured it, the pool would expire the connection correctly
    // and open a fresh one — bypassing the 502 scenario entirely.
    return TimeValue.ofMilliseconds(props.getKeepAlive().getIdleTimeoutMs()); // 30s
};
```

In production, you should parse the `Keep-Alive: timeout=N` header and return that value — see Part 11 of the handbook.

#### Decision 3 — Disable automatic retries and connection validation (demo only)

```java
// Disable pre-reuse validation: HC5 normally checks if a pooled connection
// is still alive after 2s of inactivity. Disabling this means it blindly
// sends on the stale socket → NoHttpResponseException → 502.
cm.setValidateAfterInactivity(TimeValue.ofMilliseconds(-1));

// Disable auto-retry: HC5 normally retries once on a fresh connection when
// it gets NoHttpResponseException on an idempotent request. Disabling this
// lets the 502 surface to the caller instead of being silently resolved.
HttpClients.custom()
    .disableAutomaticRetries()
    ...
```

Both of these should be left at their defaults in production.

---

### 3. `GlobalExceptionHandler.java` — maps HC5 exceptions to HTTP status codes

```
src/main/java/com/sbxservice/servicea/exception/GlobalExceptionHandler.java
```

`RestClient` wraps all I/O failures in `ResourceAccessException`. The handler unwraps the root cause and maps it:

```java
@ExceptionHandler(ResourceAccessException.class)
public ResponseEntity<ErrorResponse> handleResourceAccess(ResourceAccessException ex) {
    Throwable cause = ex.getCause();

    if (cause instanceof SocketTimeoutException)
        → 504 Gateway Timeout       // read timeout OR connect timeout (ConnectTimeoutException extends SocketTimeoutException)

    if (cause instanceof ConnectException || cause instanceof SocketException)
        → 503 Service Unavailable   // connection refused, host unreachable, system error

    if (cause instanceof NoHttpResponseException || message contains "connection reset")
        → 502 Bad Gateway           // stale keep-alive connection reused
}
```

**The exception hierarchy is the critical detail:**

```
java.io.IOException
 └── java.net.SocketException
      └── java.net.SocketTimeoutException          → 504
           └── ConnectTimeoutException (HC5)        → 504  ← connect timeout, NOT 503
      └── java.net.ConnectException                 → 503  ← connection refused
 └── org.apache.hc.core5.http.NoHttpResponseException → 502  ← stale connection
```

Notice that a **TCP connect timeout** (SYN sent, no SYN-ACK) returns **504**, not 503, because `ConnectTimeoutException` extends `SocketTimeoutException`. A **connection refused** (immediate RST) returns **503** because it throws `ConnectException`. See Part 10 of the handbook for details.

---

### 4. `application.yml` — the mismatch that enables the 502 demo

```
src/main/resources/application.yml
```

```yaml
app:
  http-client:
    read-timeout-ms: 5000       # 504 fires when service-b delay > this
    keep-alive:
      idle-timeout-ms: 30000    # 30s pool timeout > service-b's 5s Tomcat timeout
                                # this mismatch is what makes the 502 reproducible
```

The 6-second gap between `idle-timeout-ms: 30000` and service-b's `keep-alive-timeout: 5000` is the window in which the 502 lives. If you set `idle-timeout-ms` to anything ≤ 5000, the 502 disappears — the pool retires the connection before service-b closes it.

---

## How Apache HttpClient 5 Decides: Reuse vs. New Connection

Every outgoing request goes through this decision tree inside `PoolingHttpClientConnectionManager`:

```
Request arrives
      │
      ▼
Is there an available connection for this route (host:port) in the pool?
      │
  YES ─────────────────────────────────────────────────────────────────┐
      │                                                                 │
      ▼                                                                 │
Has the connection exceeded connectionTimeToLive?                       │
      │                                                                 │
  YES → discard, try pool again                                        │
  NO  ──────────────────────────────────────────────────────────────── ▼
      │                                                      Has it been idle longer than
      │                                                      validateAfterInactivity?
      │                                                                 │
      │                                                    YES → send TCP probe
      │                                                         alive? → REUSE
      │                                                         dead?  → discard, try pool again
      │                                                    NO  → REUSE immediately (no probe)
      │
  NO (pool empty or all connections in use)
      │
      ▼
Has maxPerRoute been reached?
      │
  NO  → create a NEW connection (OS assigns a new ephemeral port)
  YES → wait up to connectionRequestTimeout for a slot
         timeout fires → ConnectionRequestTimeoutException → 503
```

### The role of `validateAfterInactivity`

This is the setting that determines whether HC5 **probes** a connection before reusing it:

```java
// In this demo: disabled — HC5 blindly reuses without probing (enables 502 demo)
cm.setValidateAfterInactivity(TimeValue.ofMilliseconds(-1));

// In production: probe connections idle for more than 2s
cm.setValidateAfterInactivity(TimeValue.ofSeconds(2));
```

With probing enabled, HC5 sends a minimal TCP check on the socket. If the socket is dead (service-b sent `FIN`), the probe fails immediately, HC5 discards that connection and opens a fresh one — returning 200 instead of 502.

### The role of the keep-alive strategy

After every successful response, HC5 calls the `ConnectionKeepAliveStrategy` to decide how long to keep the connection in the pool:

```java
// Returns the TTL for this connection
TimeValue ttl = keepAliveStrategy.getKeepAliveDuration(response, context);

if (ttl.isZero())     → close connection immediately (no reuse)
if (ttl.isPositive()  → put back in pool, expire after ttl
```

In this demo the strategy returns `idleTimeoutMs` (30s) regardless of what service-b advertises. In production it should honour the server's `Keep-Alive: timeout=N` header so the pool retires connections before the server closes them.

### How `clientPort` makes this observable

service-b includes `clientPort` (`HttpServletRequest.getRemotePort()`) in every response — the ephemeral source port of service-a's outgoing TCP socket. Because a reused connection reuses the same socket, the port stays the same:

```bash
curl http://localhost:8081/api/call   # → clientPort: 57498  (new connection)
curl http://localhost:8081/api/call   # → clientPort: 57498  (reused — same socket)
sleep 7
curl http://localhost:8081/api/call   # → 502                (service-a tried port 57498,
                                      #                       service-b already closed it)
```

---

## Pool Borrowing Strategy: How 5 Requests Use 6 Pooled Connections

Suppose the pool has 6 idle connections (p1–p6) for the `service-b:8082` route and 5 requests (r1–r5) arrive.

### Sequential requests (one at a time)

HC5 uses **LIFO** — the most recently returned connection is always picked first:

```
Pool (stack, top = most recent): [p1, p2, p3, p4, p5, p6]

r1 arrives → borrows p6 (top of stack)   pool: [p1..p5]  leased: {r1=p6}
r1 done    → returns p6                  pool: [p1..p5, p6]

r2 arrives → borrows p6 again            pool: [p1..p5]  leased: {r2=p6}
r2 done    → returns p6                  pool: [p1..p5, p6]

r3 → p6 → r4 → p6 → r5 → p6
```

**All 5 requests reuse p6. p1–p5 are never touched.**

`clientPort` will be the same number for all 5 responses.

LIFO is intentional: keeping one connection "hot" lets p1–p5 sit idle until they exceed `validateAfterInactivity` and get evicted. The pool naturally shrinks to the size actually needed. **Sizing a pool to 50 does not mean 50 connections are open — it means 50 can be open at peak concurrency.**

### Concurrent requests (all in-flight simultaneously)

```
Pool: [p1, p2, p3, p4, p5, p6]   ← all available

r1 arrives → borrows p6           pool: [p1..p5]
r2 arrives → borrows p5           pool: [p1..p4]
r3 arrives → borrows p4           pool: [p1..p3]
r4 arrives → borrows p3           pool: [p1..p2]
r5 arrives → borrows p2           pool: [p1]      ← one still available
```

All 5 complete and return their connections — pool is back to 6.

**Each request gets a different connection. p1 is never used.** Each response shows a different `clientPort`.

### What happens when the pool is exhausted

If a 6th request r6 arrives while r1–r5 are all still in-flight:

```
Pool: [p1]  ← one left

r6 arrives → borrows p1           pool: []   ← empty
```

A 7th request r7 now finds the pool empty **and** `maxPerRoute` reached:

```
r7 arrives → no available connection, maxPerRoute=6 reached
          → waits up to connectionRequestTimeout (default = connectTimeoutMs = 3s)
          → timeout fires → ConnectionRequestTimeoutException → 503
```

### Practical sizing rule

Pool size should match **concurrency**, not total throughput.

| Traffic | Avg response time | Concurrent in-flight | Recommended pool size |
|---------|-------------------|---------------------|-----------------------|
| 1000 req/s | 10ms | 1000 × 0.01 = **10** | 12–15 (10 + headroom) |
| 1000 req/s | 500ms | 1000 × 0.5 = **500** | 550–600 |
| 100 req/s | 5s | 100 × 5 = **500** | 550–600 |

Formula: `pool size ≥ requests_per_second × avg_response_time_seconds`

With this demo's default `maxPerRoute: 20`, the pool supports up to 20 simultaneous in-flight requests to service-b. For sequential traffic, 1 connection does the work of all 20.

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/call` | Proxy to service-b `/api/data` — happy path or 502/503 |
| `GET` | `/api/call-slow?delayMs=10000` | Proxy to service-b `/api/slow` — triggers 504 |
| `GET` | `/api/config` | Show current HTTP client settings |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/swagger-ui.html` | Interactive API docs |

---

## Error Response Format

All errors return a structured JSON body:

```json
{
  "httpStatus": 504,
  "error": "504 Gateway Timeout",
  "scenario": "TIMEOUT",
  "message": "service-b did not respond within 5000ms...",
  "upstreamUrl": "http://service-b:8082",
  "timestamp": "2026-06-04T16:00:00Z"
}
```

`scenario` is one of: `TIMEOUT`, `UNREACHABLE`, `STALE_CONNECTION`, `UPSTREAM_ERROR`.

---

## Configuration Override (no rebuild)

```bash
# Lower read timeout to 2s — makes 504 easier to trigger
docker compose run --rm -e APP_HTTP_CLIENT_READ_TIMEOUT_MS=2000 -p 8081:8081 service-a

# Point at a non-existent host — triggers 503 immediately
docker compose run --rm -e APP_HTTP_CLIENT_BASE_URL=http://nonexistent:9999 -p 8081:8081 service-a

# Disable keep-alive — prevents 502 stale-connection scenario
docker compose run --rm -e APP_HTTP_CLIENT_KEEP_ALIVE_ENABLED=false -p 8081:8081 service-a
```
