package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StacReaderFacadeTest {
  @TempDir Path tempDir;

  @Test
  void lightweightReaderRemainsTheDefault() throws Exception {
    Path catalog = tempDir.resolve("catalog.json");
    Files.writeString(catalog, "{\"type\":\"Catalog\",\"id\":\"demo\",\"links\":[]}");

    assertEquals("demo", PortolanCatalog.open(catalog).id());
    assertEquals("demo", PortolanCatalog.open(catalog, StacReaderBackend.LIGHTWEIGHT).id());
  }

  @Test
  void optionalReaderReportsAUsefulErrorWhenItsArtifactIsAbsent() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                PortolanCatalog.open(
                    tempDir.resolve("catalog.json"), StacReaderBackend.PARQUETRY));

    assertTrue(failure.getMessage().contains("not installed"));
  }
}
