# Local wake-word training data

Place original WAV recordings in `positive/`, `unknown/`, and `background/`.
Personal recordings are ignored by Git. Do not duplicate samples to satisfy the
minimum count; the trainer requires at least 30 original WAVs per class.

The Android app exports only `positive/*.wav` and its manifest. Add genuine
unknown-speech and background recordings on the development machine before
running `scripts/train_kws.py`.
