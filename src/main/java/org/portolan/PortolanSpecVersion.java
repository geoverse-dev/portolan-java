package org.portolan;

import java.util.Objects;

/** A Portolan specification version declared through its profile schema URI. */
public record PortolanSpecVersion(String version, String schemaUri, boolean supported) {
  public static final PortolanSpecVersion V0_2_0 =
      new PortolanSpecVersion(
          "0.2.0", "https://schemas.portolan-sdi.org/portolan/v0.2.0/schema.json", true);

  public PortolanSpecVersion {
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(schemaUri, "schemaUri");
  }

  static PortolanSpecVersion fromSchema(String schema) {
    if (V0_2_0.schemaUri.equals(schema)) {
      return V0_2_0;
    }
    String prefix = "https://schemas.portolan-sdi.org/portolan/v";
    String suffix = "/schema.json";
    if (schema != null && schema.startsWith(prefix) && schema.endsWith(suffix)) {
      return new PortolanSpecVersion(
          schema.substring(prefix.length(), schema.length() - suffix.length()), schema, false);
    }
    return null;
  }
}
