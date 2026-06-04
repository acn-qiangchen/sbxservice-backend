# service-b — Backend Service

service-b is a simple Spring Boot 3.2 backend used as the upstream target for [service-a](../service-a/README.md). It provides endpoints that simulate different response behaviours — instant responses and configurable delays — so that service-a's HTTP client can be observed failing in different ways.

---

## Quick Start

```bash
# Run standalone
cd service-b && docker compose up --build -d

# Or run together with service-a (recommended for the demo)
cd .. && docker compose up --build -d
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/data` | Returns immediately — happy path |
| `GET` | `/api/slow?delayMs=10000` | Sleeps for `delayMs` ms before responding — triggers 504 in service-a |
| `GET` | `/api/status` | Server info and keep-alive configuration |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/swagger-ui.html` | Interactive API docs |

---

## Key Source Files

### 1. `BackendController.java` — the three endpoints

```
src/main/java/com/sbxservice/serviceb/controller/BackendController.java
```

#### `/api/data` — immediate response

```java
@GetMapping("/data")
public ResponseEntity<BackendResponse> getData() {
    return ResponseEntity.ok(BackendResponse.builder()
            .status("ok")
            .message("Data returned immediately")
            .delayMs(0)
            .timestamp(ZonedDateTime.now())
            .serverInstance(hostname())
            .build());
}
```

Used by service-a's `/api/call`. Returns instantly with no artificial delay.

#### `clientPort` — connection identity indicator

Every response includes `clientPort`: the ephemeral TCP source port of the caller as seen by Tomcat (`HttpServletRequest.getRemotePort()`).

```java
.clientPort(request.getRemotePort())
```

Each unique TCP connection from service-a has a unique source port assigned by the OS. When Apache HttpClient reuses a pooled connection, it reuses the same socket — and therefore the same source port. When it opens a new connection, the OS assigns a new ephemeral port.

```
Call 1 → clientPort: 57498   (new connection, OS assigned port)
Call 2 → clientPort: 57498   (same port = same TCP connection reused)
wait 7s (service-b closes socket at 5s)
Call 3 → 502                 (service-a tries port 57498 again, but server already closed it)
```

This makes connection reuse directly observable without any external tooling.

#### `/api/slow` — configurable delay

```java
@GetMapping("/slow")
public ResponseEntity<BackendResponse> getSlow(
        @RequestParam(defaultValue = "10000") long delayMs) throws InterruptedException {
    Thread.sleep(delayMs);
    return ResponseEntity.ok(...);
}
```

`Thread.sleep(delayMs)` holds the HTTP connection open for `delayMs` milliseconds before sending any response byte. service-a's read timeout clock starts ticking the moment the TCP connection is established — so if `delayMs` exceeds service-a's `read-timeout-ms` (default 5000ms), service-a's `SocketTimeoutException` fires and it returns 504 to the client.

**Why `Thread.sleep` and not a non-blocking delay?**
`Thread.sleep` is intentionally blocking — it ties up the Tomcat worker thread and keeps the socket open without writing any data. This is the correct simulation of a slow upstream: the connection is accepted but the response is withheld.

---

### 2. `application.yml` — the keep-alive timeout setting

```
src/main/resources/application.yml
```

```yaml
server:
  tomcat:
    keep-alive-timeout: 5000   # close idle HTTP/1.1 connections after 5s
```

This is the **most important configuration line** in service-b for the demo. When a request completes, Tomcat includes `Keep-Alive: timeout=5` in the response headers and keeps the TCP socket open for up to 5 seconds waiting for the next request. After 5 seconds of inactivity, Tomcat closes the socket with a TCP `FIN`.

**The mismatch this creates:**

```
service-b closes idle socket after:   5s   (this file)
service-a pool holds connection for:  30s  (service-a application.yml)
```

service-a does not honour service-b's `Keep-Alive: timeout=5` header — it keeps the connection in its pool for the full 30 seconds. After 5 seconds, service-b has sent `FIN` and closed its end. When service-a tries to write on that socket at second 7, it gets `NoHttpResponseException` → **502 Bad Gateway**.

To make the mismatch visible vs. hidden:

| `keep-alive-timeout` in service-b | Result |
|-----------------------------------|--------|
| 5000ms (current) | Server closes socket before client retires it → 502 possible |
| 30000ms or more | Server and client agree → no stale connection → 502 never happens |

---

## Response Format

```json
{
  "status": "ok",
  "message": "Data returned immediately",
  "delayMs": 0,
  "timestamp": "2026-06-04T16:00:00Z",
  "serverInstance": "a1b2c3d4e5f6"
}
```

`serverInstance` is the container hostname — useful when running multiple replicas to see which instance responded.
