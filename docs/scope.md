# Scope

`portolan-java` is the JVM counterpart of `portolan-python`. It gives Java
applications a native API for Portolan catalog semantics.

The library reads Portolan/STAC metadata and authors Portolan 0.3.0 catalog graphs.
It does not implement GeoParquet, COG, PMTiles, or GeoServer behavior.

## Belongs here

- Opening a Portolan catalog from a `Path` or `URI`.
- Navigating child collections, item links, assets, and links.
- Resolving HREFs relative to the document that declares them.
- Classifying assets from metadata.
- Validating metadata structure.
- Reading registry exports and catalog snapshots.
- Editing catalog graphs through copy-on-write operations.
- Producing deterministic PUT and explicit metadata DELETE plans.

## Belongs outside

- Reading feature rows from GeoParquet.
- Reading raster pixels from COG.
- Publishing resources to GeoServer.
- Converting data into cloud-native formats.
- Managing server processes or GIS projects.
- Applying plans to a filesystem or object store.

Consumers can pair this library with GeoTools, GeoServer, Spark, or other JVM
tools when they need data processing.
