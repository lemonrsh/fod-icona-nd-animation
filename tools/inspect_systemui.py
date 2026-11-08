#!/usr/bin/env python3
"""Read-only inventory of fingerprint resources in an apktool-decoded SystemUI."""

import argparse
import json
import math
import sys
from pathlib import Path

MAX_BYTES = 4 * 1024 * 1024
REQUIRED_FIELDS = frozenset(("v", "fr", "ip", "op", "w", "h", "layers"))
GROUPS = (
    ("under_display", "Possible under-display / FOD resources"),
    ("side", "Side fingerprint (SFPS) resources"),
    ("other", "Other fingerprint resources"),
)


class JsonObject(dict):
    """Remember duplicate keys so the basic check matches the Android import rules."""

    def __init__(self, pairs):
        super().__init__()
        self.duplicates = set()
        for key, value in pairs:
            if key in self:
                self.duplicates.add(key)
            self[key] = value


def classify(filename):
    name = filename.casefold()
    # SFPS is a side sensor even if a vendor uses "under_display" elsewhere in the name.
    if "sfps" in name or "side_fingerprint" in name or "sidefingerprint" in name:
        return "side"
    if any(term in name for term in ("udfps", "under_display", "underdisplay", "under-display", "fod")):
        return "under_display"
    if "fingerprint" in name:
        return "other"
    return None


def _reject_constant(value):
    raise ValueError("non-finite value " + value + " is not JSON")


def _number(value):
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return False
    try:
        return math.isfinite(value)
    except OverflowError:
        return False


def check_lottie(path):
    """Return (accepted, explanation); this is not a Lottie renderer or schema validator."""
    try:
        with path.open("rb") as file:
            content = file.read(MAX_BYTES + 1)
        if not content:
            return False, "file is empty"
        if len(content) > MAX_BYTES:
            return False, "file exceeds the module's 4 MiB import limit"
        text = content.decode("utf-8")
        data = json.loads(text, parse_constant=_reject_constant, object_pairs_hook=JsonObject)
    except (OSError, UnicodeError, ValueError, RecursionError) as error:
        return False, "invalid or incomplete UTF-8 JSON: " + str(error)

    if not isinstance(data, JsonObject):
        return False, "top level must be a JSON object"
    repeated = REQUIRED_FIELDS.intersection(data.duplicates)
    if repeated:
        return False, "duplicate required field(s): " + ", ".join(sorted(repeated))
    missing = REQUIRED_FIELDS.difference(data)
    if missing:
        return False, "missing Lottie field(s): " + ", ".join(sorted(missing))
    if not isinstance(data["v"], str) or not data["v"].strip():
        return False, "v must be a non-empty string"
    for field in ("fr", "ip", "op", "w", "h"):
        if not _number(data[field]):
            return False, field + " must be a finite number"
    if data["fr"] <= 0 or data["op"] <= data["ip"]:
        return False, "fr must be positive and op must be greater than ip"
    for field in ("w", "h"):
        value = data[field]
        if not 1 <= value <= 8192 or value != int(value):
            return False, field + " must be a whole number between 1 and 8192"
    layers = data["layers"]
    if not isinstance(layers, list) or any(not isinstance(layer, dict) for layer in layers):
        return False, "layers must be an array of JSON objects"
    try:
        duration = (data["op"] - data["ip"]) / data["fr"]
        seconds = f"{duration:.2f}" if math.isfinite(duration) else "too large to report"
    except OverflowError:
        seconds = "too large to report"
    details = (f'{int(data["w"])}x{int(data["h"])}, {data["fr"]:g} fps, '
               f'{seconds} s, {len(layers)} layer(s)')
    if not layers:
        details += " (no layers; may appear blank)"
    return True, details


def inventory(res_dir):
    matches = {key: [] for key, _ in GROUPS}
    for path in res_dir.rglob("*"):
        if path.is_symlink() or not path.is_file():
            continue
        group = classify(path.name)
        if group is not None:
            matches[group].append(path)
    for files in matches.values():
        files.sort(key=lambda path: str(path.relative_to(res_dir)).casefold())
    return matches


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Find possible FOD/fingerprint resources or check one complete Lottie JSON file. No files are changed.")
    parser.add_argument("path", type=Path,
                        help="apktool-decoded SystemUI directory, its res/ directory, or a .json file")
    parser.add_argument("--max-per-group", type=int, default=80,
                        help="maximum paths shown per group (default 80; use 0 for all)")
    args = parser.parse_args(argv)
    if args.max_per_group < 0:
        parser.error("--max-per-group cannot be negative")

    if args.path.is_file():
        if args.path.suffix.casefold() != ".json":
            parser.error("a file argument must be a .json file")
        valid, message = check_lottie(args.path)
        print(("Basic Lottie check passed: " if valid else "File not accepted: ") + message)
        if valid:
            print("This checks JSON and basic metadata only; it does not prove rendering compatibility.")
        return 0 if valid else 1

    res_dir = args.path if args.path.name == "res" else args.path / "res"
    if not res_dir.is_dir():
        parser.error("could not find a res/ directory at the supplied path")

    groups = inventory(res_dir)
    total = sum(len(files) for files in groups.values())
    if total == 0:
        print("No matching resource filenames found. Your ROM may use different names.")
    for key, title in GROUPS:
        files = groups[key]
        print(f"\n{title}: {len(files)}")
        visible = files if args.max_per_group == 0 else files[:args.max_per_group]
        for path in visible:
            print("  res/" + path.relative_to(res_dir).as_posix())
            if path.suffix.casefold() == ".json":
                valid, message = check_lottie(path)
                print("    " + ("Basic Lottie check: " if valid else "Not accepted by basic check: ") + message)
        if len(files) > len(visible):
            print(f"  ... {len(files) - len(visible)} more; use --max-per-group 0 to show all")
    print("\nNames and basic JSON checks do not prove which resource SystemUI uses or whether an animation renders.")
    print("The SFPS names usually describe a side sensor, not an under-display sensor.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
