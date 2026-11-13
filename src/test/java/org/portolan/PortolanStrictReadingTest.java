package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortolanStrictReadingTest {
  @TempDir Path directory;

  @Test
  void strictReadingAcceptsSupportedMetadataAndReportsUncheckedRashidScopes() throws Exception {
    writeValidCatalog();

    PortolanReadResult result = PortolanCatalog.inspect(directory);
    PortolanCatalog strict = PortolanCatalog.openStrict(directory);

    assertTrue(result.strictlyValid());
    assertTrue(result.findings().isEmpty());
    assertEquals(PortolanSpecVersion.V0_2_0, result.specVersion());
    assertEquals("root", strict.id());
    assertEquals(
        Set.of(PortolanValidationScope.ASSET_BYTES, PortolanValidationScope.LIVE_HOSTING),
        result.uncheckedScopes());
  }

  @Test
  void lenientOpenRemainsCompatibleWhileInspectAndStrictExposeMissingConformance() throws Exception {
    Files.writeString(
        directory.resolve("catalog.json"),
        """
        {"type":"Catalog","stac_version":"1.1.0","id":"legacy","description":"Readable"}
        """);

    PortolanCatalog lenient = PortolanCatalog.open(directory);
    PortolanReadResult inspected = PortolanCatalog.inspect(directory, StacReaderBackend.LIGHTWEIGHT);
    PortolanValidationException exception =
        assertThrows(
            PortolanValidationException.class,
            () -> PortolanCatalog.openStrict(directory, StacReaderBackend.LIGHTWEIGHT));

    assertEquals("legacy", lenient.id());
    assertFalse(inspected.strictlyValid());
    assertTrue(ids(inspected).contains("PORTO-CORE-006"));
    assertTrue(ids(inspected).contains("PORTO-CORE-037"));
    assertTrue(ids(inspected).contains("PORTO-CORE-061"));
    assertTrue(ids(inspected).contains("PORTO-CORE-062"));
    assertSame(exception.result().catalog().getClass(), lenient.getClass());
    assertTrue(exception.getMessage().contains("error(s)"));
  }

  @Test
  void reportsUnsupportedVersionsWithoutPretendingTheyWereValidated() throws Exception {
    Files.writeString(
        directory.resolve("catalog.json"),
        """
        {"type":"Catalog","stac_version":"1.1.0","id":"future","title":"Future catalog",
         "description":"Future", "stac_extensions":[
          "https://schemas.portolan-sdi.org/portolan/v9.0.0/schema.json"],
         "links":[
          {"rel":"root","href":"catalog.json","type":"application/json"},
          {"rel":"agents","href":"AGENTS.md","type":"text/markdown"},
          {"rel":"describedby","href":"README.md","type":"text/markdown"}]}
        """);

    var catalogUri = directory.resolve("catalog.json").toUri();
    PortolanReadResult result = PortolanCatalog.inspect(catalogUri);

    assertFalse(result.strictlyValid());
    assertEquals("9.0.0", result.specVersion().version());
    assertFalse(result.specVersion().supported());
    assertTrue(ids(result).contains("PORTOLAN-JAVA-001"));
    assertThrows(PortolanValidationException.class, () -> PortolanCatalog.openStrict(catalogUri));
  }

  @Test
  void reportsMetadataHierarchyAssetsProvidersLinksAndBboxByOfficialRequirementId()
      throws Exception {
    Files.writeString(
        directory.resolve("catalog.json"),
        """
        {"type":"Catalog","stac_version":"1.0.0","id":"root","title":"Root",
         "description":"Root", "stac_extensions":[
          "https://schemas.portolan-sdi.org/portolan/v0.2.0/schema.json"],
         "assets":{"bad":{"href":"http://example.test/data.parquet"}},
         "links":[
          {"rel":"root","href":"catalog.json"},
          {"rel":"agents","href":"AGENTS.md","type":"text/plain"},
          {"rel":"child","href":"collection.json","type":"application/json"}]}
        """);
    Files.writeString(
        directory.resolve("collection.json"),
        """
        {"type":"Collection","stac_version":"1.1.0","id":"roads","title":"Roads",
         "description":"Roads", "license":"proprietary", "stac_extensions":[
          "https://schemas.portolan-sdi.org/portolan/v0.1.2/schema.json"],
         "providers":[{"name":"No host","roles":["processor"]}],
         "extent":{"spatial":{"bbox":[[-181,20,181,10]]},"temporal":{"interval":[[null,null]]}},
         "links":[
          {"rel":"root","href":"catalog.json","type":"application/json"},
          {"rel":"parent","href":"catalog.json","type":"text/plain"},
          {"rel":"agents","href":"AGENTS.md","type":"text/markdown"},
          {"rel":"describedby","href":"README.md","type":"text/markdown"},
          {"rel":"child","href":"nested.json","type":"application/json","title":"Nested"},
          {"rel":"item","href":"missing-item.json","type":"application/geo+json"}]}
        """);
    Files.writeString(
        directory.resolve("nested.json"),
        """
        {"type":"Collection","stac_version":"1.1.0","id":"nested","title":"Nested",
         "description":"Nested","links":[]}
        """);

    PortolanReadResult result = PortolanCatalog.inspect(directory);
    Set<String> ids = ids(result);

    assertFalse(result.strictlyValid());
    assertTrue(ids.contains("PORTO-CORE-001"));
    assertTrue(ids.contains("PORTO-CORE-004"));
    assertTrue(ids.contains("PORTO-CORE-010"));
    assertTrue(ids.contains("PORTO-CORE-023"));
    assertTrue(ids.contains("PORTO-CORE-025"));
    assertTrue(ids.contains("PORTO-CORE-033"));
    assertTrue(ids.contains("PORTO-CORE-039"));
    assertTrue(ids.contains("PORTO-CORE-041"));
    assertTrue(ids.contains("PORTO-CORE-047"));
    assertTrue(ids.contains("PORTO-CORE-060"));
    assertTrue(ids.contains("PORTO-CORE-062"));
    assertTrue(ids.contains("PORTO-CORE-035"));
    assertTrue(result.findings().stream().anyMatch(finding -> finding.severity() == PortolanSeverity.WARNING));
  }

  @Test
  void validatesItemMetadataAndHostContact() throws Exception {
    writeValidCatalog();
    String collection = Files.readString(directory.resolve("collection.json"))
        .replace("\"url\":\"https://example.test\"", "\"name\":\"missing contact\"");
    Files.writeString(directory.resolve("collection.json"), collection);
    String item = Files.readString(directory.resolve("item.json"))
        .replace("\"datetime\":\"2026-01-01T00:00:00Z\"", "")
        .replace("[-1,-2,3,4]", "[1,2,3]");
    Files.writeString(directory.resolve("item.json"), item);

    PortolanReadResult result = PortolanCatalog.inspect(directory);

    assertTrue(ids(result).contains("PORTO-CORE-019"));
    assertTrue(ids(result).contains("PORTO-CORE-041"));
    assertTrue(ids(result).contains("PORTO-CORE-051"));
  }

  @Test
  void reportsAdditionalMalformedMetadataWithoutLosingTheReadableCatalog() throws Exception {
    String profile = PortolanSpecVersion.V0_2_0.schemaUri();
    Files.writeString(
        directory.resolve("catalog.json"),
        """
        {"type":"Wrong","stac_version":"1.1.0","id":"root","title":"Root",
         "description":"Root","stac_extensions":["%s"],"links":[
          {"rel":"root","href":"catalog.json","type":"application/json"},
          {"rel":"agents","href":"AGENTS.md","type":"text/markdown"},
          {"rel":"describedby","href":"README.md","type":"text/markdown"},
          {"rel":"child","href":"collection.json","type":"application/json","title":"Collection"}]}
        """.formatted(profile));
    Files.writeString(
        directory.resolve("collection.json"),
        """
        {"type":"Collection","stac_version":"1.1.0","id":"bad","title":"Bad",
         "description":"Bad","stac_extensions":["%s"],"license":"other","providers":[],
         "extent":{"spatial":{"bbox":[[1,2,3]]},"temporal":{"interval":[[null,null]]}},
         "assets":[],"links":[
          {"rel":"root","href":"catalog.json","type":"application/json"},
          {"rel":"parent","href":"catalog.json","type":"application/json"},
          {"rel":"agents","href":"AGENTS.md","type":"text/markdown"},
          {"rel":"describedby","href":"README.md","type":"text/markdown"},
          {"rel":"child","href":"wrong-child.json","type":"application/json","title":"Wrong"},
          {"rel":"item","href":"item.json","type":"application/geo+json","title":"Item"}]}
        """.formatted(profile));
    Files.writeString(directory.resolve("wrong-child.json"), "{\"type\":\"Feature\"}");
    Files.writeString(
        directory.resolve("item.json"),
        """
        {"type":"Item","stac_version":"1.0.0","id":"item","bbox":["x",0,1,2],
         "assets":{"broken":"not-an-object"},"links":[]}
        """);

    PortolanReadResult result = PortolanCatalog.inspect(directory);
    Set<String> found = ids(result);

    assertEquals("root", result.catalog().id());
    assertTrue(found.contains("PORTO-CORE-001"));
    assertTrue(found.contains("PORTO-CORE-002"));
    assertTrue(found.contains("PORTO-CORE-019"));
    assertTrue(found.contains("PORTO-CORE-022"));
    assertTrue(found.contains("PORTO-CORE-032"));
    assertTrue(found.contains("PORTO-CORE-041"));
    assertTrue(found.contains("PORTO-CORE-046"));
    assertTrue(found.contains("PORTO-CORE-059"));
  }

  @Test
  void supportsNestedCatalogCyclesIntervalsSixDimensionalBboxAndHostEmail() throws Exception {
    String profile = PortolanSpecVersion.V0_2_0.schemaUri();
    Files.writeString(
        directory.resolve("catalog.json"),
        catalog(profile, "root", "sub/catalog.json", "Subcatalog"));
    Files.createDirectories(directory.resolve("sub"));
    Files.writeString(
        directory.resolve("sub/catalog.json"),
        """
        {"type":"Catalog","stac_version":"1.1.0","id":"sub","title":"Subcatalog",
         "description":"Subcatalog","stac_extensions":[7,"%s"],"links":[
          {"rel":"root","href":"../catalog.json","type":"application/json"},
          {"rel":"parent","href":"../catalog.json","type":"application/json"},
          {"rel":"agents","href":"AGENTS.md","type":"text/markdown"},
          {"rel":"describedby","href":"README.md","type":"text/markdown"},
          {"rel":"child","href":"../catalog.json","type":"application/json","title":"Root cycle"},
          {"rel":"child","href":"collection.json","type":"application/json","title":"Collection"}]}
        """.formatted(profile));
    Files.writeString(
        directory.resolve("sub/collection.json"),
        """
        {"type":"Collection","stac_version":"1.1.0","id":"events","title":"Events",
         "description":"Events","stac_extensions":["%s"],"license":"other",
         "providers":[{"name":"Producer","roles":["producer"]},
          {"name":"Host","roles":["host"],"email":"host@example.test"}],
         "extent":{"spatial":{"bbox":[[-1,-2,0,3,4,100]]},"temporal":{"interval":[[null,null]]}},
         "links":[
          {"rel":"root","href":"../catalog.json","type":"application/json"},
          {"rel":"parent","href":"catalog.json","type":"application/json"},
          {"rel":"agents","href":"AGENTS.md","type":"text/markdown"},
          {"rel":"describedby","href":"README.md","type":"text/markdown"},
          {"rel":"license","href":"LICENSE","type":"text/plain"},
          {"rel":"item","href":"item.json","type":"application/geo+json","title":"Event"}]}
        """.formatted(profile));
    Files.writeString(
        directory.resolve("sub/item.json"),
        """
        {"type":"Feature","stac_version":"1.1.0","id":"event","collection":"events",
         "geometry":null,"bbox":[-1,-2,0,3,4,100],
         "properties":{"start_datetime":"2020-01-01T00:00:00Z","end_datetime":"2021-01-01T00:00:00Z"},
         "links":[{"rel":"root","href":"../catalog.json","type":"application/json"},
          {"rel":"parent","href":"collection.json","type":"application/json"},
          {"rel":"collection","href":"collection.json","type":"application/json"}]}
        """);

    PortolanReadResult result = PortolanCatalog.inspect(directory);

    assertTrue(result.strictlyValid(), () -> result.findings().toString());
  }

  @Test
  void versionAndDiagnosticValueObjectsDefensivelyValidateInputs() {
    assertEquals(
        PortolanSpecVersion.V0_2_0,
        PortolanSpecVersion.fromSchema(PortolanSpecVersion.V0_2_0.schemaUri()));
    assertEquals(null, PortolanSpecVersion.fromSchema("https://example.test/schema.json"));
    assertThrows(NullPointerException.class, () -> new PortolanSpecVersion(null, "schema", false));
    assertThrows(NullPointerException.class, () -> new PortolanSpecVersion("1", null, false));
    assertThrows(
        NullPointerException.class,
        () -> new PortolanFinding(null, PortolanSeverity.ERROR, "path", "message"));
    assertThrows(
        NullPointerException.class,
        () -> new PortolanFinding("ID", null, "path", "message"));
  }

  private void writeValidCatalog() throws Exception {
    String profile = PortolanSpecVersion.V0_2_0.schemaUri();
    Files.writeString(
        directory.resolve("catalog.json"),
        """
        {"type":"Catalog","stac_version":"1.1.0","id":"root","title":"Example catalog",
         "description":"Example", "stac_extensions":["%s"], "links":[
          {"rel":"root","href":"catalog.json","type":"application/json"},
          {"rel":"agents","href":"AGENTS.md","type":"text/markdown"},
          {"rel":"describedby","href":"README.md","type":"text/markdown"},
          {"rel":"child","href":"collection.json","type":"application/json","title":"Road collection"}]}
        """.formatted(profile));
    Files.writeString(
        directory.resolve("collection.json"),
        """
        {"type":"Collection","stac_version":"1.1.0","id":"roads","title":"Road collection",
         "description":"Roads", "stac_extensions":["%s"], "license":"CC-BY-4.0",
         "providers":[
          {"name":"Producer","roles":["producer"]},
          {"name":"Host","roles":["host"],"url":"https://example.test"}],
         "extent":{"spatial":{"bbox":[[-1,-2,3,4]]},"temporal":{"interval":[[null,null]]}},
         "assets":{"data":{"href":"https://example.test/roads.parquet",
          "type":"application/vnd.apache.parquet","roles":["data"]}},
         "links":[
          {"rel":"root","href":"catalog.json","type":"application/json"},
          {"rel":"parent","href":"catalog.json","type":"application/json"},
          {"rel":"agents","href":"AGENTS.md","type":"text/markdown"},
          {"rel":"describedby","href":"README.md","type":"text/markdown"},
          {"rel":"item","href":"item.json","type":"application/geo+json","title":"Road item"}]}
        """.formatted(profile));
    Files.writeString(
        directory.resolve("item.json"),
        """
        {"type":"Feature","stac_version":"1.1.0","id":"road-1","collection":"roads",
         "geometry":null,"bbox":[-1,-2,3,4],"properties":{"datetime":"2026-01-01T00:00:00Z"},
         "assets":{"data":{"href":"https://example.test/road.parquet",
          "type":"application/vnd.apache.parquet","roles":["data"]}},
         "links":[
          {"rel":"root","href":"catalog.json","type":"application/json"},
          {"rel":"parent","href":"collection.json","type":"application/json"},
          {"rel":"collection","href":"collection.json","type":"application/json"}]}
        """);
  }

  private static String catalog(String profile, String id, String child, String childTitle) {
    return """
        {"type":"Catalog","stac_version":"1.1.0","id":"%s","title":"Catalog",
         "description":"Catalog","stac_extensions":["%s"],"links":[
          {"rel":"root","href":"catalog.json","type":"application/json"},
          {"rel":"agents","href":"AGENTS.md","type":"text/markdown"},
          {"rel":"describedby","href":"README.md","type":"text/markdown"},
          {"rel":"child","href":"%s","type":"application/json","title":"%s"}]}
        """.formatted(id, profile, child, childTitle);
  }

  private static Set<String> ids(PortolanReadResult result) {
    return result.findings().stream().map(PortolanFinding::requirementId).collect(java.util.stream.Collectors.toSet());
  }
}
