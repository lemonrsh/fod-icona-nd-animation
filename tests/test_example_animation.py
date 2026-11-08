import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import inspect_systemui as inspector


class ExampleAnimationTests(unittest.TestCase):
    def test_generated_example_is_a_self_contained_animated_lottie(self):
        path = ROOT / "examples" / "iron_man_fire_test.json"
        accepted, details = inspector.check_lottie(path)
        self.assertTrue(accepted, details)
        data = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual([], data["assets"])
        self.assertGreater(len(data["layers"]), 0)
        self.assertTrue(any(layer.get("ks", {}).get("s", {}).get("a") == 1
                            for layer in data["layers"]))
        self.assertGreater(data["op"] - data["ip"], 0)


if __name__ == "__main__":
    unittest.main()
