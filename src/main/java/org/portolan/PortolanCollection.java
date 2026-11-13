package org.portolan;

import tools.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
    for (PortolanLink link : links()) {
      if (!"item".equals(link.rel())) {
        continue;
      }
      ObjectNode item = reader.read(link.href());
      if ("Feature".equals(JsonSupport.text(item, "type"))) {
        result.add(new PortolanItem(item, link.href(), reader));
      }
    }
    return List.copyOf(result);
  }
}
