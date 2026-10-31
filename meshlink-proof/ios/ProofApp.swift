import SwiftUI

@main
struct ProofApp: App {
    @StateObject private var proof = BleProof()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            VStack(alignment: .leading, spacing: 16) {
                Text("Foreground BLE proof").font(.headline)
                Text("SYNTHETIC, UNENCRYPTED BYTES ONLY. Start opposite roles on two phones. Restart both roles between runs.")
                Button("Start peripheral") { proof.start(.peripheral) }
                Button("Start central") { proof.start(.central) }
                Button("Send 100 probes from central") { proof.send() }
                Button("Stop") { proof.stop() }
                Text(proof.status).font(.system(.body, design: .monospaced))
                Spacer()
            }
            .padding()
            .onChange(of: phase) { value in
                if value == .background { proof.stop() }
            }
        }
    }
}
