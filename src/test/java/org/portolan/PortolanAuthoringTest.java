package org.portolan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class PortolanAuthoringTest {
  @Test void plansNestedDocumentsDocumentationPrefixAndDeletion() {
    PortolanAuthoring.CatalogGraph graph = graph("""
        {"spec_version":"0.3.0","documents":[
          {"key":"catalog.json","document":{"type":"Catalog","stac_version":"1.1.0","id":"demo","title":"Demo","description":"Catalog"}},
          {"key":"roads/collection.json","parent_key":"catalog.json","document":{"type":"Collection","stac_version":"1.1.0","id":"roads","title":"Roads","description":"Roads","license":"CC-BY-4.0","extent":{}}},
          {"key":"roads/item.json","parent_key":"roads/collection.json","relation":"item","document":{"type":"Feature","stac_version":"1.1.0","id":"road-1","collection":"roads","properties":{},"geometry":null}}
        ],"documentation":[
          {"owner_key":"catalog.json","key":"README.md","rel":"describedby","content":"# Demo\\n"}
        ]}
        """);

    PortolanAuthoring.WritePlan plan =
        new PortolanAuthoring.Planner().plan(graph, List.of("old.json"), "export");

    assertEquals(List.of("export/README.md", "export/catalog.json",
        "export/roads/collection.json", "export/roads/item.json", "old.json"),
        plan.operations().stream().map(PortolanAuthoring.Operation::key).toList());
    assertEquals("delete", plan.operations().get(4).kind());
    String root = text(plan.operations().get(1));
    assertTrue(root.contains("roads/collection.json"));
    assertTrue(text(plan.operations().get(2)).contains("\"rel\":\"item\""));
    assertArrayEquals("# Demo\n".getBytes(StandardCharsets.UTF_8), plan.operations().get(0).content());
    assertArrayEquals(plan.operations().get(0).content(), plan.operations().get(0).content());
  }

  @Test void editorIsCopyOnWriteAndHandlesDocumentsAndAssets() {
    PortolanAuthoring.CatalogGraph original = graph("""
        {"spec_version":"0.3.0","documents":[
          {"key":"catalog.json","document":{"type":"Catalog","stac_version":"1.1.0","id":"demo","title":"Demo","description":"Before"}},
          {"key":"old/collection.json","parent_key":"catalog.json","document":{"type":"Collection","stac_version":"1.1.0","id":"old","title":"Old","description":"Old","assets":{"old":{"href":"old.parquet"}}}}
        ]}
        """);
    ObjectNode added = object("""
        {"type":"Collection","stac_version":"1.1.0","id":"new","title":"New","description":"New"}
        """);
    PortolanAuthoring.Editor editor = new PortolanAuthoring.Editor(original)
        .setField("catalog.json", "description", JsonSupport.MAPPER.getNodeFactory().textNode("After"))
        .removeAsset("old/collection.json", "old")
        .setAsset("old/collection.json", "new", object("{\"href\":\"new.parquet\"}"))
        .addDocument(new PortolanAuthoring.ManagedDocument(
            "new/collection.json", added, "catalog.json", null))
        .removeDocument("old/collection.json");

    PortolanAuthoring.CatalogGraph edited = editor.build();
    assertEquals("Before", JsonSupport.text(original.documents().get(0).document(), "description"));
    assertEquals("After", JsonSupport.text(edited.documents().get(0).document(), "description"));
    assertEquals(List.of("catalog.json", "new/collection.json"),
        edited.documents().stream().map(PortolanAuthoring.ManagedDocument::key).toList());
  }

  @Test void normalizesLinksAndPreservesJsonValues() {
    PortolanAuthoring.CatalogGraph graph = graph("""
        {"spec_version":"0.3.0","documents":[{"key":"catalog.json","document":{
          "type":"Catalog","stac_version":"1.1.0","id":"demo","title":"Córdoba","description":"Catalog",
          "x-values":{"null":null,"boolean":true,"integer":1,"decimal":1.5},
          "links":[{"rel":"describedby","href":"README.md"},{"rel":"describedby","href":"README.md"}]
        }}]}
        """);
    PortolanAuthoring.WritePlan first = new PortolanAuthoring.Planner().plan(graph);
    PortolanAuthoring.WritePlan second = new PortolanAuthoring.Planner().plan(graph);
    assertEquals(text(first.operations().get(0)), text(second.operations().get(0)));
    assertEquals(1, occurrences(text(first.operations().get(0)), "describedby"));
    assertEquals(2, graph.documents().get(0).document().get("links").size());
  }

  @Test void graphParserRejectsMalformedEnvelopes() {
    assertCode("INVALID_REQUEST", () -> PortolanAuthoring.CatalogGraph.fromJson(null));
    assertCode("INVALID_REQUEST", () -> graph("{}"));
    assertCode("INVALID_REQUEST", () -> graph("{\"spec_version\":\"0.3.0\",\"documents\":[7]}"));
    assertCode("INVALID_REQUEST", () -> graph("{\"spec_version\":\"0.3.0\",\"documents\":[],\"documentation\":{}}"));
    assertCode("INVALID_REQUEST", () -> graph("""
        {"spec_version":"0.3.0","documents":[],"documentation":[{"owner_key":"x"}]}
        """));
  }

  @Test void preflightRejectsUnsupportedInvalidAndIncompleteGraphs() {
    assertCode("UNSUPPORTED_SPEC_VERSION", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.2.0","documents":[]}
        """)));
    assertCode("DUPLICATE_OUTPUT_KEY", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.3.0","documents":[
          {"key":"catalog.json","document":{}},{"key":"catalog.json","document":{}}
        ]}
        """)));
    assertCode("INVALID_OUTPUT_PATH", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.3.0","documents":[{"key":"../catalog.json","document":{}}]}
        """)));
    assertCode("MISSING_REQUIRED_METADATA", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.3.0","documents":[{"key":"catalog.json","document":{"type":"Catalog","stac_version":"1.1.0","id":"x","description":"missing title"}}]}
        """)));
  }

  @Test void rejectsBrokenOwnershipRelationsLinksAndPlannerArguments() {
    PortolanAuthoring.CatalogGraph root = validRoot();
    assertCode("INVALID_OUTPUT_PATH", () -> new PortolanAuthoring.Planner().plan(root, List.of(), "../x"));
    assertCode("INVALID_OUTPUT_PATH", () -> new PortolanAuthoring.Planner().plan(root, List.of("../x"), ""));
    assertCode("WRITE_ERROR", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.3.0","documents":[{"key":"catalog.json","document":{"type":"Catalog","stac_version":"1.1.0","id":"x","title":"X","description":"X","links":{}}}]}
        """)));
    assertCode("WRITE_ERROR", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.3.0","documents":[{"key":"catalog.json","document":{"type":"Catalog","stac_version":"1.1.0","id":"x","title":"X","description":"X","links":[{"rel":"child","href":7}]}}]}
        """)));
    assertCode("MISSING_REQUIRED_METADATA", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.3.0","documents":[
          {"key":"catalog.json","document":{"type":"Catalog","stac_version":"1.1.0","id":"x","title":"X","description":"X"}},
          {"key":"x.json","parent_key":"missing.json","document":{"type":"Catalog","stac_version":"1.1.0","id":"y","title":"Y","description":"Y"}}
        ]}
        """)));
  }

  @Test void editorRejectsInvalidMutations() {
    PortolanAuthoring.Editor editor = new PortolanAuthoring.Editor(validRoot());
    assertCode("INVALID_REQUEST", () -> editor.setField(
        "missing", "x", JsonSupport.MAPPER.getNodeFactory().nullNode()));
    assertCode("MISSING_REQUIRED_METADATA", () -> editor.removeDocument("catalog.json"));
    assertCode("INVALID_REQUEST", () -> editor.removeDocument("missing.json"));
    assertCode("DUPLICATE_OUTPUT_KEY", () -> editor.addDocument(
        new PortolanAuthoring.ManagedDocument("catalog.json", object("{}"), null, null)));
    assertCode("INVALID_REQUEST", () -> editor.removeAsset("catalog.json", "missing"));
    editor.setField("catalog.json", "assets", JsonSupport.MAPPER.getNodeFactory().textNode("bad"));
    assertCode("WRITE_ERROR", () -> editor.setAsset("catalog.json", "x", object("{}")));
  }

  @Test void coversBoundaryValidationAndRecordDefaults() {
    assertEquals(0, new PortolanAuthoring.Operation("delete", "x.json", null).content().length);
    assertEquals("text/markdown",
        new PortolanAuthoring.Documentation("catalog.json", "README.md", "describedby", "x", null)
            .mediaType());
    assertCode("INVALID_REQUEST", () -> graph("""
        {"spec_version":"0.3.0","documents":[{"document":{}}]}
        """));
    assertCode("INVALID_OUTPUT_PATH", () -> new PortolanAuthoring.Planner().plan(
        validRoot(), List.of("bad\\key"), ""));
    assertCode("INVALID_REQUEST", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.3.0","documents":[
          {"key":"catalog.json","document":{"type":"Catalog","stac_version":"1.1.0","id":"x","title":"X","description":"X"}},
          {"key":"x.json","parent_key":"catalog.json","relation":"alternate","document":{"type":"Catalog","stac_version":"1.1.0","id":"y","title":"Y","description":"Y"}}
        ]}
        """)));
    assertCode("MISSING_REQUIRED_METADATA", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.3.0","documents":[{"key":"catalog.json","document":{"type":"Unknown","stac_version":"1.1.0","id":"x","title":"X","description":"X"}}]}
        """)));
    assertCode("WRITE_ERROR", () -> new PortolanAuthoring.Planner().plan(graph("""
        {"spec_version":"0.3.0","documents":[{"key":"catalog.json","document":{"type":"Catalog","stac_version":"1.1.0","id":"x","title":"X","description":"X","links":[7]}}]}
        """)));
  }

  private static PortolanAuthoring.CatalogGraph validRoot() {
    return graph("""
        {"spec_version":"0.3.0","documents":[{"key":"catalog.json","document":{
          "type":"Catalog","stac_version":"1.1.0","id":"demo","title":"Demo","description":"Catalog"
        }}]}
        """);
  }

  private static PortolanAuthoring.CatalogGraph graph(String json) {
    return PortolanAuthoring.CatalogGraph.fromJson(JsonSupport.MAPPER.readTree(json));
  }

  private static ObjectNode object(String json) {
    return (ObjectNode) JsonSupport.MAPPER.readTree(json);
  }

  private static String text(PortolanAuthoring.Operation operation) {
    return new String(operation.content(), StandardCharsets.UTF_8);
  }

  private static int occurrences(String value, String token) {
    return (value.length() - value.replace(token, "").length()) / token.length();
  }

  private static void assertCode(String code, Runnable action) {
    PortolanAuthoring.AuthoringException error =
        assertThrows(PortolanAuthoring.AuthoringException.class, action::run);
    assertEquals(code, error.code());
    assertFalse(error.getMessage().isBlank());
  }
}
