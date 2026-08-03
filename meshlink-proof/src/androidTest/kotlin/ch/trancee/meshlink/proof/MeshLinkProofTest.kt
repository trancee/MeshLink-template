package ch.trancee.meshlink.proof

import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.trancee.meshlink.MeshLinkVersion
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Placeholder instrumented test. Requires a real Android device/emulator to
 * run `connectedAndroidTest`; real BLE proof scenarios (which need actual
 * hardware, not an emulator) will replace this.
 */
@RunWith(AndroidJUnit4::class)
class MeshLinkProofTest {
    @Test
    fun libraryVersionUnderTest_matchesMeshLink() {
        assertEquals(MeshLinkVersion(0, 0, 0), MeshLinkProof.libraryVersionUnderTest())
    }
}
