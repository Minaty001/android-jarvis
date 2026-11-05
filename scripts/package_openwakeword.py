#!/usr/bin/env python3
"""Install two openWakeWord TFLite heads and emit the hash-checked Android manifest."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import shutil
from pathlib import Path
import numpy as np


def validate_model(path: Path, input_shape: list[int], output_shape: list[int]) -> None:
    if path.suffix.lower() == ".onnx":
        import onnxruntime as ort

        sess = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
        inp = sess.get_inputs()[0]
        out = sess.get_outputs()[0]
        if list(inp.shape) != input_shape or list(out.shape) != output_shape:
            raise ValueError(f"Unexpected ONNX contract in {path.name}: {inp.shape} -> {out.shape}")
        test_out = sess.run(None, {inp.name: np.zeros(input_shape, dtype=np.float32)})[0]
        if not np.isfinite(test_out).all():
            raise ValueError(f"Non-finite ONNX output in {path.name}")
    else:
        from ai_edge_litert.interpreter import Interpreter

        interpreter = Interpreter(model_path=str(path), num_threads=1)
        interpreter.allocate_tensors()
        input_info = interpreter.get_input_details()[0]
        output_info = interpreter.get_output_details()[0]
        if input_info["shape"].tolist() != input_shape or output_info["shape"].tolist() != output_shape:
            raise ValueError(f"Unexpected TFLite contract in {path.name}: {input_info['shape'].tolist()} -> {output_info['shape'].tolist()}")
        if input_info["dtype"] != np.float32 or output_info["dtype"] != np.float32:
            raise ValueError(f"Expected float32 TFLite tensors in {path.name}")
        interpreter.set_tensor(input_info["index"], np.zeros(input_shape, dtype=np.float32))
        interpreter.invoke()
        if not np.isfinite(interpreter.get_tensor(output_info["index"])).all():
            raise ValueError(f"Non-finite TFLite output in {path.name}")


def validate_frontend(assets: Path) -> None:
    from ai_edge_litert.interpreter import Interpreter, OpResolverType

    mel = Interpreter(
        model_path=str(assets / "melspectrogram.tflite"), num_threads=1,
        experimental_op_resolver_type=OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES,
    )
    mel.allocate_tensors()
    mel_input, mel_output = mel.get_input_details()[0], mel.get_output_details()[0]
    if mel_input["shape"].tolist() != [1, 1760] or mel_output["shape"].tolist() != [1, 1, 8, 32] or mel_input["dtype"] != np.float32 or mel_output["dtype"] != np.float32:
        raise ValueError("Unexpected openWakeWord melspectrogram contract")
    mel.set_tensor(mel_input["index"], np.zeros([1, 1760], dtype=np.float32))
    mel.invoke()
    if not np.isfinite(mel.get_tensor(mel_output["index"])).all():
        raise ValueError("Non-finite openWakeWord melspectrogram output")

    embedding = Interpreter(
        model_path=str(assets / "embedding_model.tflite"), num_threads=1,
        experimental_op_resolver_type=OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES,
    )
    embedding.allocate_tensors()
    embedding_input, embedding_output = embedding.get_input_details()[0], embedding.get_output_details()[0]
    if embedding_input["shape"].tolist() != [1, 76, 32, 1] or embedding_output["shape"].tolist() != [1, 1, 1, 96]:
        raise ValueError("Unexpected openWakeWord embedding contract")
    embedding.set_tensor(embedding_input["index"], np.zeros([1, 76, 32, 1], dtype=np.float32))
    embedding.invoke()
    if not np.isfinite(embedding.get_tensor(embedding_output["index"])).all():
        raise ValueError("Non-finite openWakeWord embedding output")


def fix_melspectrogram_input(path: Path) -> None:
    """Freeze the upstream dynamic frontend to the 110 ms Android audio window."""
    import tflite

    data = bytearray(path.read_bytes())
    model = tflite.Model.GetRootAs(data, 0)
    if model.SubgraphsLength() != 1:
        raise ValueError("Expected one melspectrogram subgraph")
    subgraph = model.Subgraphs(0)
    if subgraph.InputsLength() != 1:
        raise ValueError("Expected one melspectrogram input")
    tensor = subgraph.Tensors(subgraph.Inputs(0))
    if tensor.ShapeLength() != 2:
        raise ValueError("Expected a rank-two melspectrogram input")

    def set_vector(table, offset: int, values: list[int]) -> None:
        field = table._tab.Offset(offset)
        if field == 0:
            return
        start = table._tab.Vector(field)
        for index, value in enumerate(values):
            data[start + index * 4 : start + index * 4 + 4] = int(value).to_bytes(4, "little", signed=True)

    set_vector(tensor, 4, [1, 1760])
    set_vector(tensor, 18, [1, 1760])
    path.write_bytes(data)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def install(source: Path, destination: Path) -> None:
    if not source.is_file():
        raise FileNotFoundError(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    if source.resolve() != destination.resolve():
        shutil.copy2(source, destination)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jarvis-model", type=Path)
    parser.add_argument("--jarvis-threshold", type=float)
    parser.add_argument("--hey-jarvis-model", type=Path, default=Path("app/src/main/assets/hey_jarvis_custom.onnx"))
    parser.add_argument("--hey-jarvis-threshold", type=float, default=0.55)
    parser.add_argument("--assets", type=Path, default=Path("app/src/main/assets"))
    args = parser.parse_args()

    models = []
    if args.jarvis_model is not None:
        if args.jarvis_threshold is None:
            parser.error("--jarvis-threshold is required with --jarvis-model")
        models.append(("Jarvis", args.jarvis_model, args.jarvis_threshold, "jarvis.tflite"))
    models.append(("Hey Jarvis", args.hey_jarvis_model, args.hey_jarvis_threshold, args.hey_jarvis_model.name))
    for phrase, _, threshold, _ in models:
        if not 0.0 < threshold <= 1.0:
            raise ValueError(f"{phrase} threshold must be in (0, 1]")
    for _, source, _, _ in models:
        validate_model(source, [1, 16, 96], [1, 1])

    spec = importlib.util.find_spec("openwakeword")
    if spec is None or not spec.submodule_search_locations:
        raise RuntimeError("Install openWakeWord before packaging its shared feature models")
    feature_assets = Path(next(iter(spec.submodule_search_locations))) / "resources/models"
    for asset in ("melspectrogram.tflite", "embedding_model.tflite"):
        install(feature_assets / asset, args.assets / asset)
    fix_melspectrogram_input(args.assets / "melspectrogram.tflite")
    validate_frontend(args.assets)

    manifest_models = []
    for phrase, source, threshold, filename in models:
        target = args.assets / filename
        install(source, target)
        manifest_models.append({"phrase": phrase, "asset": filename, "threshold": threshold, "sha256": sha256(target)})

    manifest = {
        "version": 1,
        "sampleRate": 16000,
        "frameSamples": 1280,
        "featureModels": {
            "melspectrogram": {"asset": "melspectrogram.tflite", "sha256": sha256(args.assets / "melspectrogram.tflite")},
            "embedding": {"asset": "embedding_model.tflite", "sha256": sha256(args.assets / "embedding_model.tflite")},
        },
        "models": manifest_models,
    }
    (args.assets / "openwakeword_models.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Installed openWakeWord assets in {args.assets}")


if __name__ == "__main__":
    main()
