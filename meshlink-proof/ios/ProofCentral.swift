import CoreBluetooth

extension BleProof: CBCentralManagerDelegate, CBPeripheralDelegate {
    func centralManagerDidUpdateState(_ manager: CBCentralManager) {
        guard manager === central else { return }
        if manager.state == .poweredOn {
            manager.scanForPeripherals(withServices: [Self.serviceUUID])
        } else if manager.state != .unknown && manager.state != .resetting {
            finish("FAILED: Bluetooth unavailable: \(manager.state.rawValue)")
        }
    }

    func centralManager(
        _ manager: CBCentralManager, didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any], rssi RSSI: NSNumber
    ) {
        guard manager === central, remote == nil else { return }
        manager.stopScan()
        remote = peripheral
        peripheral.delegate = self
        manager.connect(peripheral)
    }

    func centralManager(_ manager: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard manager === central, peripheral === remote else { return }
        peripheral.discoverServices([Self.serviceUUID])
    }

    func centralManager(
        _ manager: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?
    ) {
        guard manager === central, peripheral === remote else { return }
        finish("FAILED: connect: \(String(describing: error))")
    }

    func centralManager(
        _ manager: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?
    ) {
        guard manager === central, peripheral === remote else { return }
        finish("FAILED: disconnected: \(String(describing: error))")
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard peripheral === remote else { return }
        guard error == nil,
              let service = peripheral.services?.first(where: { $0.uuid == Self.serviceUUID }) else {
            finish("FAILED: proof service missing: \(String(describing: error))")
            return
        }
        peripheral.discoverCharacteristics([Self.requestUUID, Self.responseUUID], for: service)
    }

    func peripheral(
        _ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?
    ) {
        guard peripheral === remote else { return }
        let request = service.characteristics?.first { $0.uuid == Self.requestUUID }
        let response = service.characteristics?.first { $0.uuid == Self.responseUUID }
        guard error == nil, let request = request, let response = response,
              request.properties.contains(.write), response.properties.contains(.notify),
              peripheral.maximumWriteValueLength(for: .withResponse) >= 20 else {
            finish("FAILED: incompatible proof characteristics")
            return
        }
        requestCharacteristic = request
        peripheral.setNotifyValue(true, for: response)
    }

    func peripheral(
        _ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        guard peripheral === remote, characteristic.uuid == Self.responseUUID else { return }
        guard error == nil, characteristic.isNotifying else {
            finish("FAILED: subscription rejected")
            return
        }
        ready = true
        report("READY")
    }

    func peripheral(
        _ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?
    ) {
        guard peripheral === remote, characteristic.uuid == Self.requestUUID else { return }
        writing = false
        guard error == nil else {
            finish("FAILED: request write: \(String(describing: error))")
            return
        }
        if let response = earlyResponse {
            earlyResponse = nil
            verifyResponse(response)
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?
    ) {
        guard peripheral === remote, characteristic.uuid == Self.responseUUID else { return }
        guard error == nil, let value = characteristic.value, value.count == 20 else {
            finish("FAILED: invalid notification")
            return
        }
        if writing {
            guard earlyResponse == nil else {
                finish("FAILED: more than one pending response")
                return
            }
            earlyResponse = value
        } else {
            verifyResponse(value)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didModifyServices invalidatedServices: [CBService]) {
        guard peripheral === remote else { return }
        if invalidatedServices.contains(where: { $0.uuid == Self.serviceUUID }) {
            finish("FAILED: proof service invalidated")
        }
    }
}
