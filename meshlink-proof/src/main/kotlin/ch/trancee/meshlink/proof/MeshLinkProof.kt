package ch.trancee.meshlink.proof

import ch.trancee.meshlink.MeshLink
import ch.trancee.meshlink.MeshLinkVersion

/**
 * Placeholder real-device BLE proof harness entry point. BLE proofs only run
 * on physical Android hardware — never on an emulator (BLE radios are not
 * emulated). Replaced once real proof scenarios exist.
 */
public object MeshLinkProof {
    public fun libraryVersionUnderTest(): MeshLinkVersion = MeshLink.VERSION
}
