package org.portolan;

import tools.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.List;

/** A STAC asset with Portolan-friendly accessors. */
public record PortolanAsset(
    String key,
    URI href,
    String mediaType,
    List<String> roles,
    String title,
    String description,
    ObjectNode raw) {

  /** Returns the HREF exactly as authored in the containing document. */
  public String originalHref() { return JsonSupport.text(raw, "href"); }

  public AssetFormat format() {
    String path = href == null || href.getPath() == null ? "" : href.getPath().toLowerCase();
    if ("application/vnd.apache.parquet".equals(mediaType) || path.endsWith(".parquet")) {
      return AssetFormat.GEOPARQUET;
    }
    if ("image/tiff; application=geotiff; profile=cloud-optimized".equals(mediaType)
        || path.endsWith(".tif")
        || path.endsWith(".tiff")) {
      return AssetFormat.COG;
    }
    if ("application/vnd.pmtiles".equals(mediaType) || path.endsWith(".pmtiles")) {
      return AssetFormat.PMTILES;
    }
    return AssetFormat.UNKNOWN;
  }
}
