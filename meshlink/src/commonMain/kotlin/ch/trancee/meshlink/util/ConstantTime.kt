package ch.trancee.meshlink.util

/**
 * Constant-time cryptographic utility functions.
 *
 * All comparisons and selections here are designed to execute in time independent of the secret
 * data, preventing timing side-channel attacks. See docs/decisions/crypto/constant-time-policy.md.
 *
 * SPEC-ANCHOR: constant-time
 */
public object ConstantTime {

    /**
     * Constant-time comparison of two byte arrays. Returns 0 if equal, non-zero if different.
     *
     * Execution time is independent of the number of matching bytes
     * [and independent of array length]: both arrays are fully iterated even when the result
     * becomes non-zero early. When arrays differ in size, the length difference is folded into the
     * result so that the timing still depends only on the maximum of the two lengths.
     *
     * Per spec §7.4 this is the equivalent of `MessageDigest.isEqual`.
     */
    public fun constantTimeEquals(a: ByteArray, b: ByteArray): Int {
        val diff = a.size xor b.size
        val minLength = minOf(a.size, b.size)
        val maxLength = maxOf(a.size, b.size)
        var result = diff
        for (i in 0 until minLength) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        // Cover the tail of the longer array so time does not leak the shared prefix length.
        for (i in minLength until maxLength) {
            result = result or (if (a.size > b.size) a[i].toInt() else b[i].toInt())
        }
        return result
    }

    /**
     * Constant-time selection: if [condition] is 0, returns a copy of [a]; otherwise returns a copy
     * of [b]. Both [a] and [b] are fully read; only the selection branch is data-dependent.
     *
     * The compiler's branch predictor may still observe the condition value, so on platforms with
     * speculative execution (JVM, x86) this provides best-effort constant-time only. See
     * docs/decisions/crypto/constant-time-policy.md for the full platform matrix.
     *
     * @param condition any non-zero value selects [b]; 0 selects [a].
     * @param a returned when [condition] is 0.
     * @param b returned when [condition] is non-zero.
     * @throws IllegalArgumentException if [a] and [b] have different sizes.
     */
    public fun constantTimeSelect(condition: Int, a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) {
            "constantTimeSelect requires arrays of equal size, " +
                "got a.size=${a.size} b.size=${b.size}"
        }
        // Normalize any non-zero condition to 0xFFFFFFFF (all bits set); 0 stays 0.
        // Branch-free: (c or -c) has the sign bit set iff c != 0; unsigned shift by 31
        // extracts 1 for non-zero and 0 for zero; negation yields the full mask.
        val mask = -(condition or -condition ushr 31)
        return ByteArray(a.size) { i ->
            val aByte = a[i].toInt()
            val bByte = b[i].toInt()
            ((aByte and mask.inv()) or (bByte and mask)).toByte()
        }
    }

    /** Constant-time byte array comparison returning a Boolean. */
    public fun constantTimeEqualsBoolean(a: ByteArray, b: ByteArray): Boolean =
        constantTimeEquals(a, b) == 0

    /**
     * Constant-time zero check: returns true if all bytes in [a] are zero.
     *
     * Execution time is independent of the position of the first non-zero byte, though it does
     * depend on the array length (as it must inspect every byte).
     */
    public fun constantTimeIsZero(a: ByteArray): Boolean {
        var result = 0
        for (byte in a) {
            result = result or byte.toInt()
        }
        return result == 0
    }

    /**
     * Constant-time conditional swap: if [condition] is non-zero, swaps [a] and [b]; otherwise
     * returns them unchanged. Both branches are computed; only the output selection is
     * condition-dependent.
     *
     * @param condition non-zero triggers the swap; 0 leaves the arrays unchanged.
     * @param a first byte array.
     * @param b second byte array.
     * @return a [Pair] of the (possibly swapped) arrays.
     * @throws IllegalArgumentException if [a] and [b] have different sizes.
     */
    public fun constantTimeSwap(
        condition: Int,
        a: ByteArray,
        b: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        require(a.size == b.size) {
            "constantTimeSwap requires arrays of equal size, " +
                "got a.size=${a.size} b.size=${b.size}"
        }
        val mask = -(condition or -condition ushr 31)
        val swappedA =
            ByteArray(a.size) { i ->
                val aByte = a[i].toInt()
                val bByte = b[i].toInt()
                val maskedDiff = mask and (aByte xor bByte)
                (aByte xor maskedDiff).toByte()
            }
        val swappedB =
            ByteArray(a.size) { i ->
                val aByte = a[i].toInt()
                val bByte = b[i].toInt()
                val maskedDiff = mask and (aByte xor bByte)
                (bByte xor maskedDiff).toByte()
            }
        return swappedA to swappedB
    }
}
