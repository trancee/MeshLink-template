package ch.trancee.meshlink

/**
 * MeshLink SDK entry point. Instance-based — construct with [MeshLinkSettings] and a
 * [MeshLinkEnvironment].
 *
 * Placeholder for BCV baseline. Real implementation will handle lifecycle, peer management,
 * transfer, routing, and diagnostics. This placeholder exists only to give the Gradle skeleton a
 * real compilation unit and a stable [MeshLink] symbol for the Binary Compatibility Validator
 * baseline.
 */
public class MeshLink private constructor() {
    /** The MeshLink library version. Replaced once real versioning exists. */
    public companion object {
        public val VERSION: MeshLinkVersion = MeshLinkVersion(0, 0, 0)
    }
}
