# Development

The project uses Maven and targets Java 17.

## Setup

```bash
make setup
```

This downloads dependencies and compiles the project without running the full
verification phase.

## Test and coverage

```bash
make test
make coverage
```

`make coverage` runs `mvn verify`. JaCoCo writes the HTML report to
`target/site/jacoco/index.html` and enforces the configured 85% line and branch thresholds.

Every pull request runs this verification on Java 17. A second CI job builds and tests the optional
Parquetry provider on Java 25 with preview features.

## Build and install

```bash
make build
make install
```

`make build` creates the project artifact under `target/`. `make install`
installs the snapshot into the local Maven repository.

## Clean local artifacts

```bash
make clean
```

This removes Maven build output under `target/`.

## Optional Parquetry provider

Use a Java 25 JDK and point `PARQUETRY_HOME` at a checkout of the pinned Parquetry revision described
in [Parquetry reader](parquetry-reader.md):

```bash
make parquetry-install PARQUETRY_HOME=/path/to/parquetry
make parquetry-test
```

Run applications that select this provider with `java --enable-preview`. These targets are separate
so normal Java 17 development never downloads or compiles Parquetry.
