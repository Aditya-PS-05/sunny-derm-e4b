import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

try:
    import numpy as np
    from quantize_sunny_moe_pack import (
        dequantize_int4_grouped,
        dequantize_int8_grouped,
        is_perception_tensor,
        is_sensitive_q8_tensor,
        quantize_file,
        quantize_int4_grouped,
        quantize_int8_grouped,
    )
except ImportError:
    np = None


@unittest.skipIf(np is None, "numpy is not installed in the local control environment")
class SunnyMoeQuantizationTest(unittest.TestCase):
    def test_grouped_q4_round_trip_and_shape(self):
        rng = np.random.default_rng(4)
        weights = rng.normal(0, 0.25, size=(17, 259)).astype(np.float32)
        packed, scales = quantize_int4_grouped(weights, group_size=128)
        restored = dequantize_int4_grouped(packed, scales, 259, group_size=128)
        self.assertEqual(packed.shape, (17, 130))
        self.assertEqual(scales.shape, (17 * 3,))
        self.assertEqual(restored.shape, weights.shape)
        relative = np.abs(restored - weights).mean() / np.abs(weights).mean()
        self.assertLess(relative, 0.14)

    def test_zero_matrix_stays_zero(self):
        weights = np.zeros((3, 7), dtype=np.float32)
        packed, scales = quantize_int4_grouped(weights, group_size=4)
        restored = dequantize_int4_grouped(packed, scales, 7, group_size=4)
        np.testing.assert_array_equal(restored, weights)

    def test_grouped_q8_round_trip(self):
        rng = np.random.default_rng(14)
        weights = rng.normal(0, 0.25, size=(17, 259)).astype(np.float32)
        quantized, scales = quantize_int8_grouped(weights, group_size=128)
        restored = dequantize_int8_grouped(quantized, scales, 259, group_size=128)
        self.assertEqual(quantized.dtype, np.int8)
        self.assertEqual(restored.shape, weights.shape)
        relative = np.abs(restored - weights).mean() / np.abs(weights).mean()
        self.assertLess(relative, 0.01)

    def test_sensitive_tensor_policy(self):
        self.assertTrue(is_sensitive_q8_tensor("model.vision_model.layer.weight"))
        self.assertTrue(is_sensitive_q8_tensor("model.layers.0.mlp.down_proj.weight"))
        self.assertTrue(is_sensitive_q8_tensor("lm_head.weight"))
        self.assertFalse(is_sensitive_q8_tensor("model.layers.0.self_attn.q_proj.weight"))
        self.assertFalse(is_sensitive_q8_tensor("model.layers.0.mlp.up_proj.weight"))
        self.assertTrue(is_perception_tensor("model.vision_model.layer.weight"))
        self.assertTrue(is_perception_tensor("model.connector.proj.weight"))
        self.assertFalse(is_perception_tensor("model.text_model.embed_tokens.weight"))

    def test_safetensors_file_uses_colibri_weight_and_scale_names(self):
        from safetensors import safe_open
        from safetensors.numpy import save_file

        rng = np.random.default_rng(8)
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.safetensors"
            target = Path(directory) / "target.safetensors"
            save_file({
                "layer.weight": rng.normal(size=(16, 256)).astype(np.float16),
                "layer.norm": np.ones(16, dtype=np.float16),
            }, source)
            metadata, metrics = quantize_file(source, target, group_size=128)
            with safe_open(target, framework="np") as handle:
                self.assertEqual(
                    set(handle.keys()),
                    {"layer.weight", "layer.weight.qs", "layer.norm"},
                )
                self.assertEqual(handle.get_tensor("layer.weight").dtype, np.uint8)
            self.assertEqual(metadata["layer.weight"]["storage"], "q4_grouped")
            self.assertGreater(metrics["quantized_elements"], 0)

    def test_sensitive_safetensor_uses_q8(self):
        from safetensors import safe_open
        from safetensors.numpy import save_file

        rng = np.random.default_rng(18)
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.safetensors"
            target = Path(directory) / "target.safetensors"
            save_file({
                "model.vision_model.proj.weight": rng.normal(size=(16, 256)).astype(np.float16),
                "model.mlp.weight": rng.normal(size=(16, 256)).astype(np.float16),
            }, source)
            metadata, metrics = quantize_file(
                source, target, group_size=128, q8_sensitive=True
            )
            with safe_open(target, framework="np") as handle:
                self.assertEqual(
                    handle.get_tensor("model.vision_model.proj.weight").dtype,
                    np.int8,
                )
                self.assertEqual(handle.get_tensor("model.mlp.weight").dtype, np.uint8)
            self.assertEqual(
                metadata["model.vision_model.proj.weight"]["storage"], "q8_grouped"
            )
            self.assertGreater(metrics["q8_elements"], 0)
            self.assertGreater(metrics["q4_elements"], 0)

    def test_perception_safetensor_can_stay_fp16(self):
        from safetensors import safe_open
        from safetensors.numpy import save_file

        rng = np.random.default_rng(19)
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.safetensors"
            target = Path(directory) / "target.safetensors"
            save_file({
                "model.vision_model.proj.weight": rng.normal(size=(16, 256)).astype(np.float16),
                "model.mlp.weight": rng.normal(size=(16, 256)).astype(np.float16),
            }, source)
            metadata, metrics = quantize_file(
                source, target, group_size=128, fp16_perception=True
            )
            with safe_open(target, framework="np") as handle:
                self.assertEqual(
                    handle.get_tensor("model.vision_model.proj.weight").dtype,
                    np.float16,
                )
                self.assertEqual(handle.get_tensor("model.mlp.weight").dtype, np.uint8)
            self.assertEqual(
                metadata["model.vision_model.proj.weight"]["storage"], "float16"
            )
            self.assertGreater(metrics["fp16_elements"], 0)


if __name__ == "__main__":
    unittest.main()
