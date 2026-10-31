import CoreBluetooth
import SwiftUI
import UIKit

enum ProofRole { case central, peripheral }

final class BleProof: NSObject, ObservableObject {
    static let serviceUUID = CBUUID(string: "c247f4e0-18b7-4d48-a73c-7a62f4860190")
    static let requestUUID = CBUUID(string: "c247f4e1-18b7-4d48-a73c-7a62f4860190")
    static let responseUUID = CBUUID(string: "c247f4e2-18b7-4d48-a73c-7a62f4860190")
    static let packetCount = 100

    @Published var status = "STOPPED"
    var role = ProofRole.central
    var central: CBCentralManager?
    var peripheralManager: CBPeripheralManager?
    var remote: CBPeripheral?
    var requestCharacteristic: CBCharacteristic?
    var responseCharacteristic: CBMutableCharacteristic?
    var subscriber: CBCentral?
    var pendingNotification: Data?
    var expectedResponse: Data?
    var earlyResponse: Data?
    var writing = false
    var ready = false
    var token = [UInt8]()
    var sent = 0
    var received = 0
    private var deadline: DispatchWorkItem?
    private var started = 0.0
    private var exchangeStarted = 0.0

    func start(_ role: ProofRole) {
        stopRadios()
        self.role = role
        sent = 0
        received = 0
        started = ProcessInfo.processInfo.systemUptime
        exchangeStarted = 0
        UIApplication.shared.isIdleTimerDisabled = true
        report("STARTING")
        let work = DispatchWorkItem { [weak self] in
            self?.finish("FAILED: 45 second run deadline reached")
        }
        deadline = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 45, execute: work)
        if role == .central {
            central = CBCentralManager(delegate: self, queue: nil)
        } else {
            peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
        }
    }

    func send() {
        guard role == .central, ready, expectedResponse == nil, received == 0 else { return }
        ready = false
        token = (0..<4).map { _ in UInt8.random(in: .min ... .max) }
        exchangeStarted = ProcessInfo.processInfo.systemUptime
        sendNext()
    }

    func sendNext() {
        guard let remote = remote, let characteristic = requestCharacteristic else {
            finish("FAILED: request characteristic missing")
            return
        }
        let sequence = UInt32(received)
        let sequenceBytes = (0..<4).map {
            UInt8(truncatingIfNeeded: sequence >> (24 - $0 * 8))
        }
        var bytes: [UInt8] = [0x4d, 0x4c, 1, 0]
        bytes.append(contentsOf: token)
        bytes.append(contentsOf: sequenceBytes)
        bytes.append(contentsOf: (0..<8).map { UInt8($0) })
        var reply = bytes
        reply[3] = 1
        for index in 12..<20 { reply[index] = UInt8(255 - (index - 12)) }
        expectedResponse = Data(reply)
        writing = true
        sent += 1
        report("SENDING")
        remote.writeValue(Data(bytes), for: characteristic, type: .withResponse)
    }

    func verifyResponse(_ value: Data) {
        guard value == expectedResponse else {
            finish("FAILED: unexpected or corrupt response")
            return
        }
        expectedResponse = nil
        received += 1
        if received == Self.packetCount {
            finish("PASSED: all synthetic responses verified")
        } else {
            sendNext()
        }
    }

    func report(_ message: String) {
        let now = ProcessInfo.processInfo.systemUptime
        let runMs = started == 0 ? 0 : Int((now - started) * 1000)
        let exchangeMs = exchangeStarted == 0 ? 0 : Int((now - exchangeStarted) * 1000)
        status = "\(message)\nSubmitted: \(sent)  Verified received: \(received)\n" +
            "Run: \(runMs) ms  Exchange: \(exchangeMs) ms"
    }

    func finish(_ message: String) {
        stopRadios()
        report(message)
    }

    func stop() { finish("STOPPED") }

    private func stopRadios() {
        deadline?.cancel()
        deadline = nil
        remote?.delegate = nil
        central?.delegate = nil
        if let central = central, central.state == .poweredOn {
            central.stopScan()
            if let remote = remote { central.cancelPeripheralConnection(remote) }
        }
        peripheralManager?.delegate = nil
        if let manager = peripheralManager, manager.state == .poweredOn {
            manager.stopAdvertising()
            manager.removeAllServices()
        }
        central = nil
        peripheralManager = nil
        remote = nil
        subscriber = nil
        requestCharacteristic = nil
        responseCharacteristic = nil
        pendingNotification = nil
        earlyResponse = nil
        expectedResponse = nil
        writing = false
        ready = false
        UIApplication.shared.isIdleTimerDisabled = false
    }
}
