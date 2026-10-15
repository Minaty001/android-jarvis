#!/usr/bin/env python3
from pathlib import Path

def main():
    store_code = Path("app/src/main/java/com/jarvis/memory/MemoryStore.kt").read_text()
    if "fun searchMemories" not in store_code or "fun trimCache" not in store_code:
        print("FAIL (expected RED): MemoryStore lacks searchMemories or trimCache")
        return 1

    print("PASS: MemoryStore has searchMemories and trimCache")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
