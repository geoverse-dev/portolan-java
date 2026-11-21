package org.portolan;

import tools.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A loaded STAC Collection within a Portolan catalog. */
public final class PortolanCollection extends PortolanDocument {
  PortolanCollection(ObjectNode data, URI href) {
    this(data, href, JsonSupport::readObject);
  }

  PortolanCollection(ObjectNode data, URI href, StacDocumentReader reader) {
    super(data, href, reader);
  }

  public List<PortolanItem> items() {
    List<PortolanItem> result = new ArrayList<>();
    collectItems(data, href, reader, new HashSet<>(), result);
    return List.copyOf(result);
  }

  /** Returns only items referenced by direct {@code item} links. */
  public List<PortolanItem> directItems() {
    List<PortolanItem> result = new ArrayList<>();
    collectDirectItems(data, href, reader, result);
    return List.copyOf(result);
  }

  private static void collectItems(ObjectNode data, URI href, StacDocumentReader reader,
      Set<URI> visited, List<PortolanItem> result) {
    if (!visited.add(href)) return;
    collectDirectItems(data, href, reader, result);
    PortolanDocument current = PortolanDocuments.from(data, href, reader);
    for (PortolanLink link : current.links()) {
      if (!"child".equals(link.rel())) continue;
      ObjectNode child = reader.read(link.href());
      if ("Catalog".equals(JsonSupport.text(child, "type"))) {
        collectItems(child, link.href(), reader, visited, result);
      }
    }
  }

  private static void collectDirectItems(ObjectNode data, URI href, StacDocumentReader reader,
      List<PortolanItem> result) {
    PortolanDocument current = PortolanDocuments.from(data, href, reader);
    for (PortolanLink link : current.links()) {
      if (!"item".equals(link.rel())) continue;
      ObjectNode item = reader.read(link.href());
      if ("Feature".equals(JsonSupport.text(item, "type"))) {
        result.add(new PortolanItem(item, link.href(), reader));
      }
    }
  }
}
