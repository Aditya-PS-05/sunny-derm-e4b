import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

try:
    import torch
    from sunny_moe.q4_loader import (
        dequantize_grouped_q4_torch,
        dequantize_grouped_q8_torch,
        expert_pack_name_to_model_name,
    )
    from quantize_sunny_moe_pack import quantize_int4_grouped, quantize_int8_grouped
except ImportError:
    torch = None


@unittest.skipIf(torch is None, "torch and numpy are required")
class SunnyMoeQ4LoaderTest(unittest.TestCase):
    def test_expert_name_maps_to_frozen_base_projection(self):
        source = "model.text_model.layers.12.mlp.experts.3.gate_proj.weight"
        expected = "model.text_model.layers.12.mlp.experts.3.base.gate_proj.weight"
        self.assertEqual(expert_pack_name_to_model_name(source), expected)
        self.assertEqual(expert_pack_name_to_model_name("model.embed.weight"), "model.embed.weight")

    def test_torch_dequant_matches_numpy_reference(self):
        import numpy as np
        from quantize_sunny_moe_pack import dequantize_int4_grouped

        rng = np.random.default_rng(21)
        weights = rng.normal(size=(7, 135)).astype(np.float32)
        packed, scales = quantize_int4_grouped(weights, group_size=64)
        expected = dequantize_int4_grouped(packed, scales, 135, group_size=64)
        actual = dequantize_grouped_q4_torch(
            torch.from_numpy(packed), torch.from_numpy(scales),
            output_features=7, input_features=135, group_size=64,
            dtype=torch.float32,
        )
        torch.testing.assert_close(actual, torch.from_numpy(expected))

    def test_torch_q8_dequant_matches_numpy_reference(self):
        import numpy as np
        from quantize_sunny_moe_pack import dequantize_int8_grouped

        rng = np.random.default_rng(22)
        weights = rng.normal(size=(7, 135)).astype(np.float32)
        quantized, scales = quantize_int8_grouped(weights, group_size=64)
        expected = dequantize_int8_grouped(quantized, scales, 135, group_size=64)
        actual = dequantize_grouped_q8_torch(
            torch.from_numpy(quantized), torch.from_numpy(scales),
            output_features=7, input_features=135, group_size=64,
            dtype=torch.float32,
        )
        torch.testing.assert_close(actual, torch.from_numpy(expected))


if __name__ == "__main__":
    unittest.main()
