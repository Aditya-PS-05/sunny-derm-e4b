import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

try:
    import torch
    from torch import nn
    from sunny_moe.layers import SunnyLoRALinear, SunnySparseMLP
    from sunny_moe.surgery import SunnyMoeSpec, parameter_summary, upcycle_model
except ImportError:
    torch = None


if torch is None:
    @unittest.skip("torch is not installed in the local control environment")
    class SunnyMoeSurgeryTest(unittest.TestCase):
        def test_requires_torch(self):
            pass
else:
    class TinyMlp(nn.Module):
        def __init__(self):
            super().__init__()
            self.gate_proj = nn.Linear(8, 16, bias=False)
            self.up_proj = nn.Linear(8, 16, bias=False)
            self.down_proj = nn.Linear(16, 8, bias=False)
            self.act_fn = nn.SiLU()

        def forward(self, inputs):
            return self.down_proj(
                self.act_fn(self.gate_proj(inputs)) * self.up_proj(inputs)
            )

    class TinyLayer(nn.Module):
        def __init__(self):
            super().__init__()
            self.mlp = TinyMlp()

        def forward(self, inputs):
            return inputs + self.mlp(inputs)

    class TinyConfig:
        class TextConfig:
            hidden_size = 8
            num_hidden_layers = 4

        text_config = TextConfig()

    class TinyModel(nn.Module):
        def __init__(self):
            super().__init__()
            self.config = TinyConfig()
            self.model = nn.Module()
            self.model.layers = nn.ModuleList(TinyLayer() for _ in range(4))

        def forward(self, inputs):
            for layer in self.model.layers:
                inputs = layer(inputs)
            return inputs

    class SunnyMoeSurgeryTest(unittest.TestCase):
        def test_replaces_only_selected_layers_and_preserves_output(self):
            torch.manual_seed(9)
            model = TinyModel()
            inputs = torch.randn(2, 3, 8)
            expected = model(inputs)
            replaced = upcycle_model(
                model,
                SunnyMoeSpec(
                    seed_model="fixture",
                    sparse_start_layer=2,
                    num_experts=4,
                    top_k=1,
                    lora_rank=2,
                ),
            )
            self.assertEqual(
                replaced, ["model.layers.2.mlp", "model.layers.3.mlp"]
            )
            self.assertIsInstance(model.model.layers[2].mlp, SunnySparseMLP)
            self.assertNotIsInstance(model.model.layers[1].mlp, SunnySparseMLP)
            torch.testing.assert_close(model(inputs), expected, rtol=1e-5, atol=1e-6)
            summary = parameter_summary(model)
            self.assertGreater(summary["total_parameters"], 0)
            self.assertGreater(summary["trainable_parameters"], 0)
            self.assertLess(summary["trainable_parameters"], summary["total_parameters"])

        def test_shared_lora_wraps_only_dense_half_mlp_projections(self):
            torch.manual_seed(11)
            model = TinyModel()
            inputs = torch.randn(1, 2, 8)
            expected = model(inputs)
            upcycle_model(
                model,
                SunnyMoeSpec(
                    seed_model="fixture", sparse_start_layer=2,
                    num_experts=2, top_k=1, lora_rank=2,
                    shared_lora_rank=2, shared_lora_alpha=2,
                ),
            )
            self.assertIsInstance(
                model.model.layers[0].mlp.gate_proj, SunnyLoRALinear
            )
            self.assertIsInstance(
                model.model.layers[2].mlp, SunnySparseMLP
            )
            self.assertNotIsInstance(
                model.model.layers[2].mlp.experts[0].base.gate_proj,
                SunnyLoRALinear,
            )
            torch.testing.assert_close(model(inputs), expected, rtol=1e-5, atol=1e-6)


if __name__ == "__main__":
    unittest.main()
