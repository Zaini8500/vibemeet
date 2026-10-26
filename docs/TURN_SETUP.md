# TURN setup

Video is peer-to-peer when it can be. When both users are behind strict
NATs or firewalls, WebRTC needs a TURN relay — otherwise the call simply
fails to connect. VibeMeet's backend now generates per-user, time-limited
TURN credentials (see `backend/src/services/turn.ts`), so no static TURN
password is ever shipped in the app.

## Option A — self-hosted coturn (cheapest, full control)

1. Set the same secret in both places:
   - Backend env: `TURN_SHARED_SECRET=<long-random-secret>`
   - coturn config: `static-auth-secret=<same-secret>`
2. Run coturn (example, adjust ports/IP for your server):
   ```bash
   docker run -d --network host --name coturn \
     -v $(pwd)/turnserver.conf:/etc/coturn/turnserver.conf \
     coturn/coturn
   ```
   Minimal `turnserver.conf` (do not commit a real secret in it):
   ```
   listening-port=3478
   realm=vibemeet
   server-name=vibemeet
   use-auth-secret
   static-auth-secret=CHANGE-ME-SAME-AS-BACKEND
   no-multicast-peers
   ```
3. Backend env:
   ```
   TURN_URLS=turn:YOUR_SERVER_IP:3478?transport=udp,turn:YOUR_SERVER_IP:3478?transport=tcp,turns:YOUR_DOMAIN:5349?transport=tcp
   TURN_SHARED_SECRET=CHANGE-ME-SAME-AS-COTURN
   ```
   Note: `turns:` needs a TLS certificate on the TURN server.

## Option B — managed TURN (less ops work, pay per GB)

Twilio Network Traversal, Cloudflare Calls TURN, or Metered.ca. In that
case the provider gives you either a static credential (not recommended —
only use it if the provider has no time-limited option) or an API to mint
credentials. If your provider mints credentials via API, replace
`generateTurnCredentials()` with a call to that API — the Android side
already reads whatever `GET /match/ice-servers` returns, so no app change
is needed.

## How to test TURN

1. Start the backend with TURN configured and check
   `GET /match/ice-servers` returns a `turn:` entry with a username like
   `<expiry>:<userId>`.
2. On two phones, block P2P (for example, both on a restricted network or
   a VPN that blocks UDP) and start a call. If it connects, TURN relay is
   working. If it only connects on open Wi-Fi, TURN is not being used —
   check the TURN server logs and firewall (UDP 3478, TCP 3478/5349).
3. Watch TURN bandwidth costs: relayed video is the most expensive part
   of this app. P2P first, TURN only as fallback, is the cost control.
