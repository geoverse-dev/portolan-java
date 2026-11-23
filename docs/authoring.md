# Author catalogs in memory

`PortolanAuthoring` imports STAC dictionaries, edits catalog graphs, and creates a `WritePlan`.
Planning performs no filesystem, network, or object-storage writes.

```java
var graph = PortolanAuthoring.CatalogGraph.fromJson(input);
var edited = new PortolanAuthoring.Editor(graph)
    .setField("catalog.json", "description", descriptionNode)
    .build();
var plan = new PortolanAuthoring.Planner().plan(edited);
```

Plan operations contain a relative POSIX key, an intent, and UTF-8 content for PUT operations.
DELETE operations require an explicit list of previously managed metadata keys.
The planner does not inspect storage and does not delete geospatial asset data.

The planner adds the root catalog's `root` link and titles generated `child` and `item` links.
Generated `item` links use `application/geo+json`.
Pass `linkTitle` to `ManagedDocument` when an item has no `properties.title`.

Use `Editor.setDocumentation()` and `Editor.removeDocumentation()` for files such as
`README.md` and `AGENTS.md`. These methods use the same copy-on-write graph model.

Use `rashid` after materialization to validate the complete catalog and its assets.
SDK preflight checks protect plan construction but do not replace normative validation.
