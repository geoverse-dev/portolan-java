# Portolan Java

A lightweight Java implementation of the Portolan specification.

`portolan-java` provides JVM applications with a native API for reading, navigating, creating, validating, and reasoning about Portolan catalogs.

The project is intended to make Portolan a **language-independent ecosystem**, rather than one whose reference implementation is implicitly tied to Python or to a particular CLI.

## Motivation

Portolan is a specification and interoperability contract.

Applications should not need to invoke a Python CLI, run a sidecar process, or independently reimplement the Portolan specification simply because they are written in Java.

A Java application should be able to use Portolan naturally:

```java
PortolanCatalog catalog =
    PortolanCatalog.open(URI.create("https://example.com/catalog.json"));

for (PortolanCollection collection : catalog.collections()) {
    System.out.println(collection.id());

    for (PortolanAsset asset : collection.assets()) {
        System.out.println(asset.href());
        System.out.println(asset.mediaType());
    }
}
```

This project provides that capability.

## STAC reader implementations

The normal API uses the lightweight reader bundled in `portolan-java`; it remains the default and
runs on Java 17:

```java
PortolanCatalog catalog = PortolanCatalog.open(path);
```

An optional provider delegates STAC discovery and loading to Parquetry:

```java
PortolanCatalog catalog =
    PortolanCatalog.open(path, StacReaderBackend.PARQUETRY);
```

The optional `org.portolan:portolan-java-parquetry` artifact must be present at runtime. It currently
requires Java 25 with preview features enabled because that is what the pinned Parquetry version
requires. Merely using the core does not introduce Parquetry or raise the Java 17 baseline. See
[Parquetry reader](docs/parquetry-reader.md) for setup, runtime, and compatibility details.

## Tolerant and strict reading

The original `open` API remains tolerant and unchanged. Applications that need diagnostics can use
`inspect`, while applications that want to reject catalogs with metadata or structural errors can
use `openStrict`:

```java
PortolanCatalog tolerant = PortolanCatalog.open(uri);

PortolanReadResult inspected = PortolanCatalog.inspect(uri);
for (PortolanFinding finding : inspected.findings()) {
    System.out.printf("%s %s: %s%n",
        finding.severity(), finding.requirement(), finding.message());
}

try {
    PortolanCatalog strict = PortolanCatalog.openStrict(uri);
} catch (PortolanValidationException invalid) {
    PortolanReadResult result = invalid.result();
}
```

Strict reading currently implements the supported metadata-only Portolan v0.2.0 profile. It checks
JSON/STAC metadata, Portolan metadata, hierarchy, links, assets, providers, licenses, and bounding
boxes while traversing the catalog. The result explicitly exposes which scopes were checked and
which remain unchecked.

It does **not** inspect asset bytes or verify live hosting behavior. Those checks, and authoritative
full-catalog conformance, remain the responsibility of
[`rashid`](https://github.com/portolan-sdi/rashid). Therefore `strictlyValid()` means valid for the
documented Java strict-reading scope, not an unconditional Portolan compliance certification. See
[Strict reading](docs/strict-reading.md) for the complete contract.

## Scope

The expected API includes concepts such as:

```text
PortolanCatalog
PortolanCollection
PortolanItem
PortolanAsset
PortolanLink

CatalogReader
CatalogWriter
HrefResolver
Validator
ValidationResult
ConformanceChecker
```

Responsibilities include:

* reading Portolan/STAC structures;
* writing Portolan/STAC structures;
* catalog traversal;
* collection discovery;
* item and asset discovery;
* HREF resolution;
* Portolan metadata;
* Portolan extensions;
* validation;
* conformance;
* specification version handling;
* typed representation of supported asset types.

## What this library does not do

`portolan-java` does not implement the underlying geospatial formats.

For example:

```text
Portolan catalog
      │
      ├── identifies GeoParquet asset
      │       └── GeoParquet implementation reads data
      │
      ├── identifies COG asset
      │       └── COG/GDAL/GeoTools implementation reads data
      │
      └── identifies other assets
              └── appropriate implementation handles data
```

Consequently, the core should not require GeoTools, GeoServer, GDAL, or other large geospatial frameworks simply to understand a Portolan catalog.

## Relationship with `portolan-python`

`portolan-java` and `portolan-python` are sibling implementations:

```text
                    Portolan Specification
                      /             \
                     /               \
            portolan-python       portolan-java
                   │                   │
             Python ecosystem       JVM ecosystem
```

Neither implementation defines Portolan.

**The specification defines Portolan.**

Both libraries should share:

* conformance expectations;
* terminology;
* test fixtures where possible;
* canonical catalog examples;
* expected validation results;
* specification version behavior.

This allows cross-language tests such as:

```text
same catalog
    │
    ├── portolan-python → same semantic interpretation
    └── portolan-java   → same semantic interpretation
```

## Relationship with GeoServer

One of the initial consumers of this project is expected to be `geoserver-portolan`.

However, this project is **not a GeoServer library**.

The dependency direction is:

```text
portolan-java
      ↑
      │
geoserver-portolan
```

and never:

```text
portolan-java
      ↓
GeoServer
```

This separation makes the library reusable from any JVM application.

## Potential consumers

Examples include:

* GeoServer;
* GeoTools applications;
* JVM-based data pipelines;
* desktop GIS applications;
* Spark applications;
* catalog services;
* custom enterprise geospatial applications.

## Package structure

A possible initial organization is:

```text
org.portolan
├── model
│   ├── PortolanCatalog
│   ├── PortolanCollection
│   ├── PortolanItem
│   ├── PortolanAsset
│   └── PortolanLink
├── io
│   ├── CatalogReader
│   └── CatalogWriter
├── validation
│   ├── Validator
│   └── ValidationResult
├── conformance
│   └── ConformanceChecker
└── href
    └── HrefResolver
```

The actual package structure should remain deliberately small.

## Design principles

1. Portolan specification is the source of truth.
2. Native Java API with no Python runtime dependency.
3. No GeoServer dependency.
4. No GeoTools dependency unless eventually justified by an optional module.
5. No implementation of GeoParquet, COG, PMTiles, etc. in core.
6. Small dependency graph.
7. Stable API suitable for embedding.
8. Cross-language semantic compatibility with `portolan-python`.

The long-term objective is simple:

> A Java developer should be able to support Portolan by adding a normal Java dependency.

## Local documentation

- [Scope](docs/scope.md) defines what belongs in the Java core.
- [Examples](docs/examples.md) shows basic API usage.
- [Development](docs/development.md) lists Maven and Make targets.
- [Distribution](docs/distribution.md) explains releases and Maven repositories.
- [Parquetry reader](docs/parquetry-reader.md) explains the optional STAC backend.
- [Strict reading](docs/strict-reading.md) documents inspection, strict mode, and its validation scope.

---
