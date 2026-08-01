# Sunny freemium access worker

This Cloudflare Worker is the server authority for Sunny's Free and Pro plans.
The Android client never grants itself quota, Pro access, or model URLs.

## Product contract

| Plan | Cloud allowance | On-device Sunny MoE |
|---|---:|---|
| Free | 5/month, maximum 2/day | No |
| Pro | 100/month, maximum 25/day | Yes, unlimited local use |

Counters use UTC calendar days/months and a single atomic D1 upsert, so parallel
requests cannot exceed a limit. Failed upstream inference calls are refunded.
The app sends one random analysis ID across its safety retry, so an internal
second model call does not consume a second user allowance.
Successful responses return the authoritative remaining quota in response
headers; Android also mirrors successful consumption immediately in its UI.

Free access is keyed by a random app-install identifier—not a hardware,
advertising, or account ID. Reinstalling can reset Free quota, so add Play
Integrity or authenticated accounts before a large public launch. Apply a
Cloudflare rate-limit rule to `/v1/access/free` and `/v1/inference/*` as a second
abuse-control layer. A monthly scheduled task deletes counters after 62 days of
inactivity.

## Pro subscription

Create one Google Play subscription:

- Product ID: `sunny_pro`
- Monthly base plan: `monthly` (`P1M`)
- Annual base plan: `annual` (`P1Y`)
- Paid first-month offer: `intro-month`, tagged `sunny-pro-intro`

Use the country prices in `../play-pricing/regional-prices.csv`. Android queries
Play `ProductDetails`, groups eligible offers by base plan and renders the actual
localized checkout prices. There is intentionally no Plus tier and no zero-cost
trial that exposes the model pack without a verified payment.

## Private model delivery

The R2 bucket must remain private. Upload these exact keys:

```text
sunny-moe-2.2b-v4-gguf/sunny-moe-text-Q4_K_M.gguf
sunny-moe-2.2b-v4-gguf/sunny-moe-mmproj-F16.gguf
sunny-moe-2.2b-v4-gguf/manifest.json
```

Only a verified Pro purchase receives per-file HMAC-signed HTTPS URLs. URLs
expire after at most 12 hours (and never after the verified purchase), are allow-listed to those three paths and support HTTP
Range resume. Android still enforces the APK-pinned size and SHA-256 before
activating a file. The URLs raise extraction effort but cannot make weights
secret from a determined device owner.

## Create Cloudflare resources

```bash
cd ops/entitlement-worker
npx wrangler d1 create sunny-entitlements
npx wrangler r2 bucket create sunny-models-private
```

Copy the returned D1 ID into the commented `[[d1_databases]]` block in
`wrangler.toml`, uncomment both D1 and R2 bindings, then apply the migration:

```bash
npx wrangler d1 migrations apply sunny-entitlements --remote
```

Set secrets without writing them to source control:

```bash
npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_EMAIL
npx wrangler secret put GOOGLE_PRIVATE_KEY
openssl rand -base64 48 | npx wrangler secret put MODEL_TOKEN_SECRET
npx wrangler secret put INFERENCE_UPSTREAM_URL
npx wrangler secret put INFERENCE_UPSTREAM_TOKEN
```

Grant the Google service account only the Android Publisher permissions required
to read and acknowledge `com.sunny.skin` subscriptions. The inference upstream
must be the exact HTTPS chat-completions endpoint.

Upload the checksum-verified model pack only when deployment is authorized:

```bash
npx wrangler r2 object put \
  sunny-models-private/sunny-moe-2.2b-v4-gguf/sunny-moe-text-Q4_K_M.gguf \
  --file ../../exports/model_tiers/sunny-moe-2.2b-v4-gguf/sunny-moe-text-Q4_K_M.gguf
npx wrangler r2 object put \
  sunny-models-private/sunny-moe-2.2b-v4-gguf/sunny-moe-mmproj-F16.gguf \
  --file ../../exports/model_tiers/sunny-moe-2.2b-v4-gguf/sunny-moe-mmproj-F16.gguf
npx wrangler r2 object put \
  sunny-models-private/sunny-moe-2.2b-v4-gguf/manifest.json \
  --file ../../exports/model_tiers/sunny-moe-2.2b-v4-gguf/manifest.json
```

Validate and deploy:

```bash
npm install
npm run check
npm run deploy
```

Build Android with the deployed worker URL:

```bash
./android/gradlew -p android assembleBeta \
  -PentitlementApiUrl=https://<worker-host>
```

`SUNNY_MODEL_BASE_URL` remains only a debug fallback. Release builds require
signed URLs from this Worker. No Cloudflare resources, Play products, secrets,
model files, or external hosts are modified by the repository build itself.
