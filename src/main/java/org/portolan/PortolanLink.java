package org.portolan;

import tools.jackson.databind.node.ObjectNode;
import java.net.URI;

/** A STAC link with its HREF resolved against the containing document. */
public record PortolanLink(String rel, URI href, String mediaType, String title, ObjectNode raw) {
  /** Returns the HREF exactly as authored in the containing document. */
  public String originalHref() { return JsonSupport.text(raw, "href"); }
}
