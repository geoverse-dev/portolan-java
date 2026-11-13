package org.portolan;

import java.net.URI;
import tools.jackson.databind.node.ObjectNode;

@FunctionalInterface
interface StacDocumentReader {
  ObjectNode read(URI source);
}
