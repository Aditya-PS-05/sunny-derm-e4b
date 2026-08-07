# Sunny private-beta HTTPS gateway

This gateway preserves the Android API contract while putting TLS, bearer-token
checks, request limits, and rate limits in front of the GPU services.

## Deploy

1. Point a domain at the GPU host.
2. Obtain a trusted certificate and place `fullchain.pem` and `privkey.pem` in
   `certs/`. Keep `privkey.pem` readable only by the deploy account.
3. Copy `.env.example` to `.env`, set separate random tokens and upstreams, then
   run `docker compose up -d`.
4. Build the non-debuggable beta app with the HTTPS URLs:

```bash
SUNNY_INFERENCE_MODE=server \
SUNNY_INFERENCE_API_URL=https://beta.example.com \
SUNNY_CONTRIBUTION_MODE=enabled \
SUNNY_CONTRIBUTE_URL=https://beta.example.com \
./android/gradlew -p android assembleBeta
```

5. Install the APK, then enter the two tokens from the gateway `.env` in
   **Settings → Beta server access**. Runtime provisioning keeps shared server
   credentials out of the APK; the values are encrypted in app-private storage.

The access log records request metadata only, not bodies. Nginx does not solve
storage governance inside the contribution service: disable upstream request-body
logging, encrypt stored images, restrict operator access, document a retention
period, and provide deletion by contribution identifier before external testing.

The gateway currently uses operator-provisioned shared beta tokens. For a wider
beta, replace them with short-lived, per-install tokens issued after device/app
attestation so one tester cannot reuse another install's credential.

## Current GPU-host deployment

The private beta gateway is installed directly on the `gpu` host at:

```text
https://34-238-98-220.sslip.io
```

- Nginx terminates a trusted, automatically renewed Let's Encrypt certificate.
- `/beta-access/v1/access/free` exchanges an installation ID for short-lived
  private-beta authorization without user-entered credentials.
- `/beta-access/v1/inference/v1/chat/completions` validates that authorization,
  applies quota, and proxies to `127.0.0.1:8080`.
- `/beta-access/v1/models/...` validates expiring signed URLs and serves only the
  six allow-listed PAD-model, grammar, manifest, and license files with Range resume.
- The inference and legacy collection services bind only to loopback on `8080`
  and `8090`; neither port is present in public security-group ingress. Only
  SSH, HTTP certificate renewal, and HTTPS are exposed.
- `sunny-inference.service` binds the SmolVLM 500M llama-server to loopback,
  loads `/etc/sunny-inference/derm.gbnf`, and restarts after failure or reboot.

The checked-in Android Debug/Beta default already uses this broker. Build the
closed-beta artifact with:

```bash
./android/gradlew -p android bundleBeta
```

If the host changes, override it with
`-PbetaEntitlementApiUrl=https://<host>/beta-access` or the
`SUNNY_BETA_ENTITLEMENT_API_URL` environment variable. Public Release ignores
the beta origin and still requires its own production entitlement service.

The private broker currently grants beta Pro access so the complete flow can be
tested. Public releases must replace that beta grant with server-verified Play
entitlement before distribution.
