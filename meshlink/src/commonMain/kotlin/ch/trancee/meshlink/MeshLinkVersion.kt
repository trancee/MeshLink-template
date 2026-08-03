package ch.trancee.meshlink

/** Semantic version for MeshLink releases. */
public data class MeshLinkVersion(
    public val major: Int,
    public val minor: Int,
    public val patch: Int,
) : Comparable<MeshLinkVersion> {
    public companion object {
        private const val SEMVER_PARTS = 3

        /**
         * Parse a semantic version string in the form `major.minor.patch`.
         *
         * @throws IllegalArgumentException if the string is not a valid semver.
         */
        public fun parse(version: String): MeshLinkVersion {
            val parts = version.split(".")
            require(parts.size == SEMVER_PARTS) {
                "Expected semver string 'major.minor.patch', got: '$version'"
            }
            val major = parts[0].toIntOrNull()
            val minor = parts[1].toIntOrNull()
            val patch = parts[2].toIntOrNull()
            require(major != null) { "Invalid major version in '$version'" }
            require(minor != null) { "Invalid minor version in '$version'" }
            require(patch != null) { "Invalid patch version in '$version'" }
            return MeshLinkVersion(major, minor, patch)
        }
    }

    override fun toString(): String = toStringValue()

    /** Returns the string representation of this version. */
    internal fun toStringValue(): String = "$major.$minor.$patch"

    override fun compareTo(other: MeshLinkVersion): Int =
        compareValuesBy(
            this,
            other,
            MeshLinkVersion::major,
            MeshLinkVersion::minor,
            MeshLinkVersion::patch,
        )
}
