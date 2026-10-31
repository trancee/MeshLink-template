# Template design review: Android 8 and iOS 15 BLE mesh

**Reviewed:** 2026-10-31  
**Status:** Proposal, not an accepted specification or implementation change  
**Target:** Android 8/API 26 and iOS 15 minimum; small, efficient P2P BLE mesh SDK

## Assessment and evidence

The template is over-specified before its hardest platform assumptions have
been proven. Preserve the shared Kotlin core, authenticated transport, bounded
streaming, and automatic routing, but redesign discovery, power policy, wire
framing, and key rotation before implementing more scaffolding.

This review covered [SPEC.md](../../SPEC.md), decision records,
reference/explanation documentation, normative wire/state contracts, and relevant
build configuration. Platform claims were checked against authoritative
Android/Apple documentation; the pinned crypto facade was checked against its
published sources. Graph evidence used Tier 2 verification, generation
`2026-10-31T14:20:30Z`; coverage checks reported no recorded issues, a best-effort
signal rather than proof of completeness.

No implementation or normative contract was changed during the review. Existing
worktree edits were preserved. This is a design review, not real-device
certification or a cryptographic implementation audit.

## Decisions that must change

| Priority | Current decision | Challenge and proposed change |
|---|---|---|
| Blocker | Android API 21/iOS 14 minimums | The requirement is Android 8/API 26 and iOS 15. Update the constitution, SPEC, README, reference docs, and Android modules to `minSdk = 26`. Explicitly configure and verify the produced iOS framework's deployment floor, not merely the consuming app's setting. |
| Blocker | L2CAP as the preferred baseline | Android's public LE CoC APIs start at API 29, not API 26. Android 8/9 must have a fully functional GATT-only path. Treat CoC as an optional optimization, never a minimum-platform performance assumption. |
| Blocker | iOS background discovery degrades safely through GATT | GATT metadata helps after discovery, not when discovery is unavailable. Apple places background service UUIDs in an overflow area discoverable only by iOS devices explicitly scanning for them. Android cannot rely on discovering a background iOS advertiser using the normal service filter. Document the directional limitation and favor background iOS acting as central toward advertising Android peers. |
| Blocker | Application-controlled scan/advertising/connection intervals | Several documented APIs do not exist or do something different. `CBAdvertisementIntervalKey`, `AdvertiseSettings.setInterval()`, and using `setNotifyValue()` to request connection intervals are incorrect. Replace exact radio promises with policy requests, observable capabilities, and unknown effective values. |
| Blocker | Crypto dependency directly supplies the required Noise primitives | The published `meshlink-crypto:0.1.1` public AEAD facade generates a random nonce, prepends it, and accepts no associated data. Noise needs caller-controlled counter nonces and handshake-hash AD. A supported nonce-and-AD API is required before Noise integration. Its provider interface alone does not supply a usable default implementation. |
| Blocker | Wire format is sufficiently specified | Scalar byte order conflicts across SPEC and the codec explanation. Handshake/session demultiplexing, encrypted-record counters/epochs, and snapshot pagination are insufficiently defined. Finish byte-exact contracts before implementation. |
| High | Arbitrary private 32-bit `MESH` UUID plus dynamic metadata UUID | This borrows Bluetooth's assigned-number namespace and depends on portable packing of an unusual advertisement. Use a genuinely custom 128-bit service UUID. Prove advertising/scanning behavior on both minimum platforms before retaining dynamic UUID metadata. |
| High | Secure Enclave/StrongBox support for the selected curves | iOS 15 Secure Enclave is not an Ed25519/X25519 key provider. Android availability is algorithm/provider-specific. Separate secure storage, native software computation, and hardware-isolated computation in the capability model. |
| High | Universal identical platform behavior | Preserve API shape and safety semantics, not fictitious identical radio behavior. Background discovery, execution opportunities, connection capacity, and latency differ materially. Expose those differences as capabilities/availability without leaking platform objects. |

Primary evidence:

- [Android LE CoC API](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#listenUsingL2capChannel())
- [Apple advertising API](https://developer.apple.com/documentation/corebluetooth/cbperipheralmanager/startadvertising(_:))
- [Apple background behavior](https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html)
- [Apple L2CAP publishing](https://developer.apple.com/documentation/corebluetooth/cbperipheralmanager/publishl2capchannel(withencryption:))
- [Apple Secure Enclave API](https://developer.apple.com/documentation/cryptokit/secureenclave)
- [Published crypto sources](https://repo.maven.apache.org/maven2/ch/trancee/meshlink/meshlink-crypto/0.1.1/meshlink-crypto-0.1.1-sources.jar)

**Product constraint:** Android 8/iOS 15 support is feasible; an always-discoverable,
always-relaying mixed-platform background mesh is not a guarantee these public
APIs can support.

## Architecture and protocol simplifications

### Discovery and connection topology

The discovery design spends substantial complexity on rotating hints before
demonstrating reliable cross-platform discovery.

Recommended baseline:

- One connectable custom service advertisement; authenticate metadata after connection.
- Move platform, power mode, and CoC capability out of mandatory advertising.
- Prefer an application/profile-specific service UUID to reduce unrelated connections. This is filtering, not authorization.
- Remove the separate FNV mesh hash if the service UUID already provides namespace filtering; retain authenticated `appHash` domain separation.
- Keep rotating hints only if device measurements show meaningful connection-work savings.

The FNV rationale is weak: `appHash` already requires SHA-256, and application
hashing happens infrequently. Avoiding one startup hash is not a meaningful
hot-path optimization.

**The missing architectural decision is neighbor selection.** Babel routes over
links that already exist; it does not build those links. Define how nodes select
neighbors under connection limits, avoid wasting links on redundant local
clusters, recover partitions, and reserve capacity for newcomers. Duplicate-link
arbitration alone does not solve topology formation.

Android API 26 does not guarantee peripheral advertising capability on every
device. Define capability-limited behavior or a typed unsupported result rather
than promising every supported phone can perform every mesh role.

### Transport and framing

Keep GATT correctness first, with optional CoC acceleration.

| Decision | Recommendation |
|---|---|
| GATT-only control even when CoC is healthy | Require control to work over GATT; benchmark whether keeping it permanently on GATT helps. Two bearer queues introduce ordering and scheduling complexity. |
| Write-with-response/indication for control | Reasonable conservative baseline. Measure setup latency and head-of-line blocking before adopting additional reliability mechanisms. |
| Manual L2CAP credit counts and 100 ms monitoring | Remove. Mobile socket/stream APIs hide protocol credit management. Implement application queue bounds, stream progress deadlines, and backpressure instead. |
| `chunkSize + 3` MTU calculation | Incorrect. Include all framing, routing fields, counters, and AEAD tags. A 512-byte chunk plus two 16-byte tags already exceeds a 514-byte ATT value, before other headers. |
| Single-fragment chunks wherever possible | Optimize for useful bytes per radio operation, not an invariant. At MTU 23, the protected record necessarily needs fragmentation. |
| 65,535-byte payload and 65,535-byte framed-record limits | Reconcile them: the codec adds four envelope bytes, so the largest codec payload cannot fit the declared bearer record. Nested encryption adds more overhead. |
| Large records with preemption only between records | Use smaller operational record bounds to limit memory and control starvation; specify paginated snapshots explicitly. |
| Disable CoC for the entire process after four failures | Prefer a cooldown and demand-driven reprobe. A long-running process should recover after transient interference or Bluetooth reset. |

Specify whether CoC requires OS bonding. Application-layer Noise and BLE pairing
are different trust mechanisms. Every accepted CoC must prove attachment to the
intended authenticated hop session; knowing a PSM is not authentication.

Relevant records:
[GATT framing](../decisions/transport/gatt-channel-and-framing.md) and
[MTU negotiation](../decisions/transport/mtu-negotiation.md).

### Security, identity, and rotation

Keep standard Noise XX/IK, no application early data, explicit pin mismatch
handling, fresh ephemeral keys, device-only key storage, and no plaintext fallback.

Change or challenge:

- Delete the hand-written Noise examples in [the crypto API reference](../reference/meshlink-crypto-api.md). They use `Random.Default` for secret generation, mix curve roles, and do not implement Noise's token/chaining rules. A simplified-illustration warning is insufficient for copyable crypto code.
- Stop rotating the installation signing root every three days by default. Installation-lifetime dual-signed chains create growing recovery traffic and storage requirements. Consider a stable identity signing root with replaceable certified DH keys. Root compromise needs an explicitly defined recovery/reset policy.
- If signing-root rotation remains mandatory, specify bounded chain transfer, verification work, storage growth, and recovery framing. Eighteen 234-byte proofs already exceed the 4 KiB pre-auth record bound: roughly 54 days at the default interval.
- Remove propagation-deadline timers unless the protocol provides evidence of remote receipt. Sent within one second and installed remotely within one second are different guarantees.
- Define compromise recovery. A continuity signature made with a compromised old key cannot distinguish the legitimate owner from its attacker.
- Keep fresh-DH renewal where needed, but challenge the prepare/commit/ack/drain machinery against a simpler bounded session replacement. Time expiry does not by itself justify that entire state machine.
- Correct constant-time claims: hardware isolation is not blanket proof of timing behavior; Wycheproof proves neither constant-time behavior nor Noise correctness.
- Size replay windows from maximum supported reordering, not minutes of traffic. A 64-record replay window alongside 256 in-flight chunks and bearer migration needs specific tests. Increasing a bitmap costs very little compared with payload buffers.
- Clarify that E2E encryption, not hop encryption, prevents relays reading application content. TOFU establishes continuity, not real-world identity or authorization.

### Routing

Babel-inspired feasibility and starvation recovery are defensible. Do not replace
them with flooding merely because flooding looks smaller: radio traffic can
dominate code savings.

However:

- Start with a simpler metric: hop cost plus measured failures/queue pressure. Mandatory RSSI polling and quadratic penalties are not proven to improve delivery.
- RSSI observations are not equally available for every central/peripheral role. Specify missing/stale observations.
- Separate neighbor capacity, route capacity, and network size. A capacity of 256 routes does not prove 256 useful mesh nodes.
- Remove max-observed capacity/convergence tables unless linked to reproducible measurements.
- Cache verified destination statements and reference identity bindings instead of retransmitting the complete binding in every route update.
- Keep signed origin statements only with an explicit threat-model benefit: they protect origin assertions, not honest path costs or relay forwarding.
- Retain per-neighbor digests/split horizon, but define snapshot paging, revisions on incremental updates, missing-page handling, and atomic installation.
- Bound synchronization and sequence-advancement work globally as well as per destination. Immediate cooldown exceptions must not become unlimited work.

Before selecting proactive versus on-demand routing, compare them on the same
virtual/device workloads: control airtime, discovery-to-delivery latency,
battery, churn, and partitions.

## Transfers, memory, power, and public API

| Area | Proposed improvement |
|---|---|
| Small messages | A mandatory manifest/accept/chunk/ACK sequence is expensive for tiny messages. Add a bounded authenticated single-record message fast path while retaining duplicate suppression. |
| Large transfers | Preserve host-owned random-access sources/sinks and process-local transfer state. Do not add durable SDK payload storage without an actual requirement. |
| SACK | Keep selective retransmission, but distinguish bitmap capacity from active window size. Grow the active window with measured RTT/throughput; do not immediately fill 256 slots. |
| Congestion | Add global/per-peer byte budgets and fair scheduling. SACK and timeout backoff are not complete congestion control. |
| Completion | Distinguish chunk receipt from successful sink finalization. Sender `Completed` should mean the receiver's `complete()` succeeded, not merely that all bytes were buffered. |
| Duplicate delivery | Specify tombstone capacity, retention, eviction, and process-death semantics. In-memory tombstones cannot guarantee installation-lifetime at-most-once delivery. |
| Source/sink ownership | State exact-read, immutable-source, cancellation, finalization, and error rules consistently. Avoid forced extra copies where internal ownership is clear. |
| Dynamic scoreboard | Remove from large-transfer state. `TransferSession` still contains a whole-transfer `Scoreboard`; this contradicts window-bounded memory. Its current constructor also narrows `UInt` before sizing. |
| Immutable updates | `Scoreboard.markReceived()` copies the bitmap. There is no structural sharing for free. Keep mutable single-owner hot-path state; publish bounded snapshots. |
| Identity representation | `@JvmInline` over `Pair<ULong, ULong>` is not allocation-free primitive storage. Remove that claim and benchmark direct representations before changing the API. |
| Diagnostics | Reduce the default 1,000-event buffer based on measured event sizes. Avoid per-chunk DEBUG production unless explicitly enabled; HIGH power should not mean mandatory allocation-heavy diagnostics. |
| Message flow | Define behavior with no collectors and slow collectors. A hot non-replaying flow cannot simultaneously guarantee application receipt without an explicit admission/delivery contract. |
| API state | Separate direct-link connectivity from routed reachability. Remove unreachable `UNINITIALIZED` public state if construction always returns `CONFIGURED`. |
| Radio lease | Keep ownership inside the library, but describe it as coordination of MeshLink-managed resources, not exclusive ownership of the physical radio against other apps/libraries. |
| Settings | Remove overridden or unimplemented knobs. Keep app namespace, operational power preference, background opt-in, resource limits, and observability. Provide a genuinely usable Swift construction path. |

### Power policy needs a rewrite

Three user-facing modes are reasonable. Their current parameter mapping is not.

- MEDIUM mapped to `SCAN_MODE_OPPORTUNISTIC` breaks autonomous discovery. Android defines this mode as listening to scans started by others. Use a scanning mode that actually scans.
- Do not implement small duty cycles through rapid scan restarts; respect scan-start limits and background scheduling.
- Larger efficiently packed chunks can consume less energy per delivered byte. Chunk size should follow bearer capacity and measured efficiency, not automatically shrink in LOW mode.
- Separate retry deadline from priority. The current priority-dependent TTLs need workload evidence, not an assumed relationship.
- Remove the EU 300 ms advertisement floor/70% scan ceiling unless backed by applicable regulatory clauses and a correct compliance model. These constants do not establish compliance.
- Expose requested policy, platform acceptance, and measured/unknown effective behavior separately.

Evidence:
[Power decision](../decisions/power/power-mode-behavior.md),
[power reference](../reference/power.md), and
[Android opportunistic scanning](https://developer.android.com/reference/android/bluetooth/le/ScanSettings#SCAN_MODE_OPPORTUNISTIC).

## Documentation and quality rules

Existing contradictions create false confidence:

| Conflict | Required correction |
|---|---|
| SPEC inner scalar values are big-endian; codec explanation says little-endian | Choose one exact scalar encoding and publish golden vectors. |
| Codec explanation alternately mentions varints and prohibits them | Remove speculative format features; specify only the actual format. |
| Wire text is prohibited, but failure results transmit strings | Use bounded numeric wire reasons; keep redacted explanatory text local. |
| `TransferSource.total` and sink `accept/write/close/cancel` versus ADR `size/write/complete/abort` | Reconcile SPEC, source/API, and ADR immediately. |
| Incoming offers: three per peer versus two | Establish one normative limit and accounting rule. |
| Trust reference uses `EncryptedSharedPreferences`; persistence ADR uses atomic files | Remove the obsolete storage guidance and extra dependency implication. |
| Transfer reference suggests surviving process death by persisting offsets | Distinguish host-owned file persistence from unsupported SDK transfer resumption. |
| Peer-lifecycle explanation requires new sequence progression on reconnect; routing spec forbids reconnect increments | Correct the explanation. |
| CI-enforced device throughput/battery budgets | Reviewed CI compiles the iOS target; it does not establish those real-device measurements. Label targets versus measured gates honestly. |

The existing validator failed during this review:
`./scripts/validate-specs.sh` exited because it expects `14. Settings`, while
the current SPEC says `14. Configuration Model`. It also emitted
unresolved-anchor warnings. Its settings check only tests whether two files
mention `PowerMode`; that is not default/schema agreement.

Proposed governance amendments:

- Replace exactly-two-dependencies policy with measured shipped footprint, justified dependencies, and transitive dependency auditing. The build already declares SKIE annotations in addition to coroutines/crypto.
- Track R8-minified Android app size delta and linked/stripped iOS app size delta, including dependencies. Artifact size alone is misleading.
- Keep real BLE tests on hardware, but restore iOS simulator non-radio testing and distribution support. The crypto dependency's missing simulator variant is a dependency gap, not a reason JVM testing proves Native behavior.
- Treat 100% coverage as one metric, not protocol proof. Require fault injection, parser fuzzing, independent Noise vectors, cross-target execution, and bounded-resource tests.
- Replace arbitrary universal performance figures with workload/device-specific baselines, confidence intervals, and allocation measurements.
- Pin compatible toolchains and CI tooling reproducibly. Always-latest-stable policy conflicts with existing alpha pins and dynamically installed tooling.
- Consolidate normative values instead of requiring the same change across SPEC, YAML, ADR tables, and reference facades. Generate projections; let ADRs explain decisions.
- Preserve module separation. Test tooling and Compose do not belong in the shipped SDK.
- Remove PQ hybrid, compression, MLS, speculative codec types, and premature extension machinery from v1 scope. The PQ matrix's +184 bytes cannot represent a complete ML-KEM-768 public-key/ciphertext exchange; label it synthetic evidence if that is what it is.

## Recommended minimum viable scope and proof order

The lean baseline is Android API 26/iOS 15, foreground-first mixed-platform
operation, capability-aware background continuation, GATT-first authenticated
duplex transport, optional CoC, standard Noise with a compatible primitive API,
bounded messages and streaming transfers, a measured neighbor-selection/routing
policy, and immutable public observations over mutable single-owner internals.

Recommended implementation/proof sequence:

1. **Platform spike:** Bidirectional Android 8/iOS 15 traffic, MTU 23, simultaneous roles, screen lock, background discovery asymmetry, permissions, and Bluetooth reset.
2. **Security/wire contract:** Correct primitive adapter, independent Noise vectors, exact record layouts, authenticated session attachment, length bounds, and fuzzing.
3. **Three-node mesh:** A/B/C routing, topology formation, link loss, alternate routes, queue fairness, and expiry.
4. **Streaming/resource proof:** Sink-finalization ACK, duplication, bearer changes, process death, memory ceilings, and allocations per delivered byte.
5. **Release evidence:** Minimum-OS execution, stripped size deltas, GATT and CoC throughput, actual energy measurements, and Swift integration.

## Validation limits and skills

The spec validator was executed; platform facts and the crypto facade were
checked against primary sources. No Gradle suite or real-device tests were run
for the analysis, so size, throughput, battery, and minimum-OS runtime
compatibility remain unverified.

**Skills used:** Codebase Memory, BLE stack architecture, Android BLE, Core
Bluetooth, Noise Protocol Framework, and Diataxis.
