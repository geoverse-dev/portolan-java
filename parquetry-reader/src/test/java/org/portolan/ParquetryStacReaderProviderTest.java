package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParquetryStacReaderProviderTest {
  @TempDir Path tempDir;

  @Test
  void serviceProviderReadsCatalogWithoutLosingPortolanMetadata() throws Exception {
    write(
        "catalog.json",
        """
        {
          "type": "Catalog", "stac_version": "1.1.0", "id": "demo",
          "links": [{"rel": "child", "href": "collections/roads.json"}],
          "portolan:custom": "preserved"
        }
        """);
    write(
        "collections/roads.json",
        """
        {
          "type": "Collection", "stac_version": "1.1.0", "id": "roads",
          "description": "Roads", "license": "CC-BY-4.0",
          "extent": {
            "spatial": {"bbox": [[-71, -35, -70, -34]]},
            "temporal": {"interval": [[null, null]]}
          },
          "links": [{"rel": "item", "href": "items/road-1.json"}]
        }
        """);
    write(
        "collections/items/road-1.json",
        """
        {
          "type": "Feature", "stac_version": "1.1.0", "id": "road-1",
          "geometry": null, "bbox": [-71, -35, -70, -34],
          "properties": {"datetime": null}, "links": [],
          "assets": {
            "primary_data": {
              "href": "road-1.parquet",
              "type": "application/vnd.apache.parquet",
              "roles": ["data"],
              "description": "Exact metadata must survive the Parquetry facade"
            }
          }
        }
        """);

    PortolanCatalog catalog =
        PortolanCatalog.open(tempDir.resolve("catalog.json"), StacReaderBackend.PARQUETRY);
    PortolanAsset asset = catalog.collections().get(0).items().get(0).assets().get(0);

    assertEquals("demo", catalog.id());
    assertEquals("preserved", catalog.data().get("portolan:custom").asText());
    assertEquals("primary_data", asset.key());
    assertEquals("Exact metadata must survive the Parquetry facade", asset.description());
  }

  private void write(String relativePath, String json) throws Exception {
    Path target = tempDir.resolve(relativePath);
    Files.createDirectories(target.getParent());
    Files.writeString(target, json);
  }
}
