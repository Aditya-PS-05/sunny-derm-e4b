# Sunny Android model delivery

## Product contract

| Product | Processing | Delivery |
|---|---|---|
| Sunny AI Cloud | Server analysis with explicit consent | No local model |
| Sunny Offline Pro | On-device after verified Pro access | Google Play install-time asset pack |

Both routes use the PAD-UFES-20-trained SmolVLM 500M model and the same bounded
six-field grammar.

## One-install delivery

Android publishes an App Bundle containing the `sunny_model_pack` Play Asset
Delivery module with `install-time` delivery. Google Play installs its six
objects as part of the normal Sunny installation:

- Q4_K_M SmolVLM 500M model
- Q8_0 vision projector
- bounded GBNF grammar
- PAD-UFES-20/SmolVLM notices
- Apache License 2.0
- provenance manifest

The pack totals 412,049,555 uncompressed bytes. There is no in-app network
model download, foreground data-sync service, download notification, or R2
dependency for Android.

## First-launch preparation

Play install-time packs are exposed through Android `AssetManager`, while
llama.cpp requires ordinary file paths for memory mapping. On first launch,
`BundledModelInstaller` streams the already-installed six assets into private
runtime storage, checking the pinned size and SHA-256 of every file before
atomic activation. This is a local copy, not a network transfer.

The model therefore consumes storage twice: approximately 393 MiB in Play's
installed asset pack plus 393 MiB in Sunny's private runtime directory. Keep at
least about 0.9 GB free for installation and preparation.

## Build contract

Large GGUFs are intentionally Git-ignored. Stage and verify the pack, then
build the AAB:

```bash
cd android
./scripts/stage_bundled_model.sh
./gradlew bundleBeta
```

`verifyBundledModelPack` fails the bundle build if a file is missing, the size
differs, or a hash differs. Ordinary `assembleDebug` still creates a small APK
without the PAD module; use `bundleDebug` plus bundletool to test install-time
delivery locally.

## Google Play constraints

As of August 2026, an individual asset pack may have up to 1.5 GB compressed
download size and all install-time modules/packs may total 4 GB. Sunny's pack is
comfortably within those limits. Apps above 200 MB show a non-blocking large-app
notice on mobile data.

## Runtime boundary

`SunnyMoeModel`, `SunnyMoeBridge`, and `libsunny_moe.so` keep their historical
names for upgrade compatibility. They load the SmolVLM pair plus `derm.gbnf`,
apply the image-first SmolVLM template, and use grammar-constrained greedy
decoding with a 160-token fail-closed cap. Android uses the model's single
fixed 256 px global image view instead of the desktop 2048 px/13-pass tiled path.

See `exports/model_tiers/sunny-pad-smolvlm-500m-v1-gguf/manifest.json`.
