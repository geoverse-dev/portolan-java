package org.portolan;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import tools.jackson.databind.node.ObjectNode;

/** Opens any STAC core document without normative validation. */
public final class PortolanDocuments {
  private PortolanDocuments() {}

  public static PortolanDocument open(Path source) {
    Objects.requireNonNull(source, "source");
    Path path = Files.isDirectory(source) ? source.resolve("catalog.json") : source;
    return open(path.toAbsolutePath().normalize().toUri());
  }

  public static PortolanDocument open(URI source) {
    Objects.requireNonNull(source, "source");
    validateScheme(source);
    JsonSupport.JsonDocument loaded = JsonSupport.readDocument(source);
    return from(loaded.data(), loaded.href(), JsonSupport::readObject);
  }

  static PortolanDocument open(URI source, StacDocumentReader reader) {
    ObjectNode data = reader.read(source);
    return from(data, source, reader);
  }

  static PortolanDocument from(ObjectNode data, URI source, StacDocumentReader reader) {
    return switch (String.valueOf(JsonSupport.text(data, "type"))) {
      case "Catalog" -> new PortolanCatalog(data, source, reader);
      case "Collection" -> new PortolanCollection(data, source, reader);
      case "Feature" -> new PortolanItem(data, source, reader);
      default -> throw new PortolanReadException(
          PortolanReadException.Code.INVALID_REQUEST,
          "Unsupported STAC document type at " + source);
    };
  }

  private static void validateScheme(URI source) {
    String scheme = source.getScheme();
    if (scheme == null || !(scheme.equals("file") || scheme.equals("http") || scheme.equals("https"))) {
      throw new PortolanReadException(
          PortolanReadException.Code.UNSUPPORTED_PROTOCOL,
          "Unsupported URI protocol: " + source);
    }
  }
}
