"""Shared WAV loading, deterministic splitting, and Android-parity MFCC features."""
from __future__ import annotations

import hashlib
import random
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

SAMPLE_RATE = 16_000
WINDOW_SAMPLES = 16_000
FRAME_LENGTH = 400
FRAME_STRIDE = 320
FFT_SIZE = 512
NUM_MEL_FILTERS = 40
NUM_MFCC = 10
NUM_FRAMES = (WINDOW_SAMPLES - FRAME_LENGTH) // FRAME_STRIDE + 1
PREEMPHASIS = 0.97
LABELS = ("background", "unknown", "jarvis")
CLASS_BY_DIRECTORY = {"background": 0, "unknown": 1, "positive": 2}
MINIMUM_PER_CLASS = 30


def hz_to_mel(hz):
    return 2595.0 * np.log10(1.0 + np.asarray(hz) / 700.0)


def mel_to_hz(mel):
    return 700.0 * (10.0 ** (np.asarray(mel) / 2595.0) - 1.0)


def make_mel_filterbank():
    low_mel = hz_to_mel(20.0)
    high_mel = hz_to_mel(SAMPLE_RATE / 2.0)
    mel_points = np.linspace(low_mel, high_mel, NUM_MEL_FILTERS + 2)
    hz_points = mel_to_hz(mel_points)
    bins = np.floor((FFT_SIZE + 1) * hz_points / SAMPLE_RATE).astype(np.int32)
    bank = np.zeros((NUM_MEL_FILTERS, FFT_SIZE // 2 + 1), dtype=np.float32)
    for m in range(1, NUM_MEL_FILTERS + 1):
        left, center, right = bins[m - 1 : m + 2]
        if center > left:
            for k in range(left, min(center, bank.shape[1])):
                bank[m - 1, k] = (k - left) / float(center - left)
        if right > center:
            for k in range(center, min(right, bank.shape[1])):
                bank[m - 1, k] = (right - k) / float(right - center)
    return bank


def make_dct_matrix():
    matrix = np.zeros((NUM_MFCC, NUM_MEL_FILTERS), dtype=np.float32)
    factor = np.sqrt(2.0 / NUM_MEL_FILTERS)
    for i in range(NUM_MFCC):
        for j in range(NUM_MEL_FILTERS):
            matrix[i, j] = factor * np.cos(np.pi * i * (j + 0.5) / NUM_MEL_FILTERS)
    return matrix


MEL_BANK = make_mel_filterbank()
DCT_MATRIX = make_dct_matrix()
HAMMING_WINDOW = np.hamming(FRAME_LENGTH).astype(np.float32)


def load_audio(path: Path) -> np.ndarray:
    audio, sample_rate = sf.read(path, always_2d=False, dtype="float32")
    audio = np.asarray(audio, dtype=np.float32)
    if audio.ndim == 2:
        audio = audio.mean(axis=1, dtype=np.float32)
    if audio.ndim != 1 or len(audio) == 0:
        raise ValueError(f"Empty or unsupported audio: {path}")
    if not np.isfinite(audio).all():
        raise ValueError(f"Audio contains non-finite values: {path}")
    if sample_rate != SAMPLE_RATE:
        if sample_rate <= 0:
            raise ValueError(f"Invalid sample rate {sample_rate}: {path}")
        audio = resample_poly(audio, SAMPLE_RATE, sample_rate).astype(np.float32)
    peak = float(np.max(np.abs(audio)))
    if peak > 1.0:
        audio = audio / peak
    return audio


def window_audio(audio: np.ndarray, start: int = 0) -> np.ndarray:
    x = np.asarray(audio, dtype=np.float32)
    if len(x) >= WINDOW_SAMPLES:
        start = min(max(0, start), len(x) - WINDOW_SAMPLES)
        return x[start : start + WINDOW_SAMPLES].copy()
    result = np.zeros(WINDOW_SAMPLES, dtype=np.float32)
    result[: len(x)] = x
    return result


def extract_mfcc(audio) -> np.ndarray:
    """Exactly 49x10 Android features: PCM normalized to [-1,1] before this call."""
    x = window_audio(np.asarray(audio, dtype=np.float32))
    frames = []
    for frame_index in range(NUM_FRAMES):
        start = frame_index * FRAME_STRIDE
        frame = x[start : start + FRAME_LENGTH]
        emphasized = np.empty_like(frame)
        emphasized[0] = frame[0]
        emphasized[1:] = frame[1:] - PREEMPHASIS * frame[:-1]
        spectrum = np.fft.rfft(emphasized * HAMMING_WINDOW, n=FFT_SIZE)
        power = (np.abs(spectrum) ** 2 / FFT_SIZE).astype(np.float32)
        mel_energy = np.maximum(MEL_BANK @ power, 1e-6)
        log_mel = np.log(mel_energy)
        frames.append((DCT_MATRIX @ log_mel).astype(np.float32))
    return np.stack(frames, axis=0)


@dataclass(frozen=True)
class AudioFile:
    path: Path
    class_id: int
    directory_class: str


def positive_dataset_sha256(files: list[Path]) -> str:
    """Matches WakeWordDatasetStore: sorted filename, NUL, file bytes, NUL."""
    digest = hashlib.sha256()
    for path in sorted(files, key=lambda p: p.name):
        digest.update(path.name.encode("utf-8"))
        digest.update(b"\0")
        with path.open("rb") as stream:
            while chunk := stream.read(8192):
                digest.update(chunk)
        digest.update(b"\0")
    return digest.hexdigest()


def discover_dataset(data_root: Path) -> dict[str, list[AudioFile]]:
    data_root = Path(data_root)
    result = {name: [] for name in CLASS_BY_DIRECTORY}
    for directory_name, class_id in CLASS_BY_DIRECTORY.items():
        directory = data_root / directory_name
        if not directory.is_dir():
            continue
        result[directory_name] = [
            AudioFile(path, class_id, directory_name)
            for path in sorted(directory.rglob("*"))
            if path.is_file() and path.suffix.lower() == ".wav"
        ]

    seen = {}
    for class_name, files in result.items():
        for audio_file in files:
            file_hash = hashlib.sha256(audio_file.path.read_bytes()).hexdigest()
            if file_hash in seen:
                raise ValueError(f"Duplicate WAV recordings: {seen[file_hash]} and {audio_file.path}")
            seen[file_hash] = audio_file.path
            try:
                load_audio(audio_file.path)
            except Exception as error:
                raise ValueError(f"Invalid WAV {audio_file.path}: {error}") from error

    for class_name in ("positive", "unknown", "background"):
        count = len(result[class_name])
        if count == 0:
            label = "positive Jarvis" if class_name == "positive" else class_name
            raise ValueError(f"No {label} recordings found.")
        if count < MINIMUM_PER_CLASS:
            label = "positive" if class_name == "positive" else class_name
            raise ValueError(f"Only {count} {label} recordings found. Minimum development dataset is {MINIMUM_PER_CLASS}.")
    return result


def split_by_file(dataset: dict[str, list[AudioFile]], seed: int = 2026):
    rng = random.Random(seed)
    splits = {"train": [], "validation": [], "test": []}
    for class_name in ("positive", "unknown", "background"):
        files = list(dataset[class_name])
        rng.shuffle(files)
        train_end = max(1, int(len(files) * 0.8))
        validation_count = max(1, int(len(files) * 0.1))
        validation_end = min(len(files) - 1, train_end + validation_count)
        splits["train"].extend(files[:train_end])
        splits["validation"].extend(files[train_end:validation_end])
        splits["test"].extend(files[validation_end:])
    return splits


def augment_audio(audio, rng: np.random.Generator, backgrounds: list[np.ndarray] | None = None):
    x = window_audio(audio)
    x *= rng.uniform(0.75, 1.25)
    shift = int(rng.integers(-800, 801))
    if shift > 0:
        x[shift:] = x[:-shift].copy()
        x[:shift] = 0.0
    elif shift < 0:
        amount = -shift
        x[:-amount] = x[amount:].copy()
        x[-amount:] = 0.0
    if backgrounds:
        background = window_audio(backgrounds[int(rng.integers(0, len(backgrounds)))])
        snr_db = rng.uniform(5.0, 25.0)
        speech_rms = float(np.sqrt(np.mean(x * x)))
        noise_rms = float(np.sqrt(np.mean(background * background)))
        if speech_rms > 0.0 and noise_rms > 1e-8:
            x += background * (speech_rms / (noise_rms * (10.0 ** (snr_db / 20.0))))
    x += rng.normal(0.0, rng.uniform(0.0002, 0.002), size=x.shape).astype(np.float32)
    return np.clip(x, -1.0, 1.0).astype(np.float32)


def feature_contract():
    return {
        "sampleRate": SAMPLE_RATE,
        "windowSamples": WINDOW_SAMPLES,
        "frameLengthSamples": FRAME_LENGTH,
        "frameStrideSamples": FRAME_STRIDE,
        "fftSize": FFT_SIZE,
        "numMelFilters": NUM_MEL_FILTERS,
        "numMfcc": NUM_MFCC,
        "numFrames": NUM_FRAMES,
    }
