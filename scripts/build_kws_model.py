#!/usr/bin/env python3
"""Compatibility wrapper; synthetic KWS generation has been removed.

Use scripts/train_kws.py with real positive, unknown, and background WAV data.
"""

from train_kws import main


if __name__ == "__main__":
    main()
