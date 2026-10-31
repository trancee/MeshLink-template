import CoreBluetooth

extension BleProof: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ manager: CBPeripheralManager) {
        guard manager === peripheralManager else { return }
        if manager.state == .poweredOn {
            let request = CBMutableCharacteristic(
                type: Self.requestUUID, properties: [.write], value: nil, permissions: [.writeable]
            )
            let response = CBMutableCharacteristic(
                type: Self.responseUUID, properties: [.notify], value: nil, permissions: []
            )
            responseCharacteristic = response
            let service = CBMutableService(type: Self.serviceUUID, primary: true)
            service.characteristics = [request, response]
            manager.add(service)
        } else if manager.state != .unknown && manager.state != .resetting {
            finish("FAILED: Bluetooth unavailable: \(manager.state.rawValue)")
        }
    }

    func peripheralManager(
        _ manager: CBPeripheralManager, didAdd service: CBService, error: Error?
    ) {
        guard manager === peripheralManager else { return }
        guard error == nil else {
            finish("FAILED: service registration: \(String(describing: error))")
            return
        }
        manager.startAdvertising([CBAdvertisementDataServiceUUIDsKey: [Self.serviceUUID]])
        report("Advertising; waiting for subscription")
    }

    func peripheralManagerDidStartAdvertising(_ manager: CBPeripheralManager, error: Error?) {
        guard manager === peripheralManager else { return }
        if let error = error { finish("FAILED: advertising: \(error)") }
    }

    func peripheralManager(
        _ manager: CBPeripheralManager, central: CBCentral,
        didSubscribeTo characteristic: CBCharacteristic
    ) {
        guard manager === peripheralManager, characteristic.uuid == Self.responseUUID else { return }
        guard subscriber == nil || subscriber?.identifier == central.identifier else { return }
        guard central.maximumUpdateValueLength >= 20 else {
            finish("FAILED: notification capacity below 20 bytes")
            return
        }
        subscriber = central
        ready = true
        manager.stopAdvertising()
        report("READY")
    }

    func peripheralManager(
        _ manager: CBPeripheralManager, central: CBCentral,
        didUnsubscribeFrom characteristic: CBCharacteristic
    ) {
        guard manager === peripheralManager, subscriber?.identifier == central.identifier else { return }
        finish("Peer unsubscribed; restart peripheral. Check central result.")
    }

    func peripheralManager(_ manager: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        guard manager === peripheralManager else { return }
        manager.respond(to: request, withResult: .readNotPermitted)
    }

    func peripheralManager(
        _ manager: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]
    ) {
        guard manager === peripheralManager, let request = requests.first else { return }
        guard requests.count == 1, ready, request.characteristic.uuid == Self.requestUUID,
              request.central.identifier == subscriber?.identifier,
              request.offset == 0, let value = request.value, value.count == 20,
              pendingNotification == nil, received < Self.packetCount else {
            manager.respond(to: request, withResult: .requestNotSupported)
            return
        }
        let bytes = [UInt8](value)
        let sequence = bytes[8..<12].reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
        let requestToken = Array(bytes[4..<8])
        let valid = Array(bytes[0..<4]) == [0x4d, 0x4c, 1, 0] &&
            sequence == UInt32(received) &&
            Array(bytes[12..<20]) == [0, 1, 2, 3, 4, 5, 6, 7] &&
            (received == 0 || token == requestToken)
        guard valid else {
            manager.respond(to: request, withResult: .unlikelyError)
            finish("FAILED: unexpected or corrupt request")
            return
        }
        token = requestToken
        manager.respond(to: request, withResult: .success)
        var response = bytes
        response[3] = 1
        for index in 12..<20 { response[index] = UInt8(255 - (index - 12)) }
        received += 1
        pendingNotification = Data(response)
        flushNotification()
    }

    func peripheralManagerIsReady(toUpdateSubscribers manager: CBPeripheralManager) {
        guard manager === peripheralManager else { return }
        flushNotification()
    }

    func flushNotification() {
        guard let manager = peripheralManager, let subscriber = subscriber,
              let characteristic = responseCharacteristic, let value = pendingNotification else { return }
        if manager.updateValue(value, for: characteristic, onSubscribedCentrals: [subscriber]) {
            pendingNotification = nil
            sent += 1
            report(sent == Self.packetCount ?
                "100 replies submitted; check central for PASSED" : "Serving synthetic requests")
        } else {
            report("Backpressure: waiting for notification capacity")
        }
    }
}
