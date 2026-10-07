# Stabilization Notes

This file records concrete maintenance changes; it does not claim benchmark results.

- LAN web files are streamed as bytes so images and other binary assets are preserved.
- LAN API bodies are decoded as UTF-8 using their byte length and constrained by a request-size limit.
- Audio capture backs off on transient zero-length reads and resets after unrecoverable `AudioRecord` errors so it can be started again.
- Memory reads purge expired records and their full-text index rows.
- Session summaries use bounded stored conversation text and are skipped when the configured LLM returns no usable summary.
- Wearable wake and command broadcasts preserve their action when starting the foreground service from a cold state.

APK build sizes and checksums are listed in `dist/` when exported. No comparative speed, cost, or code-size benchmark is maintained here.
