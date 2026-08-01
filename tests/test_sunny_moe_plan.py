import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from sunny_moe.plan import build_plan, build_plan_from_hf_config  # noqa: E402


class SunnyMoePlanTest(unittest.TestCase):
    def test_production_candidate_stays_under_three_gb(self):
        plan = build_plan(
            seed_model="SmolVLM2-2.2B",
            seed_parameters=2_246_784_880,
            hidden_size=2048,
            intermediate_size=8192,
            num_layers=24,
            sparse_start_layer=12,
            num_experts=4,
            top_k=1,
            deployment_bpw=5.5,
        )
        self.assertEqual(plan.expert_parameters_per_layer, 50_331_648)
        self.assertEqual(plan.added_parameters, 1_811_939_328)
        self.assertEqual(plan.total_parameters, 4_058_724_208)
        self.assertEqual(plan.active_parameters, 2_246_784_880)
        self.assertLess(plan.estimated_download_bytes, 3_000_000_000)
        self.assertGreater(plan.cold_expert_bytes_per_token, 400_000_000)

    def test_top_two_increases_active_not_total_parameters(self):
        common = dict(
            seed_model="tiny",
            seed_parameters=500_000_000,
            hidden_size=960,
            intermediate_size=2560,
            num_layers=32,
            sparse_start_layer=16,
            num_experts=4,
        )
        top1 = build_plan(**common, top_k=1)
        top2 = build_plan(**common, top_k=2)
        self.assertEqual(top1.total_parameters, top2.total_parameters)
        self.assertGreater(top2.active_parameters, top1.active_parameters)

    def test_half_layers_is_default(self):
        plan = build_plan_from_hf_config(
            {"text_config": {
                "hidden_size": 64,
                "intermediate_size": 128,
                "num_hidden_layers": 10,
            }},
            seed_model="fixture",
            seed_parameters=1_000_000,
        )
        self.assertEqual(plan.sparse_start_layer, 5)
        self.assertEqual(plan.sparse_layers, 5)

    def test_rejects_invalid_top_k(self):
        with self.assertRaises(ValueError):
            build_plan(
                seed_model="bad", seed_parameters=1, hidden_size=1,
                intermediate_size=1, num_layers=1, sparse_start_layer=0,
                num_experts=2, top_k=3,
            )


if __name__ == "__main__":
    unittest.main()
