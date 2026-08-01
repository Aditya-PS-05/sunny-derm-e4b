# Sunny model delivery

## Product contract

| Product | Processing | Local download |
|---|---|---:|
| Sunny AI Cloud | Included server analysis with explicit consent | None |
| Sunny MoE Pro | On-device after verified Pro access | 3.082 GB, explicit/resumable |

Lite, Medium, and the legacy 5.86 GB offline Pro model are no longer Android products. On upgrade,
the app deletes their known ONNX/GGUF files and partial downloads. Subscription
Sunny MoE is the sole downloadable Pro weight pack. The included cloud model is
never downloaded.

## Signed Sunny-MoE catalog

The APK pins three immutable files: a mixed dense/MoE Q4_K_M text GGUF, an FP16
vision/projector GGUF, and a runtime manifest. The exact catalog totals
3,082,369,677 bytes. Downloads use
HTTPS, Range resume, full SHA-256 verification, and atomic `.part` finalization.
Wi-Fi and mobile-data transports are both supported.
After backend verification, Pro receives short-lived signed URLs in this shape:

```text
https://<entitlement-worker>/v1/models/sunny-moe-2.2b-v4-gguf/<file>?exp=...&sig=...
```

The private R2 bucket is never public; the Worker allow-lists only the three
catalog paths and preserves HTTP Range resume. `SUNNY_MODEL_BASE_URL` is now a
debug-only fallback. The local pack is not DRM. App-private storage raises extraction effort but
cannot make weights secret from the device owner.

## Runtime boundary

`SunnyMoeModel` and `SunnyMoeBridge` are attached to model selection. The app
will load only `libsunny_moe.so`; the former ONNX and Pro-local engines are
removed. The arm64 library is compiled from the pinned vendored source under:

```text
android/app/src/main/cpp/
```

The implemented runtime performs SmolVLM vision-token injection, Q4_K_M CPU
kernels, sequence-level top-1 routing, route pinning and greedy schema output.
An installed pack still fails closed if native initialization fails.

The final host-native smoke used the production prompt and a 600×450 image. On
the four-core GPU host CPU it processed 223 prompt tokens, returned all six
fields plus the disclaimer in 20.15 seconds, and peaked at 3,482,508 KB RSS.
This proves the native graph and pack are executable; it is not an Android
latency, thermal or memory benchmark.

## Included AI Cloud

The existing Gemma/E4B checkpoint remains on the inference server. Android
selects it when a current server authorization exists and the user enables cloud
processing. Cloud access is not tied to the Pro plan. No server-model GGUF URL
or digest exists in the Android download catalog.

## Reinstall retention

Sunny keeps downloaded weights in app-private storage. The Android manifest sets
`hasFragileUserData=true`, so supported Android uninstall flows offer **Keep app
data**. When the user chooses it, the verified model pack remains private and
`ModelDownloadManager.init()` detects it automatically after reinstall. Choosing
to delete app data removes the model and health records as expected; Sunny does
not place proprietary weights in public shared storage or request broad storage
access.

## Production activation still required

1. Upload the exact Sunny-MoE pack to the configured HTTPS path (not required
   for local `adb push` testing).
2. Validate `libsunny_moe.so` on representative 6/8/12 GB devices.
3. Configure short-lived authorization for included AI Cloud and Play-verified
   Pro access for Sunny-MoE downloads.
4. Complete rights, clinician-labelled phone-photo, skin-tone, safety, thermal,
   battery, peak-RSS and latency validation.

See `sunny_moe.md` and `exports/sunny_moe/benchmark-summary.json` for the GPU
quality, size, routing and integrity results.
