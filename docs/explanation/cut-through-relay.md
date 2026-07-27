# Cut-Through Relay in MeshLink

## What Is Cut-Through Relay?

In a traditional store-and-forward relay, a middle node receives an entire
message, stores it in memory, and then forwards it to the next hop.
This adds latency proportional to message size — a 256 KB message must be
fully received before forwarding can begin.

 Cut-through relay pipelines the forwarding process:

1. Relay receives the **first chunk** of the message from the previous hop
2. Relay **immediately decrypts** the hop-layer encryption
3. Relay **re-encrypts** with the next-hop's session key
4. Relay **forwards** the re-encrypted chunk to the next hop
5. Steps 1-4 repeat for each subsequent chunk

Relays do NOT wait for the entire message to arrive before forwarding the first byte.

## How MeshLink Implements Cut-Through Relay

### At the Transport Layer

Each hop maintains a relay buffer for the current transfer session:

```text
Relay node R between Origin (O) and Destination (D):

O --[chunk 0]--> R --[chunk 0 re-encrypted]--> D
O --[chunk 1]--> R --[chunk 1 re-encrypted]--> D
...
```

The relay socket receives a chunk, decrypts it (removing the outer AEAD
envelope), re-encrypts it for the next hop, and immediately forwards it.

### At the Transfer Layer

The `TransferCoordinator` on the relay node:

1. Tracks which chunks it has received (for local retransmission)
2. Forwards each chunk as soon as it can be re-encrypted
3. Maintains a relay buffer (`ceil(totalChunks / 8)` bytes scoreboard) so it know
   which chunks to retransmit if the outgoing link drops

### Why Cut-Through?

| Benefit | Explanation |
|---------|-------------|
| **Lower latency** | First byte reaches destination after 1 hop delay + 1 hop processing, not N hop delays |
| **Lower memory** | Relay buffers only the current chunk, not the entire message |
| **Better throughput** | Pipeline utilization: while chunk N is being forwarded, chunk N+1 can already be received |

## Security Properties

Cut-through relay does NOT weaken security because:

1. **Relays cannot read E2E payload** — they only decrypt/re-encrypt the
   hop-layer AEAD envelope, not the E2E payload inside
2. **E2E encryption is end-to-end** — the origin and destination share the
   IX handshake keys; relays don't participate in E2E key exchange
3. **Hop-layer encryption is per-link** — each hop has its own Noise session keys

## Failure Handling

If a relay node crashes or disconnects mid-transfer:

1. The next node detects the transport failure (`TRANSPORT_CLOSED` or
   `MAX_RETRIES_EXCEEDED`)
2. The destination receives an incomplete `TransferAck` with partial scoreboard
3. The destination marks missing chunks and the sender retransmits them
4. The scoreboard mechanism ensures only missing chunks are re-sent, even
   after a relay failure

## Performance Characteristics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Relay processing delay per chunk | <1 ms | JVM benchmark |
| Cut-through throughput | Same as direct link | Limited by weakest link in path |
| Relay buffer memory | ≤ scoreboard size per session | ~125 bytes for 1000-chunk transfer |

## Related

- [Transfer Layer Spec](../../SPEC.md#9-transfer-layer)
- [RFC 2018 (TCP SACK)](../../docs/rfcs/transfer/rfc2018.txt) — selective ACK semantics
- [Wire Format: TransferChunk](../../specs/wire_frames.yaml)
