#!/usr/bin/env python3
"""Validate an INT8 KWS TFLite model against its manifest and held-out WAV data."""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--metadata", type=Path)
    parser.add_argument("--data-root", type=Path, default=Path("data/wakeword"))
    parser.add_argument("--seed", type=int, help="Override the split seed recorded in model metadata.")
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    metadata_path = args.metadata or args.model.with_name("jarvis_model.json")
    if not args.model.is_file():
        print(f"ERROR: KWS model is missing: {args.model}. Train a real model from WAV recordings first.", file=sys.stderr)
        return 1
    if not metadata_path.is_file():
        print(f"ERROR: KWS model metadata is missing: {metadata_path}.", file=sys.stderr)
        return 1
    try:
        import numpy as np
        import tensorflow as tf
        from kws_features import (
            feature_contract,
            positive_dataset_sha256,
            discover_dataset,
            split_by_file,
        )
        from train_kws import int8_predictions, metrics, prepare_features, sha256_file

        metadata = json.loads(metadata_path.read_text())
        expected_features = feature_contract()
        if metadata.get("featureSpec") != expected_features:
            raise ValueError(f"Model metadata feature contract mismatch: {metadata.get('featureSpec')}")
        if metadata.get("phrase") != "Jarvis" or metadata.get("labels") != ["background", "unknown", "jarvis"]:
            raise ValueError("Model metadata phrase/class labels are invalid")
        if metadata.get("positiveClassIndex") != 2:
            raise ValueError("Model metadata positiveClassIndex must be 2 (jarvis)")
        if metadata.get("modelFile") != args.model.name:
            raise ValueError(f"Metadata modelFile={metadata.get('modelFile')} does not match {args.model.name}")
        if sha256_file(args.model) != metadata.get("modelSha256"):
            raise ValueError("Model SHA-256 does not match jarvis_model.json")

        dataset = discover_dataset(args.data_root)
        positive_hash = positive_dataset_sha256([item.path for item in dataset["positive"]])
        if positive_hash != metadata.get("positiveDatasetSha256"):
            raise ValueError("Positive WAV dataset SHA-256 does not match model metadata")
        split_seed = args.seed if args.seed is not None else int(metadata.get("splitSeed", 2026))
        splits = split_by_file(dataset, split_seed)
        test_files = splits["test"]
        test_features, test_labels = prepare_features(test_files, 0, args.seed, [])

        interpreter = tf.lite.Interpreter(model_path=str(args.model), num_threads=1)
        interpreter.allocate_tensors()
        input_info = interpreter.get_input_details()[0]
        output_info = interpreter.get_output_details()[0]
        input_shape = input_info["shape"].tolist()
        output_shape = output_info["shape"].tolist()
        print(f"Input: shape={input_shape} dtype={input_info['dtype']} quantization={input_info['quantization']}")
        print(f"Output: shape={output_shape} dtype={output_info['dtype']} quantization={output_info['quantization']}")
        if metadata.get("inputShape") != input_shape or metadata.get("outputShape") != output_shape:
            raise ValueError("Model metadata tensor shapes do not match the TFLite model")
        if metadata.get("inputType") != "INT8" or metadata.get("outputType") != "INT8":
            raise ValueError("Model metadata tensor types must both be INT8")
        if input_shape != [1, 49, 10, 1] or input_info["dtype"] != np.int8:
            raise ValueError(f"Expected INT8 input [1,49,10,1], got {input_shape} {input_info['dtype']}")
        if output_shape != [1, 3] or output_info["dtype"] != np.int8:
            raise ValueError(f"ERROR: INT8 model output shape/type is invalid: {output_shape} {output_info['dtype']}")
        if input_info["quantization"][0] <= 0 or output_info["quantization"][0] <= 0:
            raise ValueError("INT8 model quantization is invalid")
        for key, detail in (("inputScale", input_info), ("outputScale", output_info)):
            if not math.isclose(float(metadata[key]), float(detail["quantization"][0]), rel_tol=1e-6, abs_tol=1e-9):
                raise ValueError(f"Metadata {key} does not match TFLite tensor quantization")
        for key, detail in (("inputZeroPoint", input_info), ("outputZeroPoint", output_info)):
            if int(metadata[key]) != int(detail["quantization"][1]):
                raise ValueError(f"Metadata {key} does not match TFLite tensor quantization")

        test_outputs = int8_predictions(tf, args.model.read_bytes(), test_features)
        if test_outputs.shape != (len(test_labels), 3) or not np.isfinite(test_outputs).all():
            raise ValueError("TFLite test inference returned invalid shape or non-finite values")
        if any(item.class_id not in range(3) for item in test_files):
            raise ValueError("Dataset contains an invalid class index")
        threshold = float(metadata["threshold"])
        if not math.isfinite(threshold) or not 0.0 <= threshold <= 1.0:
            raise ValueError("Metadata threshold is invalid")
        recall, false_accept = metrics(test_outputs[:, 2], test_labels, threshold)
        target_fpr = float(metadata["targetFalsePositiveRate"])
        print(f"Held-out test positive recall: {recall:.3f}")
        print(f"Held-out test negative false accept rate: {false_accept:.3f}")
        print(f"Threshold: {threshold:.6f} (validation target FPR {target_fpr:.3f})")
        print(f"SHA256: {metadata['modelSha256']}")
        if false_accept > target_fpr:
            raise ValueError("Held-out test false-accept rate exceeds the metadata target")
        if recall <= 0.0:
            raise ValueError("Held-out test set contains no positive detections at the calibrated threshold")
    except (ImportError, ModuleNotFoundError) as error:
        print(f"ERROR: KWS dependencies are missing: {error}", file=sys.stderr)
        print("Create/activate the training virtual environment and install scripts/requirements-kws.txt.", file=sys.stderr)
        return 1
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError, RuntimeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
