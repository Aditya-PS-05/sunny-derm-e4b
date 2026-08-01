import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

try:
    import torch
    from torch import nn
    from sunny_moe.layers import SunnyLoRALinear, SunnySparseMLP
except ImportError:
    torch = None


if torch is None:
    @unittest.skip("torch is not installed in the local control environment")
    class SunnySparseMlpTest(unittest.TestCase):
        def test_requires_torch(self):
            pass
else:
    class SunnySparseMlpTest(unittest.TestCase):
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

        def test_upcycling_is_function_preserving_at_initialization(self):
            torch.manual_seed(7)
            dense = self.TinyMlp()
            inputs = torch.randn(2, 5, 8)
            expected = dense(inputs)
            sparse = SunnySparseMLP(
                dense, hidden_size=8, num_experts=4, top_k=1,
                lora_rank=2, routing_scope="sequence",
            )
            actual = sparse(inputs)
            torch.testing.assert_close(actual, expected, rtol=1e-5, atol=1e-6)

        def test_sequence_route_cache_stays_fixed_during_decode(self):
            sparse = SunnySparseMLP(
                self.TinyMlp(), hidden_size=8, num_experts=4, top_k=1,
                lora_rank=2, routing_scope="sequence",
            )
            sparse.set_route_cache(True)
            sparse(torch.randn(1, 4, 8))
            first = sparse._cached_indices.clone()
            sparse(torch.randn(1, 1, 8) * 100)
            torch.testing.assert_close(sparse._cached_indices, first)

        def test_router_and_adapters_are_the_only_trainable_parameters(self):
            sparse = SunnySparseMLP(
                self.TinyMlp(), hidden_size=8, num_experts=3, top_k=1,
                lora_rank=2,
            )
            trainable = [
                name for name, param in sparse.named_parameters() if param.requires_grad
            ]
            self.assertTrue(any(name.startswith("router.") for name in trainable))
            self.assertTrue(any("_delta." in name for name in trainable))
            self.assertFalse(any(".base." in name for name in trainable))

        def test_top_one_straight_through_gate_trains_router(self):
            sparse = SunnySparseMLP(
                self.TinyMlp(), hidden_size=8, num_experts=4, top_k=1,
                lora_rank=2, routing_scope="sequence",
            )
            loss = sparse(torch.randn(3, 5, 8)).square().mean()
            loss.backward()
            self.assertIsNotNone(sparse.router.weight.grad)
            self.assertGreater(float(sparse.router.weight.grad.abs().sum()), 0.0)

        def test_prompt_mask_excludes_assistant_tokens_from_sequence_route(self):
            sparse = SunnySparseMLP(
                self.TinyMlp(), hidden_size=8, num_experts=2, top_k=1,
                lora_rank=2, routing_scope="sequence",
            )
            with torch.no_grad():
                sparse.router.weight.zero_()
                sparse.router.weight[0, 0] = 1
                sparse.router.weight[1, 0] = -1
            hidden = torch.zeros(1, 3, 8)
            hidden[0, 0, 0] = 4       # prompt/image representation -> expert 0
            hidden[0, 1:, 0] = -10    # assistant target would dominate -> expert 1
            sparse.set_routing_mask(torch.tensor([[True, False, False]]))
            sparse.set_route_cache(True)
            sparse(hidden)
            self.assertEqual(int(sparse._cached_indices[0, 0]), 0)

        def test_shared_lora_linear_is_initially_exact_and_trainable(self):
            base = nn.Linear(8, 5)
            inputs = torch.randn(2, 8)
            expected = base(inputs)
            wrapped = SunnyLoRALinear(base, rank=2, alpha=2)
            actual = wrapped(inputs)
            torch.testing.assert_close(actual, expected)
            actual.square().mean().backward()
            self.assertIsNotNone(wrapped.delta.b.weight.grad)
            self.assertFalse(wrapped.base.weight.requires_grad)


if __name__ == "__main__":
    unittest.main()
