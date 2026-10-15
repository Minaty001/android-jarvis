#!/usr/bin/env python3
import re
from pathlib import Path

def main():
    props = Path("gradle.properties").read_text()
    jvmargs = re.search(r"org\.gradle\.jvmargs=(.*)", props)
    daemonargs = re.search(r"kotlin\.daemon\.jvmargs=(.*)", props)
    workers = re.search(r"org\.gradle\.workers\.max=(\d+)", props)

    assert jvmargs, "Missing org.gradle.jvmargs"
    assert daemonargs, "Missing kotlin.daemon.jvmargs"
    assert workers, "Missing org.gradle.workers.max"

    jvm_mem = int(re.search(r"-Xmx(\d+)m", jvmargs.group(1)).group(1))
    daemon_mem = int(re.search(r"-Xmx(\d+)m", daemonargs.group(1)).group(1))
    max_workers = int(workers.group(1))

    assert jvm_mem <= 1536, f"Gradle JVM heap ({jvm_mem}m) exceeds 1536m safe limit for 4GB RAM"
    assert daemon_mem <= 1024, f"Kotlin daemon heap ({daemon_mem}m) exceeds 1024m safe limit"
    assert max_workers <= 2, f"Max workers ({max_workers}) exceeds 2 CPU cores"
    print("PASS: gradle.properties is properly tuned for 2-core / 4GB hardware")

if __name__ == "__main__":
    main()
