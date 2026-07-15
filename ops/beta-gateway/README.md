# Sunny private-beta HTTPS gateway

This gateway preserves the Android API contract while putting TLS, bearer-token
checks, request limits, and rate limits in front of the GPU services.

## Deploy

1. Point a domain at the GPU host.
2. Obtain a trusted certificate and place `fullchain.pem` and `privkey.pem` in
   `certs/`. Keep `privkey.pem` readable only by the deploy account.
3. Copy `.env.example` to `.env`, set separate random tokens and upstreams, then
   run `docker compose up -d`.
4. Build the beta app with the HTTPS URL and matching client tokens:

```bash
SUNNY_INFERENCE_MODE=server \
SUNNY_INFERENCE_API_URL=https://beta.example.com \
SUNNY_INFERENCE_API_TOKEN=... \
SUNNY_CONTRIBUTE_URL=https://beta.example.com \
SUNNY_CONTRIBUTE_API_TOKEN=... \
./android/gradlew -p android assembleDebug
```

The access log records request metadata only, not bodies. Nginx does not solve
storage governance inside the contribution service: disable upstream request-body
logging, encrypt stored images, restrict operator access, document a retention
period, and provide deletion by contribution identifier before external testing.

Static APK tokens deter casual abuse but can be extracted. For a wider beta,
replace them with short-lived tokens issued after device/app attestation.
