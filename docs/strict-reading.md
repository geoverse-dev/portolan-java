# Strict reading

`portolan-java` offers three additive reading modes. Existing callers keep the same behavior:

| API | Behavior |
| --- | --- |
| `PortolanCatalog.open(source)` | Reads tolerantly and preserves the original API contract. |
| `PortolanCatalog.inspect(source)` | Reads and returns a catalog, detected spec version, and diagnostics. |
| `PortolanCatalog.openStrict(source)` | Returns the catalog only when strict inspection has no errors; otherwise throws `PortolanValidationException`. |

All modes accept `Path` or `URI`. The overloads that take `StacReaderBackend` work with both the
default lightweight reader and any installed optional provider, including Parquetry.

## Result and diagnostics

`PortolanReadResult` contains:

- the readable `PortolanCatalog`, even when diagnostics contain errors;
- the detected `PortolanSpecVersion`;
- immutable `PortolanFinding` entries with severity, requirement identifier, JSON/document location,
  and message;
- checked and unchecked `PortolanValidationScope` sets.

This separation lets an application show useful content and explain non-conformance without forcing
all callers into exception-based control flow. `PortolanValidationException.result()` exposes the
same complete result when strict opening fails.

## Supported strict profile

The current implementation recognizes the versioned Portolan v0.2.0 schema URI and checks a
metadata-only profile while traversing catalogs, nested catalogs, collections, and linked items. The
checked scopes are:

- syntactically readable JSON structure;
- relevant STAC core metadata;
- Portolan metadata requirements;
- Portolan hierarchy and structural requirements.

Checks include schema/version declarations, required identity and descriptive fields, documentation
links, structural link metadata, licenses, providers, assets, item metadata, bounding boxes, cycles,
and unresolved linked documents. Findings use the corresponding Portolan requirement identifier
where applicable. A future/unknown Portolan schema is reported as unsupported instead of silently
being interpreted as v0.2.0.

## Deliberate boundary

Strict reading does not download or validate asset bytes and does not test live-hosting behavior.
Those scopes appear in `uncheckedScopes()`. It is a consumer-side safety and diagnostic API, not a
replacement for the authoritative [`rashid`](https://github.com/portolan-sdi/rashid) validator.

For release or publisher certification, run Rashid as the complete conformance check. For embedded
Java consumers that need predictable reading and actionable diagnostics, use `inspect` or
`openStrict`.
