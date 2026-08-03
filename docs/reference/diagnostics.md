# Diagnostics & Events

> **Specification**: [SPEC.md §11](../../SPEC.md#diagnostics--events)  
> **Design rationale**: [Diagnostic Flow Delivery](../decisions/diagnostics/flow-delivery.md), [Public API and Lifecycle](../decisions/api/public-api-and-lifecycle.md)  
> **Machine-readable**: [specs/catalogs/diagnostic-events.yaml](../../specs/catalogs/diagnostic-events.yaml), [specs/codecs/enums.yaml](../../specs/codecs/enums.yaml)

## Platform-Specific Notes

### Android

- Diagnostic events mirrored to Logcat when `DiagnosticsSettings.emitLog = true`
- Log level mapping: `DEBUG`→`Log.d`, `INFO`→`Log.i`, `WARN`→`Log.w`, `ERROR`→`Log.e`
- No PII, keys, or payloads in logs — redaction enforced at emission point

### iOS

- Diagnostic events mirrored to unified logging (`os_log`) when `DiagnosticsSettings.emitLog = true`
- Log level mapping: `DEBUG`→`debug`, `INFO`→`info`, `WARN`→`default`, `ERROR`→`error`
- Uses `os_log_type_t` for efficient filtering in Console.app

## Quick Links

- [SPEC.md §11 — Full diagnostics spec](../../SPEC.md#diagnostics--events)
- [Diagnostic Flow Delivery ADR](../decisions/diagnostics/flow-delivery.md)
- [Public API and Lifecycle ADR](../decisions/api/public-api-and-lifecycle.md)
- [Diagnostic Events Spec](../../specs/catalogs/diagnostic-events.yaml)
- [Enums Spec](../../specs/codecs/enums.yaml)
