#!/usr/bin/env python3
"""Small deterministic checks for the KWS preprocessing contract."""
import json
import tempfile
import unittest
from pathlib import Path

import numpy as np

from kws_features import AudioFile, discover_dataset, extract_mfcc, split_by_file


class KwsFeatureTests(unittest.TestCase):
    def test_golden_contract_is_49_by_10(self):
        root = Path(__file__).resolve().parents[1] / "app/src/test/resources/kws_mfcc_golden.json"
        fixture = json.loads(root.read_text())
        expected = np.asarray(fixture["features"], dtype=np.float32)
        self.assertEqual((49, 10), expected.shape)
        n = np.arange(16_000, dtype=np.float64)
        signal = 0.25 * np.sin(2 * np.pi * 440 * n / 16_000) + 0.12 * np.sin(2 * np.pi * 1_000 * n / 16_000)
        pcm = (signal * 32768).astype(np.int16).astype(np.float32) / 32768
        # NumPy's FFT backend differs by a few ULPs across builds; Kotlin uses a
        # float32 FFT and is checked separately against this fixture at 1e-2.
        np.testing.assert_allclose(extract_mfcc(pcm), expected, rtol=0, atol=1e-5)

    def test_file_split_has_no_overlap(self):
        dataset = {
            name: [AudioFile(Path(f"{name}/{i}.wav"), class_id, name) for i in range(30)]
            for name, class_id in (("background", 0), ("unknown", 1), ("positive", 2))
        }
        splits = split_by_file(dataset, seed=2026)
        paths = [{item.path for item in splits[name]} for name in ("train", "validation", "test")]
        self.assertFalse(paths[0] & paths[1])
        self.assertFalse(paths[0] & paths[2])
        self.assertFalse(paths[1] & paths[2])
        self.assertEqual(90, sum(map(len, splits.values())))

    def test_missing_positive_data_fails_loudly(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaisesRegex(ValueError, "No positive Jarvis recordings found"):
                discover_dataset(Path(root))


if __name__ == "__main__":
    unittest.main()
