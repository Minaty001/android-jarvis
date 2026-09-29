#!/usr/bin/env python3
"""Train and validate an INT8 DS-CNN from real WAV files only."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-root", type=Path, default=Path("data/wakeword"))
    parser.add_argument("--output-model", type=Path, default=Path("app/src/main/assets/jarvis_dscnn_int8.tflite"))
    parser.add_argument("--output-float", type=Path, default=Path("app/src/main/assets/jarvis_dscnn_float32.tflite"))
    parser.add_argument("--output-metadata", type=Path, default=Path("app/src/main/assets/jarvis_model.json"))
    parser.add_argument("--target-fpr", type=float, default=0.01, help="Maximum validation false-accept rate (default 1%%).")
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--augmentations-per-sample", type=int, default=5)
    return parser


def require_tensorflow():
    try:
        import tensorflow as tf
    except ImportError as error:
        raise RuntimeError(
            "TensorFlow is not installed. Create/activate the training virtual environment and install scripts/requirements-kws.txt."
        ) from error
    return tf


def build_model(tf):
    layers = tf.keras.layers
    inputs = tf.keras.Input(shape=(49, 10, 1), name="input")
    x = layers.Conv2D(16, 3, padding="same", use_bias=False)(inputs)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)
    x = layers.DepthwiseConv2D(3, padding="same", use_bias=False)(x)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)
    x = layers.Conv2D(32, 1, padding="same", use_bias=False)(x)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)
    x = layers.DepthwiseConv2D(3, padding="same", use_bias=False)(x)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)
    x = layers.Conv2D(64, 1, padding="same", use_bias=False)(x)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dense(32, activation="relu")(x)
    outputs = layers.Dense(3, activation="softmax", name="output")(x)
    model = tf.keras.Model(inputs, outputs)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model


def prepare_features(files, augmentations, seed, background_audio):
    import numpy as np
    from kws_features import augment_audio, extract_mfcc, load_audio, window_audio

    rng = np.random.default_rng(seed)
    features, labels = [], []
    for audio_file in files:
        audio = window_audio(load_audio(audio_file.path))
        features.append(extract_mfcc(audio)[..., np.newaxis])
        labels.append(audio_file.class_id)
        if audio_file.directory_class != "background" and augmentations > 0:
            for _ in range(augmentations):
                changed = augment_audio(audio, rng, background_audio)
                features.append(extract_mfcc(changed)[..., np.newaxis])
                labels.append(audio_file.class_id)
        elif audio_file.directory_class == "background" and augmentations > 0:
            for _ in range(max(1, augmentations // 2)):
                changed = augment_audio(audio, rng)
                features.append(extract_mfcc(changed)[..., np.newaxis])
                labels.append(audio_file.class_id)
    return np.stack(features).astype(np.float32), np.asarray(labels, dtype=np.int64)


def int8_predictions(tf, model_bytes, feature_batch):
    import numpy as np

    interpreter = tf.lite.Interpreter(model_content=model_bytes, num_threads=1)
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]
    input_scale, input_zero = input_info["quantization"]
    output_scale, output_zero = output_info["quantization"]
    if input_scale <= 0 or output_scale <= 0:
        raise ValueError("INT8 model tensor quantization is missing")
    scores = []
    for feature in feature_batch:
        quantized = np.clip(np.rint(feature / input_scale + input_zero), -128, 127).astype(np.int8)
        interpreter.set_tensor(input_info["index"], quantized[np.newaxis, ...])
        interpreter.invoke()
        output = interpreter.get_tensor(output_info["index"])[0].astype(np.float32)
        output = (output - output_zero) * output_scale
        if not np.isfinite(output).all():
            raise ValueError("INT8 model produced non-finite output")
        scores.append(output)
    return np.stack(scores)


def choose_threshold(scores, labels, target_fpr):
    import numpy as np

    positive = labels == 2
    negative = ~positive
    if not positive.any() or not negative.any():
        raise ValueError("Validation split must contain positive and negative examples")
    for threshold in np.linspace(0.05, 0.99, 189):
        accepted = scores >= threshold
        fpr = float(np.count_nonzero(accepted & negative) / np.count_nonzero(negative))
        if fpr <= target_fpr:
            recall = float(np.count_nonzero(accepted & positive) / np.count_nonzero(positive))
            if recall <= 0.0:
                break
            return float(threshold), recall, fpr
    raise ValueError("No threshold satisfies target false-positive rate. Collect more negative/background data or improve the model.")


def metrics(scores, labels, threshold):
    import numpy as np

    positive = labels == 2
    negative = ~positive
    accepted = scores >= threshold
    recall = float(np.count_nonzero(accepted & positive) / np.count_nonzero(positive)) if positive.any() else 0.0
    fpr = float(np.count_nonzero(accepted & negative) / np.count_nonzero(negative)) if negative.any() else 0.0
    return recall, fpr


def sha256_file(path: Path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def next_model_version(metadata_path: Path) -> int:
    if not metadata_path.exists():
        return 1
    try:
        previous = int(json.loads(metadata_path.read_text()).get("version", 0))
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        previous = 0
    return max(1, previous + 1)


def verify_model_bytes(model_bytes: bytes, tf, expected_dtype):
    import numpy as np

    interpreter = tf.lite.Interpreter(model_content=model_bytes, num_threads=1)
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    if input_detail["shape"].tolist() != [1, 49, 10, 1]:
        raise ValueError(f"Exported TFLite input shape is invalid: {input_detail['shape'].tolist()}")
    if output_detail["shape"].tolist() != [1, 3]:
        raise ValueError(f"Exported TFLite output shape is invalid: {output_detail['shape'].tolist()}")
    if input_detail["dtype"] != expected_dtype or output_detail["dtype"] != expected_dtype:
        raise ValueError(f"Exported TFLite dtype is invalid: {input_detail['dtype']}, {output_detail['dtype']}")
    if expected_dtype == np.int8:
        if input_detail["quantization"][0] <= 0 or output_detail["quantization"][0] <= 0:
            raise ValueError("Exported INT8 tensors have invalid quantization")
        sample = np.zeros((1, 49, 10, 1), dtype=np.int8)
    else:
        sample = np.zeros((1, 49, 10, 1), dtype=np.float32)
    interpreter.set_tensor(input_detail["index"], sample)
    interpreter.invoke()
    output = interpreter.get_tensor(output_detail["index"])
    if output.shape != (1, 3) or not np.isfinite(output).all():
        raise ValueError(f"Exported TFLite inference output is invalid: {output.shape}")
    return input_detail, output_detail


def atomic_write(path: Path, contents: bytes):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(contents)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def main(argv=None):
    args = build_parser().parse_args(argv)
    outputs = [args.output_model.resolve(), args.output_float.resolve(), args.output_metadata.resolve()]
    if len(set(outputs)) != len(outputs):
        raise SystemExit("ERROR: Model, float model, and metadata must use three different output files.")
    if args.output_model.name != "jarvis_dscnn_int8.tflite" or args.output_metadata.name != "jarvis_model.json":
        raise SystemExit("ERROR: Android runtime expects output filenames jarvis_dscnn_int8.tflite and jarvis_model.json.")
    if not 0.0 < args.target_fpr < 1.0:
        raise SystemExit("ERROR: --target-fpr must be between 0 and 1.")
    if args.epochs < 1 or args.batch_size < 1 or args.augmentations_per_sample < 0:
        raise SystemExit("ERROR: epochs/batch size must be positive and augmentations must not be negative.")

    try:
        from kws_features import (
            LABELS,
            discover_dataset,
            feature_contract,
            load_audio,
            positive_dataset_sha256,
            split_by_file,
        )

        dataset = discover_dataset(args.data_root)
        data_manifest_path = args.data_root / "manifest.json"
        dataset_version = 1
        if data_manifest_path.exists():
            try:
                data_manifest = json.loads(data_manifest_path.read_text())
            except (OSError, json.JSONDecodeError) as error:
                raise ValueError(f"Invalid dataset manifest: {data_manifest_path}: {error}") from error
            expected_manifest = {"phrase": "Jarvis", "sampleRate": 16000, "channels": 1, "encoding": "PCM_16BIT"}
            for key, expected in expected_manifest.items():
                if data_manifest.get(key) != expected:
                    raise ValueError(f"Dataset manifest {key}={data_manifest.get(key)!r}; expected {expected!r}")
            positive_files = [item.path for item in dataset["positive"]]
            try:
                manifest_count = int(data_manifest.get("sampleCount", -1))
                dataset_version = int(data_manifest.get("datasetVersion", 1))
            except (TypeError, ValueError) as error:
                raise ValueError(f"Invalid sampleCount or datasetVersion in {data_manifest_path}") from error
            if manifest_count != len(positive_files):
                raise ValueError("Dataset manifest sampleCount does not match positive WAV files")
            expected_hash = positive_dataset_sha256(positive_files)
            if data_manifest.get("positiveDatasetSha256") != expected_hash:
                raise ValueError("Dataset manifest positiveDatasetSha256 does not match positive WAV files")
        splits = split_by_file(dataset, args.seed)
        tf = require_tensorflow()
        print("Dataset summary\n---------------")
        for name in ("positive", "unknown", "background"):
            print(f"{name}: {len(dataset[name])}")
        for name in ("train", "validation", "test"):
            print(f"{name.title()}: {len(splits[name])} original files")

        background_audio = [load_audio(item.path) for item in splits["train"] if item.directory_class == "background"]
        x_train, y_train = prepare_features(splits["train"], args.augmentations_per_sample, args.seed, background_audio)
        x_validation, y_validation = prepare_features(splits["validation"], 0, args.seed + 1, [])
        x_test, y_test = prepare_features(splits["test"], 0, args.seed + 2, [])

        tf.keras.utils.set_random_seed(args.seed)
        try:
            tf.config.experimental.enable_op_determinism()
        except Exception:
            pass
        model = build_model(tf)
        class_counts = {i: int((y_train == i).sum()) for i in range(3)}
        total = sum(class_counts.values())
        class_weights = {i: total / (3.0 * count) for i, count in class_counts.items() if count > 0}

        with tempfile.TemporaryDirectory(prefix="jarvis-kws-") as temp_dir:
            checkpoint = Path(temp_dir) / "best.keras"
            callbacks = [
                tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=10, restore_best_weights=True),
                tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=4, min_lr=1e-5),
                tf.keras.callbacks.ModelCheckpoint(checkpoint, monitor="val_loss", save_best_only=True),
            ]
            model.fit(
                x_train,
                y_train,
                validation_data=(x_validation, y_validation),
                epochs=args.epochs,
                batch_size=args.batch_size,
                class_weight=class_weights,
                callbacks=callbacks,
                verbose=2,
            )

        float_converter = tf.lite.TFLiteConverter.from_keras_model(model)
        float_model = float_converter.convert()
        import numpy as np
        verify_model_bytes(float_model, tf, np.float32)

        representative_features = x_train[: min(len(x_train), 128)]
        def representative_dataset():
            for features in representative_features:
                yield [features[np.newaxis, ...].astype(np.float32)]

        int8_converter = tf.lite.TFLiteConverter.from_keras_model(model)
        int8_converter.optimizations = [tf.lite.Optimize.DEFAULT]
        int8_converter.representative_dataset = representative_dataset
        int8_converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        int8_converter.inference_input_type = np.int8
        int8_converter.inference_output_type = np.int8
        int8_model = int8_converter.convert()
        int8_input, int8_output = verify_model_bytes(int8_model, tf, np.int8)

        val_probabilities = int8_predictions(tf, int8_model, x_validation)
        threshold, validation_recall, validation_fpr = choose_threshold(val_probabilities[:, 2], y_validation, args.target_fpr)
        test_probabilities = int8_predictions(tf, int8_model, x_test)
        test_recall, test_fpr = metrics(test_probabilities[:, 2], y_test, threshold)
        if test_fpr > args.target_fpr:
            raise ValueError(
                f"Held-out test false-accept rate {test_fpr:.3f} exceeds target {args.target_fpr:.3f}. Collect more diverse negative data or improve the model."
            )
        if test_recall <= 0.0:
            raise ValueError("Held-out test has zero positive recall at the validation-calibrated threshold.")

        sha256 = hashlib.sha256(int8_model).hexdigest()
        positive_files = [item.path for item in dataset["positive"]]
        metadata = {
            "version": next_model_version(args.output_metadata),
            "phrase": "Jarvis",
            "sampleRate": 16000,
            "windowSamples": 16000,
            "frameLengthSamples": 400,
            "frameStrideSamples": 320,
            "fftSize": 512,
            "numMelFilters": 40,
            "numMfcc": 10,
            "numFrames": 49,
            "labels": list(LABELS),
            "positiveClassIndex": 2,
            "threshold": threshold,
            "targetFalsePositiveRate": args.target_fpr,
            "splitSeed": args.seed,
            "validationPositiveRecall": validation_recall,
            "validationFalseAcceptRate": validation_fpr,
            "testPositiveRecall": test_recall,
            "testFalseAcceptRate": test_fpr,
            "modelFile": args.output_model.name,
            "modelSha256": sha256,
            "positiveDatasetSha256": positive_dataset_sha256(positive_files),
            "datasetVersion": dataset_version,
            "trainedSampleCount": len(positive_files),
            "trainedAt": datetime.now(timezone.utc).isoformat(),
            "inputShape": int8_input["shape"].tolist(),
            "outputShape": int8_output["shape"].tolist(),
            "inputType": "INT8",
            "outputType": "INT8",
            "inputScale": float(int8_input["quantization"][0]),
            "inputZeroPoint": int(int8_input["quantization"][1]),
            "outputScale": float(int8_output["quantization"][0]),
            "outputZeroPoint": int(int8_output["quantization"][1]),
            "featureSpec": feature_contract(),
        }
        # Publish only after threshold calibration and held-out evaluation pass.
        atomic_write(args.output_float, float_model)
        atomic_write(args.output_model, int8_model)
        atomic_write(args.output_metadata, (json.dumps(metadata, indent=2) + "\n").encode("utf-8"))

        print("\nModel:\ninput shape: [1, 49, 10, 1]\noutput shape: [1, 3]")
        print(f"Validation:\npositive recall: {validation_recall:.3f}\nnegative false accept rate: {validation_fpr:.3f}")
        print(f"Chosen threshold: {threshold:.6f}")
        print(f"Test:\npositive recall: {test_recall:.3f}\nfalse accept rate: {test_fpr:.3f}")
        print(f"TFLite:\nINT8 input: {int8_input['dtype']} scale={int8_input['quantization'][0]} zero={int8_input['quantization'][1]}\nINT8 output: {int8_output['dtype']} scale={int8_output['quantization'][0]} zero={int8_output['quantization'][1]}")
        print(f"SHA256: {sha256}\nMetadata: {args.output_metadata}")
    except (ImportError, ModuleNotFoundError) as error:
        if "tensorflow" in str(error).lower():
            print(f"ERROR: {error}", file=sys.stderr)
            return 1
        print(f"ERROR: KWS dependencies are missing: {error}", file=sys.stderr)
        print("Create/activate the training virtual environment and install scripts/requirements-kws.txt.", file=sys.stderr)
        return 1
    except (OSError, ValueError, RuntimeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
