package ch.trancee.meshlink.util

/**
 * Constant-time cryptographic utility functions.
 *
 * All comparisons and selections here are designed to execute in time independent of the secret
 * data being compared, preventing timing side-channel attacks. See
 * docs/decisions/crypto/constant-time-policy.md.
 *
 * SPEC-ANCHOR: constant-time
 */
public object ConstantTime {

    /**
     * Constant-time comparison of two byte arrays. Returns 0 if equal, non-zero if different.
     * Execution time is independent of the number of matching bytes.
     */
    public fun constantTimeEquals(a: ByteArray, b: ByteArray): Int {
        if (a.size != b.size) return -1
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result
    }

    /**
     * Constant-time selection: returns [a] if [condition] is 0, [b] otherwise. Both branches are
     * computed; only the selection is data-dependent.
     */
    public fun constantTimeSelect(condition: Int, a: ByteArray, b: ByteArray): ByteArray {
        val mask = -condition
        return ByteArray(a.size) { i ->
            val av = a[i].toInt()
            val bv = b[i].toInt()
            ((av and mask.inv()) or (bv and mask)).toByte()
        }
    }

    /** Constant-time byte array comparison returning a Boolean. */
    public fun constantTimeEqualsBoolean(a: ByteArray, b: ByteArray): Boolean =
        constantTimeEquals(a, b) == 0

    /** Constant-time zero check: returns true if all bytes in [a] are zero. */
    public fun constantTimeIsZero(a: ByteArray): Boolean {
        var result = 0
        for (byte in a) {
            result = result or byte.toInt()
        }
        return result == 0
    }

    /** Constant-time conditional swap: if [condition] is non-zero, swaps [a] and [b]. */
    public fun constantTimeSwap(
        condition: Int,
        a: ByteArray,
        b: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        val mask = -condition
        val swappedA =
            ByteArray(a.size) { i ->
                val av = a[i].toInt()
                val bv = b[i].toInt()
                val temp = mask and (av xor bv)
                (av xor temp).toByte()
            }
        val swappedB =
            ByteArray(a.size) { i ->
                val av = a[i].toInt()
                val bv = b[i].toInt()
                val temp = mask and (av xor bv)
                (bv xor temp).toByte()
            }
        return swappedA to swappedB
    }
}
