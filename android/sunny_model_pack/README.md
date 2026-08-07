# Sunny install-time model pack

This Play Asset Delivery module ships the checksum-pinned SmolVLM 500M pack as
part of the normal Google Play installation. It is `install-time`, so the app
does not request or download it after first launch.

Large `.gguf` files remain ignored by Git. Before building an AAB, stage and
verify all six objects with:

```bash
./scripts/stage_bundled_model.sh
./gradlew bundleBeta
```

The app copies the Play-installed assets once into private storage because
llama.cpp requires ordinary file paths. This is local preparation, not a
network transfer.
