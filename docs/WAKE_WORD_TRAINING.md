# Personal wake-word dataset and offline training

Android enrollment records real positive `Jarvis` WAV files. It does not train model weights. The runtime accepts only a validated INT8 TFLite model for the phrase `Jarvis`; a missing model disables wake detection.

## Recording and export

In **CONFIG → Personal Wake Word Dataset**, record at least 50 samples (100–200 is better), varying volume and distance. The app stores mono PCM16 WAV at 16 kHz under `filesDir/wake_word_dataset/positive/` and can export those files with `manifest.json` as a ZIP.

On the development machine, unpack the positive WAVs into `data/wakeword/positive/`. Add at least 30 unique real WAV files to each of `data/wakeword/unknown/` and `data/wakeword/background/`. Unknown speech should include similar-sounding words; background should include silence and realistic environmental noise. Keep original files intact; the trainer rejects duplicates and splits by original file before it augments training examples.

## Create the training environment

Python 3.12.3 was used for the repository-side script checks. The requirements are pinned for that Python/TensorFlow combination.

```bash
python3 -m venv .venv-kws
source .venv-kws/bin/activate
python -m pip install --upgrade pip
python -m pip install -r scripts/requirements-kws.txt
```

## Train and validate

```bash
python scripts/train_kws.py \
  --data-root data/wakeword \
  --output-model app/src/main/assets/jarvis_dscnn_int8.tflite \
  --output-float app/src/main/assets/jarvis_dscnn_float32.tflite \
  --output-metadata app/src/main/assets/jarvis_model.json

python scripts/validate_kws_model.py \
  --model app/src/main/assets/jarvis_dscnn_int8.tflite \
  --data-root data/wakeword
```

Training uses an 80/10/10 deterministic file split, augments training data only, trains a compact DS-CNN, derives the score threshold from validation negatives at the requested false-accept rate, evaluates the held-out test split, and verifies both TFLite exports. It writes the model assets only after those checks pass. The manifest records the feature contract, validation threshold, dataset fingerprint, and model SHA-256.

The Android `MfccExtractor` and Python `kws_features.py` share the same 16 kHz, 16,000-sample, 49×10 MFCC feature contract. `MfccParityTest` compares Android output to a fixed Python-generated tone fixture. After installing a newly built APK, adding further recordings changes the dataset fingerprint and the UI reports the model as outdated; train and install again to update the weights.
