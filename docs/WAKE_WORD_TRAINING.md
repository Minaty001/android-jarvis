# Wake word models

The Android runtime uses openWakeWord's shared TFLite melspectrogram and speech embedding models, followed by one or two classifier heads: the locally trained **Jarvis** head and upstream **Hey Jarvis**. Audio runs at 16 kHz in 80 ms frames. The shared models produce 32-bin mel features and 96-value embeddings; each classifier consumes `[1,16,96]` and returns one score. Packaged phrases enter the same `WakeWordEngine` activation flow. Until the custom Jarvis head is trained and validated, only **Hey Jarvis** is available.

## Training data

Export the personal dataset from **CONFIG → Personal Wake Word Dataset**. Keep whole WAV files separate for training and evaluation: reserve at least 10 real Jarvis clips as held-out positives. Use openWakeWord's synthetic speech generation to increase the training positives, with adversarial phrase negatives, room responses, and background noise. Include a separate negative validation set with similar-sounding speech and ordinary background audio; do not use clips from that set for training. Keep the original recordings and generated examples in separate directories so the held-out set stays untouched.

## Train the Jarvis head

Use the upstream [openWakeWord training workflow](https://github.com/dscripka/openWakeWord#training-new-models) and its `custom_model.yml` configuration. Set `target_phrase` to `jarvis`, provide Piper sample generation, room responses, background clips, the ACAV100M negative feature set, and a false-positive validation feature set. Copy the 41 training recordings to `positive_train` and reserve 10 untouched real recordings in `positive_test` before synthetic data generation. Keep these real clips distinct from generated speech.

Run each upstream stage with the same configuration:

```bash
python openWakeWord/openwakeword/train.py --training_config jarvis.yml --generate_clips
python openWakeWord/openwakeword/train.py --training_config jarvis.yml --augment_clips
python openWakeWord/openwakeword/train.py --training_config jarvis.yml --train_model --convert_to_tflite
```

The configuration must point to the Piper voice, room impulse responses, background audio, ACAV100M features, and false-positive validation features on your machine. The first stage makes synthetic positives and adversarial negatives; the second creates augmented embeddings; the final stage trains and exports the classifier. Keep the config, data licenses, and validation results with the output.

Select the Jarvis threshold using held-out positives and negative/background audio. Record recall, missed detections, false accepts, and the chosen threshold. Do not package a model that has not passed the held-out checks. Rename the exported head to `jarvis_openwakeword.tflite` for the Android manifest.

## Install assets

After calibrating both heads, install the assets and create the hash manifest with:

```bash
python scripts/package_openwakeword.py \
  --jarvis-model /path/to/jarvis.tflite \
  --jarvis-threshold 0.50
```

The script copies the shared TFLite feature models from the installed openWakeWord package and writes classifier entries to `app/src/main/assets/openwakeword_models.json`. Set `--jarvis-threshold` to the measured value from validation; the Hey Jarvis threshold defaults to 0.50 and can be overridden with `--hey-jarvis-threshold`. To package and test only the included Hey Jarvis head, omit both Jarvis options. The Android loader checks hashes and tensor contracts before enabling each packaged phrase. Keep the `.tflite` assets uncompressed in the APK.

The Hey Jarvis pretrained weights are licensed **CC BY-NC-SA 4.0**; they are included only for personal, non-commercial use. Preserve attribution and the license with any redistribution. The shared openWakeWord frontend models use the project's Apache 2.0 license. See [third-party model notices](../THIRD_PARTY_MODELS.md).
