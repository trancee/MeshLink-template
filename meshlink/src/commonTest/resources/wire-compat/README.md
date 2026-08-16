# Wire Compatibility Test Vectors

Hex test vectors for byte-for-byte wire codec verification across all KMP targets.

See SPEC.md §13.2. Vectors here validate that the custom MeshLink Wire Codec
produces identical binary output on JVM, Android, and iOS arm64 — no
emulator/simulator can substitute (no real BLE radios).

This directory is populated as wire codec frame encodings land. Until then,
host JVM tests cover codec logic.
