# Optional Parquetry STAC reader

`portolan-java` has two STAC reader choices:

| Backend | Default | Runtime | Dependency |
| --- | --- | --- | --- |
| `LIGHTWEIGHT` | yes | Java 17+ | core only |
| `PARQUETRY` | no | Java 25 with `--enable-preview` | optional provider and Parquetry |

The public Portolan model stays the same. The Parquetry provider uses Parquetry to discover and parse
the full STAC document tree, while retaining the original JSON documents so Portolan extensions,
asset keys, descriptions, and other fields not represented by Parquetry's typed model are preserved.
It is discovered with Java `ServiceLoader`; there is no direct Parquetry dependency in the core.

## Current compatibility pin

Until Parquetry publishes a stable artifact, CI builds this exact upstream revision:

```text
repository: https://github.com/tileverse-io/parquetry.git
revision:   876687bc865fb3fc0526bd3c7d69d030db6a7d44
artifact:   io.tileverse.parquetry:parquetry-stac:1.0-SNAPSHOT
```

This pin makes pull-request verification reproducible. Updating it is an explicit compatibility
change: update the workflow and this document together, then run both reader test suites.

## Build locally

Install Java 25, make it the active JDK, and check out the pinned revision. Then run:

```bash
make parquetry-install PARQUETRY_HOME=/path/to/parquetry
make parquetry-test
```

The first target installs the required Parquetry modules and the Java 17 Portolan core in the local
Maven repository. The second builds and tests `org.portolan:portolan-java-parquetry`.
The pinned upstream revision currently has an unrelated cloud integration test-compilation mismatch,
so its bootstrap uses `-Dmaven.test.skip=true`; the Portolan provider tests are not skipped.

## Use

Add both artifacts to the application (the provider is intentionally not pulled by core):

```xml
<dependency>
  <groupId>org.portolan</groupId>
  <artifactId>portolan-java</artifactId>
  <version>0.1.4-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>org.portolan</groupId>
  <artifactId>portolan-java-parquetry</artifactId>
  <version>0.1.4-SNAPSHOT</version>
</dependency>
```

Select it explicitly:

```java
PortolanCatalog catalog =
    PortolanCatalog.open(path, StacReaderBackend.PARQUETRY);
```

Start the JVM with preview enabled:

```bash
java --enable-preview -jar application.jar
```

If the provider artifact is absent, selecting `PARQUETRY` fails immediately with a descriptive
error. Calling `PortolanCatalog.open(path)` always selects `LIGHTWEIGHT`, so installing the optional
artifact never changes existing behavior implicitly.

## Operational considerations

- Parquetry eagerly materializes the tree in this adapter so the returned Portolan model can be used
  after Tileverse Storage is closed. Item documents are fetched concurrently by Parquetry.
- The adapter limits child-catalog recursion to 128 levels to reject malformed cycles.
- File, HTTP, S3, Azure, and GCS support depends on the Tileverse Storage providers available through
  the pinned Parquetry dependency graph and their normal credential configuration.
- Java 25 preview is a constraint of the current Parquetry revision, not of Portolan core. A future
  Parquetry release without that constraint can lower the optional provider's runtime requirement.
