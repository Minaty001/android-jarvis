#!/usr/bin/env python3
"""Mirror of OpenWakeWordDetector.initialize() asset validation.

Android refuses to load a wake-word head unless its manifest sha256 matches the
shipped asset, the phrase is supported, the threshold is in (0,1], and the
feature models hash correctly. This runs those same checks locally so a broken
manifest is caught before an APK build.

    python3 verify_manifest.py
"""
import hashlib
import json
import sys
from pathlib import Path

ASSETS = Path(__file__).parent / "app" / "src" / "main" / "assets"
FEATURE_MODELS = {"melspectrogram": "melspectrogram.tflite", "embedding": "embedding_model.tflite"}
ALLOWED_PHRASES = {"Jarvis", "Hey Jarvis"}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    manifest = json.loads((ASSETS / "openwakeword_models.json").read_text())
    errors = []

    for name, expected in FEATURE_MODELS.items():
        item = manifest["featureModels"][name]
        if item["asset"] != expected:
            errors.append(f"featureModels.{name}: expected {expected}, got {item['asset']}")
        if sha256(ASSETS / expected) != item["sha256"].lower():
            errors.append(f"{expected}: sha256 mismatch")

    models = manifest["models"]
    if not 1 <= len(models) <= 2:
        errors.append(f"expected 1..2 heads, got {len(models)}")
    phrases = [m["phrase"] for m in models]
    if len(set(phrases)) != len(phrases):
        errors.append(f"duplicate phrases: {phrases}")
    for m in models:
        if m["phrase"] not in ALLOWED_PHRASES:
            errors.append(f"unsupported phrase: {m['phrase']}")
        asset = ASSETS / m["asset"]
        if not asset.exists():
            errors.append(f"{m['asset']}: missing asset")
            continue
        if not 0 < float(m["threshold"]) <= 1:
            errors.append(f"{m['asset']}: threshold {m['threshold']} outside (0,1]")
        if sha256(asset) != m["sha256"].lower():
            errors.append(f"{m['asset']}: sha256 mismatch")
        print(f"  head {m['phrase']!r:12s} asset={m['asset']:24s} thr={m['threshold']} sha=ok")

    if errors:
        print("\nFAIL:")
        for e in errors:
            print("  -", e)
        return 1
    print("\nCHECK PASS: manifest + assets satisfy OpenWakeWordDetector validation")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
