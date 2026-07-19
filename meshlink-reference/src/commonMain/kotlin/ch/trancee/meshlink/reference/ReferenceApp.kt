package ch.trancee.meshlink.reference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ch.trancee.meshlink.MeshLink

/**
 * Placeholder reference screen. Demonstrates that meshlink-reference can consume the public
 * :meshlink API. Replaced once real reference UI exists.
 */
@Composable
public fun ReferenceApp() {
    Text("MeshLink reference app — library version ${MeshLink.VERSION}")
}
