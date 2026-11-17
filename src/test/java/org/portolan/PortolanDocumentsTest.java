package org.portolan;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortolanDocumentsTest {
  @TempDir Path temp;

  @Test void opensEachCoreTypeAndPreservesMetadata() throws Exception {
    Path document = temp.resolve("collection.json");
    Files.writeString(document, """
        {"type":"Collection","id":"c","title":"Café","description":null,
         "stac_version":"1.1.0","stac_extensions":["https://example/ext"],
         "links":[],"assets":{"data":{"href":"../a b.parquet","x:test":null}}}
        """);
    PortolanDocument loaded = PortolanDocuments.open(document);
    assertInstanceOf(PortolanCollection.class, loaded);
    assertEquals("Café", loaded.title());
    assertNull(loaded.description());
    assertEquals("1.1.0", loaded.stacVersion());
    assertEquals("../a b.parquet", loaded.assets().get(0).originalHref());
    assertTrue(loaded.assets().get(0).href().isAbsolute());
    assertTrue(loaded.data().path("assets").path("data").has("x:test"));
  }

  @Test void reportsStableErrors() throws Exception {
    PortolanReadException missing = assertThrows(PortolanReadException.class,
        () -> PortolanDocuments.open(temp.resolve("missing.json")));
    assertEquals(PortolanReadException.Code.NOT_FOUND, missing.code());
    Path malformed = temp.resolve("bad.json");
    Files.writeString(malformed, "{");
    PortolanReadException invalid = assertThrows(PortolanReadException.class,
        () -> PortolanDocuments.open(malformed));
    assertEquals(PortolanReadException.Code.INVALID_JSON, invalid.code());
  }

  @Test void dispatchesCatalogAndFeatureAndDirectChildren() throws Exception {
    Files.writeString(temp.resolve("child.json"),
        "{\"type\":\"Feature\",\"id\":\"i\",\"links\":[],\"assets\":{}}");
    Files.writeString(temp.resolve("catalog.json"), """
        {"type":"Catalog","id":"root","stac_extensions":["one",null,2],
         "links":[{"rel":"self","href":"catalog.json"},{"rel":"child","href":"child.json"}]}
        """);
    PortolanDocument catalog = PortolanDocuments.open(temp);
    assertInstanceOf(PortolanCatalog.class, catalog);
    assertEquals(List.of("one"), catalog.stacExtensions());
    assertInstanceOf(PortolanItem.class, catalog.children().get(0));
    assertEquals(List.of(), catalog.children().get(0).stacExtensions());
  }

  @Test void rejectsUnknownDocumentTypesAndProtocols() throws Exception {
    Path unknown = temp.resolve("unknown.json");
    Files.writeString(unknown, "{\"type\":\"Thing\",\"id\":\"x\"}");
    PortolanReadException type = assertThrows(PortolanReadException.class,
        () -> PortolanDocuments.open(unknown));
    assertEquals(PortolanReadException.Code.INVALID_REQUEST, type.code());
    PortolanReadException protocol = assertThrows(PortolanReadException.class,
        () -> PortolanDocuments.open(URI.create("s3://bucket/catalog.json")));
    assertEquals(PortolanReadException.Code.UNSUPPORTED_PROTOCOL, protocol.code());
    assertThrows(NullPointerException.class, () -> PortolanDocuments.open((URI) null));
  }
}
