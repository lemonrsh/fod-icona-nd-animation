import contextlib
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import inspect_systemui as inspector


class ClassificationTests(unittest.TestCase):
    def test_udfps_and_fod_candidates_are_distinct_from_sfps(self):
        self.assertEqual("under_display", inspector.classify("udfps_icon.xml"))
        self.assertEqual("under_display", inspector.classify("my_fod_animation.json"))
        self.assertEqual("under_display", inspector.classify("under_display_icon.png"))
        self.assertEqual("side", inspector.classify("biometricprompt_sfps_fingerprint_authenticating.json"))
        self.assertEqual("side", inspector.classify("biometricprompt_sfps_rear_display_fingerprint_authenticating.json"))
        self.assertEqual("other", inspector.classify("ic_fingerprint.xml"))
        self.assertIsNone(inspector.classify("ic_wifi.xml"))

    def test_inventory_reports_paths_with_qualifiers_and_does_not_modify_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            res = root / "res"
            files = {
                "raw/biometricprompt_sfps_fingerprint_authenticating.json": b"{}",
                "drawable-xhdpi/ic_udfps.png": b"image",
                "drawable/ic_fingerprint.xml": b"<vector/>",
                "drawable/ic_wifi.xml": b"<vector/>",
            }
            for name, content in files.items():
                path = res / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
            grouped = inspector.inventory(res)
            self.assertEqual(1, len(grouped["side"]))
            self.assertEqual(1, len(grouped["under_display"]))
            self.assertEqual(1, len(grouped["other"]))
            self.assertEqual("drawable-xhdpi/ic_udfps.png", grouped["under_display"][0].relative_to(res).as_posix())
            for name, content in files.items():
                self.assertEqual(content, (res / name).read_bytes())

    @unittest.skipUnless(hasattr(os, "symlink"), "symlinks are not supported")
    def test_inventory_skips_symlinked_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            res = root / "res"
            res.mkdir()
            target = root / "outside.json"
            target.write_text("secret", encoding="utf-8")
            (res / "fod_external.json").symlink_to(target)
            self.assertEqual([], inspector.inventory(res)["under_display"])


class LottieChecks(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.file = Path(self.tmp.name) / "animation.json"
        self.animation = {
            "v": "5.10.2", "fr": 60, "ip": 0, "op": 301, "w": 150, "h": 150,
            "assets": [], "layers": [{"ty": 3}, {"ty": 4, "shapes": []}],
        }

    def check_text(self, text):
        self.file.write_text(text, encoding="utf-8")
        return inspector.check_lottie(self.file)

    def check_object(self, value):
        return self.check_text(json.dumps(value))

    def test_valid_complete_json_reports_metadata(self):
        valid, detail = self.check_object(self.animation)
        self.assertTrue(valid, detail)
        self.assertIn("150x150", detail)
        self.assertIn("60 fps", detail)
        self.assertIn("5.02 s", detail)
        self.assertIn("2 layer(s)", detail)

    def test_empty_layers_are_reported_as_likely_blank(self):
        self.animation["layers"] = []
        valid, detail = self.check_object(self.animation)
        self.assertTrue(valid)
        self.assertIn("no layers", detail)

    def test_rejects_inspector_preview_incomplete_and_trailing_text(self):
        self.assertFalse(self.check_text('v"5.10.2"fr60ip0op301w150h150layers0')[0])
        self.assertFalse(self.check_text('{"v":"5.10.2", "layers":[...]}')[0])
        self.assertFalse(self.check_text(json.dumps(self.animation)[:-1])[0])
        self.assertFalse(self.check_text(json.dumps(self.animation) + " trailing")[0])

    def test_rejects_missing_duplicate_or_wrong_type_metadata(self):
        del self.animation["fr"]
        self.assertIn("fr", self.check_object(self.animation)[1])
        self.assertFalse(self.check_text('{"v":"5", "fr":30, "fr":60, "ip":0, "op":1, "w":1, "h":1, "layers":[]}')[0])
        self.animation["fr"] = True
        self.assertFalse(self.check_object(self.animation)[0])
        self.animation["fr"] = 60
        self.animation["layers"] = [1]
        self.assertFalse(self.check_object(self.animation)[0])

    def test_rejects_bad_dimensions_and_invalid_time_range(self):
        self.animation["w"] = 8193
        self.assertFalse(self.check_object(self.animation)[0])
        self.animation["w"] = 150.5
        self.assertFalse(self.check_object(self.animation)[0])
        self.animation["w"] = 150
        self.animation["op"] = self.animation["ip"]
        self.assertFalse(self.check_object(self.animation)[0])
        self.animation["op"] = 301
        self.animation["fr"] = 0
        self.assertFalse(self.check_object(self.animation)[0])

    def test_rejects_nonstandard_numbers_and_bad_utf8(self):
        self.assertFalse(self.check_text(json.dumps(self.animation).replace('"fr": 60', '"fr": NaN'))[0])
        self.assertFalse(self.check_text(json.dumps(self.animation).replace('"fr": 60', '"fr": ' + ('1' * 400)))[0])
        self.file.write_bytes(b'{"v":"\xc3("}')
        self.assertFalse(inspector.check_lottie(self.file)[0])

    def test_rejects_oversized_json_without_loading_all_of_it(self):
        self.file.write_bytes(b" " * (inspector.MAX_BYTES + 1))
        valid, detail = inspector.check_lottie(self.file)
        self.assertFalse(valid)
        self.assertIn("4 MiB", detail)

    def test_direct_file_cli_has_useful_exit_status(self):
        self.file.write_text(json.dumps(self.animation), encoding="utf-8")
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(0, inspector.main([str(self.file)]))
        self.assertIn("does not prove", output.getvalue())
        self.file.write_text("not json", encoding="utf-8")
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(1, inspector.main([str(self.file)]))

    def test_directory_cli_highlights_both_sensor_groups(self):
        root = Path(self.tmp.name)
        raw = root / "res" / "raw"
        raw.mkdir(parents=True)
        (raw / "biometricprompt_sfps_fingerprint_authenticating.json").write_text(
            json.dumps(self.animation), encoding="utf-8")
        (raw / "udfps_auth.json").write_text(json.dumps(self.animation), encoding="utf-8")
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(0, inspector.main([str(root)]))
        text = output.getvalue()
        self.assertIn("Possible under-display / FOD resources: 1", text)
        self.assertIn("Side fingerprint (SFPS) resources: 1", text)
        self.assertIn("Basic Lottie check: 150x150", text)
        self.assertIn("do not prove", text)


if __name__ == "__main__":
    unittest.main()
