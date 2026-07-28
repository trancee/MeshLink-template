# Testing & Verification

> Source: [SPEC.md §13](../../SPEC.md#13-testing--verification)

## 13.1 Test Suite Structure

| Layer | Location | Coverage |
|-------|----------|----------|
| Unit/JVM | `commonTest` | Full coverage |
| Host/Android | `androidHostTest` | Crypto fallback validation |
| Device/Android | `meshlink-proof/android/` | Real BLE behavior |
| Device/iOS | `meshlink-proof/ios/` | Real BLE behavior, platform crypto |
| Reference app | `meshlink-reference` | Public API consumption only |

## 13.2 iOS Proof Testing (Planned)

iOS proof harness is planned under `meshlink-proof/ios/` for real-device validation (simulator cannot validate BLE). Requires physical device testing for:

- Security framework + Secure Enclave key usage (iOS 14+)
- CoreBluetooth throughput verification
- Background mode handling during transfers

## 13.3 Handshake Testing (Planned)

Planned test coverage for Noise handshake patterns:

- XX: Establishes bidirectional link keys with mutual TOFU pinning
- IK: Reconnect succeeds when both peers hold pinned keys; 0-RTT data after message 1
- IX: E2E handshake when destination key known
- NX: Fallback when destination key unknown; full public key verification; rate limiting; timeout; replay protection

## 13.4 Key Rotation Testing (Planned)

- KeyRotationAnnouncement signature verification and key adoption
- Seqno resets to 1 on rotation
- Propagation deadlines within mesh
- Grace period acceptance for active sessions
- Wire compatibility round-trips

## 13.5 Virtual Mesh Harness (Planned)

Multi-node scenarios exercised without physical hardware:

- Reconnect churn scenarios
- Digest-mismatch resolution
- Routing convergence tests
- Cross-platform compatibility verification

## 13.6 Wire Compatibility Testing (Planned)

- Hex test vectors in `commonTest/resources/wire-compat/`
- Forward-compatibility checks
- Malformed-input validation
- Cross-platform CI job for byte-for-byte equality across targets

## 13.7 Acceptance Criteria Per Layer

1. **Data Model / Trust**: Wire vectors, malformed input rejection
2. **Discovery / Advertisement**: Single-packet format, PeerFingerprint matching
3. **Security Contract**: Wycheproof vectors, fail-closed on all edge cases
4. **Routing Control**: Convergence under virtual harness, seqno correctness
5. **Chunked Transfer**: Dynamic bitfield SACK semantics, cut-through relay, retry bounds
6. **Power Policy**: mode-to-parameter mapping, EU clamping observable
7. **Public API**: Identical Android/iOS surface, lifecycle events
