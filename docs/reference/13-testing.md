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

## 13.2 iOS Proof Testing (Security-Critical)

iOS proof harness is planned under `meshlink-proof/ios/` for real-device validation (simulator cannot validate BLE). Requires physical device testing for:

- `IosCryptoProviderTest`: Verify Security framework + Secure Enclave key usage (iOS 14+)
- `CoreBluetoothThroughputTest`: Verify 15-20ms floor per BLE references
- `IosBackgroundTransferTest`: Verify background mode handling during transfers

## 13.3 Link-Layer Handshake Testing (XX + IK)

- `NoiseXXHandshakeTest`: Verify XX establishes bidirectional link keys with mutual TOFU pinning
- `NoiseIKReconnectTest`: Verify IK reconnect succeeds when both peers hold pinned keys
- `NoiseIKFallbackTest`: Verify IK is used after TOFU, XX is used for first contact
- `NoiseIK0RTTTest`: Verify 0-RTT data can be sent after IK message 1
- `NoiseIKFailClosedTest`: Verify IK fails closed on key mismatch or malformed input

## 13.4 NX Fallback Testing

- `NXFallbackPublicKeyVerifyTest`: Verify full public key mismatch causes rejection
- `NXFallbackRateLimitTest`: Verify 3rd attempt succeeds, 4th fails
- `NXFallbackTimeoutTest`: Verify 10s timeout expires correctly
- `NXFallbackReplayTest`: Verify nonce replay is rejected

## 13.5 Key Rotation Testing

- `KeyRotationAnnounceTest`: Verify signature verification and key adoption
- `KeyRotationSeqnoResetTest`: Verify seqno resets to 1, not preserved
- `KeyRotationPropagationTest`: Verify gossip reaches mesh within deadlines
- `KeyRotationRollbackTest`: Verify old key still accepted for active sessions
- `WireCompatTest`: Verify KeyRotationAnnouncement round-trips correctly

## 13.6 Virtual Mesh Harness

Multi-node scenarios exercised without physical hardware:

- Reconnect churn scenarios
- Digest-mismatch resolution
- Routing convergence tests
- Cross-platform compatibility verification

## 13.7 Wire Compatibility Testing

- Hex test vectors in `commonTest/resources/wire-compat/`
- Forward-compatibility checks
- Malformed-input validation
- **Cross-platform CI job** (`.github/workflows/wire-compat.yml`):
  - Builds `:meshlink` for `androidArm64`, `iosArm64` (no simulator)
  - Runs shared test suite `WireCompatibilityTestSuite` on each target
  - Encodes all frame types using each platform's implementation
  - Decodes all vectors using each platform's implementation
  - **Asserts byte-for-byte equality** of encoded output across all targets
  - Fails if any platform produces different bytes for the same logical frame
  - Runs on macOS runner (required for iOS targets)
  - Scheduled on every PR and nightly

## 13.8 Acceptance Criteria Per Layer

1. **Data Model / Trust**: Wire vectors, malformed input rejection
2. **Discovery / Advertisement**: Single-packet format, PeerFingerprint matching
3. **Security Contract**: Wycheproof vectors, fail-closed on all edge cases
4. **Routing Control**: Convergence under virtual harness, seqno correctness
5. **Chunked Transfer**: Dynamic bitfield SACK semantics, cut-through relay, retry bounds
6. **Power Policy**: Tier-to-parameter mapping, EU clamping observable
7. **Public API**: Identical Android/iOS surface, lifecycle events
