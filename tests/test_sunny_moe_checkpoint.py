import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

try:
    import torch
    from torch import nn
    from safetensors import safe_open  # noqa: F401
    from sunny_moe.checkpoint import load_adapter_checkpoint, save_adapter_checkpoint
    from sunny_moe.surgery import SunnyMoeSpec, upcycle_model
except ImportError:
    torch = None


if torch is None:
    @unittest.skip("torch and safetensors are required")
    class SunnyMoeCheckpointTest(unittest.TestCase):
        def test_requires_dependencies(self):
            pass
else:
    class TinyMlp(nn.Module):
        def __init__(self):
            super().__init__()
            self.gate_proj = nn.Linear(4, 8, bias=False)
            self.up_proj = nn.Linear(4, 8, bias=False)
            self.down_proj = nn.Linear(8, 4, bias=False)
            self.act_fn = nn.SiLU()

    class TinyLayer(nn.Module):
        def __init__(self):
            super().__init__()
            self.mlp = TinyMlp()

    class TinyConfig:
        class TextConfig:
            hidden_size = 4
            num_hidden_layers = 2

        text_config = TextConfig()

    class TinyModel(nn.Module):
        def __init__(self):
            super().__init__()
            self.config = TinyConfig()
            self.model = nn.Module()
            self.model.layers = nn.ModuleList(TinyLayer() for _ in range(2))

    class SunnyMoeCheckpointTest(unittest.TestCase):
        def test_compact_checkpoint_restores_only_trainable_state(self):
            spec = SunnyMoeSpec(
                seed_model="fixture", sparse_start_layer=1,
                num_experts=2, top_k=1, lora_rank=2,
            )
            model = TinyModel()
            replaced = upcycle_model(model, spec)
            original = {
                name: parameter.detach().clone()
                for name, parameter in model.named_parameters()
                if parameter.requires_grad
            }
            with tempfile.TemporaryDirectory() as directory:
                save_adapter_checkpoint(
                    model, directory, spec=spec, replaced_modules=replaced
                )
                for parameter in model.parameters():
                    if parameter.requires_grad:
                        parameter.data.add_(1)
                payload = load_adapter_checkpoint(model, directory)
                self.assertEqual(payload["format"], "sunny-moe-v1")
            restored = {
                name: parameter.detach()
                for name, parameter in model.named_parameters()
                if parameter.requires_grad
            }
            self.assertEqual(set(original), set(restored))
            for name in original:
                torch.testing.assert_close(restored[name], original[name])


if __name__ == "__main__":
    unittest.main()
