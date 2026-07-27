package ch.trancee.meshlink

import co.touchlab.skie.configuration.annotations.SuppressSkieWarning

/**
 * Placeholder entry point for the MeshLink public API.
 *
 * This exists only to give the Gradle skeleton a real compilation unit and a stable [MeshLink]
 * symbol for the Binary Compatibility Validator baseline. It will be replaced by the actual
 * protocol API as MeshLink's TDD-driven implementation work begins (see PROJECT.md and
 * docs/decisions/).
 */
@SuppressSkieWarning.NameCollision
public object MeshLink {
    /** The MeshLink library version. Replaced once real versioning exists. */
    public const val VERSION: String = "0.0.0"
}
