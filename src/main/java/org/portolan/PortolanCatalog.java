package org.portolan;

import tools.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Objects;

/** A loaded Portolan catalog root. */
public final class PortolanCatalog extends PortolanDocument {
  private PortolanCatalog(ObjectNode data, URI href) {
    this(data, href, JsonSupport::readObject);
  }

  PortolanCatalog(ObjectNode data, URI href, StacDocumentReader reader) {
    super(data, href, reader);
  }

  public static PortolanCatalog open(Path source) {
    return open(source, StacReaderBackend.LIGHTWEIGHT);
  }

  public static PortolanCatalog open(Path source, StacReaderBackend backend) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(backend, "backend");
    Path path = Files.isDirectory(source) ? source.resolve("catalog.json") : source;
    URI href = path.toAbsolutePath().normalize().toUri();
    return StacReaderProviders.open(href, backend);
  }

  public static PortolanCatalog open(URI source) {
    return open(source, StacReaderBackend.LIGHTWEIGHT);
  }

  public static PortolanCatalog open(URI source, StacReaderBackend backend) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(backend, "backend");
    return StacReaderProviders.open(source, backend);
  }

  static PortolanCatalog openLightweight(URI source) {
    return new PortolanCatalog(JsonSupport.readObject(source), source);
  }

  public List<PortolanCollection> collections() {
    List<PortolanCollection> result = new ArrayList<>();
    collectCollections(data, href, reader, new HashSet<>(), result);
    return List.copyOf(result);
  }

  private static void collectCollections(
      ObjectNode data,
      URI href,
      StacDocumentReader reader,
      Set<URI> visited,
      List<PortolanCollection> result) {
    if (!visited.add(href)) {
      return;
    }
    PortolanCatalog current = new PortolanCatalog(data, href, reader);
    for (PortolanLink link : current.links()) {
      if (!"child".equals(link.rel())) {
        continue;
      }
      ObjectNode child = reader.read(link.href());
      String childType = JsonSupport.text(child, "type");
      if ("Collection".equals(childType)) {
        result.add(new PortolanCollection(child, link.href(), reader));
      } else if ("Catalog".equals(childType)) {
        collectCollections(child, link.href(), reader, visited, result);
      }
    }
  }
}
