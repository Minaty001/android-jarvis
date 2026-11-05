# Wake word models and attribution

## Custom "Hey Jarvis" Wake Word Model
- `hey_jarvis_custom.onnx` is the custom-trained openWakeWord DNN classifier (32 hidden units, 50,000 steps). Trained with 46,000 synthetic audio clips (Piper LibriTTS-High), ACAV100M 2,000-hr background speech features, and MIT room impulse responses.
- Evaluated performance: False Positives / Hour: 0.088 / hr (target <= 0.20/hr); Adversarial Hard-Negative Rejection: 99.70%; Detection Recall: 62.8% - 64.8%.
- Recommended calibrated threshold: 0.55 with 2.0s cooldown.
- SHA-256: `c18d024b331e663989a63f799cd429bbade28404d4a291eeebb7f8560f807424`.

## Shared Frontend Feature Models
- `melspectrogram.tflite` and `embedding_model.tflite` are the shared openWakeWord frontend models. The openWakeWord source and models are licensed under [Apache 2.0](https://github.com/dscripka/openWakeWord/blob/main/LICENSE).
