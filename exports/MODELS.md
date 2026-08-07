# Model weights — locations and integrity

The current cloud and phone candidate is the PAD-UFES-20-trained SmolVLM 500M
pair on `ssh sunny-gpu`. Large binaries are not stored in Git. The app catalog
is defined by
`exports/model_tiers/sunny-pad-smolvlm-500m-v1-gguf/manifest.json`.

## Current artifacts

| GPU path | Pack filename | Bytes | SHA-256 |
|---|---|---:|---|
| `~/models/smolvlm-derm-pad-Q4_K_M.gguf` | `sunny-pad-smolvlm-500m-Q4_K_M.gguf` | 303,250,432 | `fb64c371d4044c7556966bf5dbf12d8aa5fb3b628a8be9ba0841189dbffe4d64` |
| `~/models/mmproj-smolvlm-derm-pad-Q8_0.gguf` | `sunny-pad-smolvlm-500m-mmproj-Q8_0.gguf` | 108,782,144 | `ac585ec2ee776eab23c4502f1a71d9d90a4057752057c05ed2487d47dc31798f` |
| `ops/beta-gateway/derm.gbnf` | `derm.gbnf` | 304 | `bb7668aafa0c3b87cb5b10ecf9bd01037c6ad3ed8fdf01bc53e7b267538c2f09` |

The GPU also retains:

- `~/models/smolvlm-derm-pad-lora/` — reproducible LoRA adapter and training config.
- `~/models/smolvlm-derm-pad-merged/` — merged Hugging Face checkpoint.
- older Gemma/Sunny-MoE/ONNX experiments — retained for reproducibility only;
  they are not release products.

## Retrieve directly

```bash
scp sunny-gpu:~/models/smolvlm-derm-pad-Q4_K_M.gguf .
scp sunny-gpu:~/models/mmproj-smolvlm-derm-pad-Q8_0.gguf .
```

Rename them to the catalog filenames only when assembling the model pack. Do
not change bytes after hashing.

## Server and beta pack

The live GPU service reads the source paths above. The authenticated beta
download mirror is:

```text
/srv/sunny-models/sunny-pad-smolvlm-500m-v1-gguf/
```

Production delivery uses the identical six objects in the private R2 prefix
`sunny-pad-smolvlm-500m-v1-gguf/`.

## Licensing

SmolVLM 500M is Apache-2.0 licensed. PAD-UFES-20 is CC BY 4.0. The attribution,
DOI, modification notice, and complete Apache license are under `licenses/` and
are checksum-pinned pack objects. Never publish replacement weights without
updating the manifest, app catalogs, notices, and signed-download allow-list.
