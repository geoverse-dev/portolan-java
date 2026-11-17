package org.portolan;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Process adapter for portolan-conformance. This class is not part of the SDK artifact. */
public final class ConformanceAdapter {
  private static final Set<String> OPERATIONS =
      Set.of("document", "inspect", "children", "collections", "items", "assets", "links",
          "validate_metadata");

  private ConformanceAdapter() {}

  public static void main(String[] args) {
    Map<String, Object> response;
    try {
      JsonNode request = JsonSupport.MAPPER.readTree(System.in);
      if (!request.isObject()) throw error("INVALID_REQUEST");
      String protocol = JsonSupport.text(request, "protocol_version");
      if (!"1".equals(protocol)) throw error("UNSUPPORTED_PROTOCOL");
      String operation = JsonSupport.text(request, "operation");
      String uriText = JsonSupport.text(request, "uri");
      if (operation == null || uriText == null || uriText.isBlank()) throw error("INVALID_REQUEST");
      if (!OPERATIONS.contains(operation)) throw error("UNSUPPORTED_OPERATION");
      URI uri;
      try { uri = URI.create(uriText); } catch (IllegalArgumentException exception) {
        throw error("INVALID_REQUEST");
      }
      if ("validate_metadata".equals(operation)) {
        response = Map.of("status", "ok", "result", validateMetadata(uri));
      } else {
        PortolanDocument document = PortolanDocuments.open(uri);
        response = Map.of("status", "ok", "result", result(operation, document));
      }
    } catch (AdapterError exception) {
      response = errorResponse(exception.code);
    } catch (PortolanReadException exception) {
      response = errorResponse(exception.code().name());
    } catch (Exception exception) {
      System.err.println(exception.getMessage());
      response = errorResponse("READ_ERROR");
    }
    System.out.println(JsonSupport.MAPPER.writeValueAsString(response));
  }

  private static Map<String, Object> validateMetadata(URI uri) {
    PortolanReadResult inspected = PortolanCatalog.inspect(uri);
    PortolanSpecVersion version = inspected.specVersion();
    if (version != null && !version.supported()) throw error("UNSUPPORTED_PROFILE");
    Set<String> errors = new TreeSet<>();
    Set<String> warnings = new TreeSet<>();
    for (PortolanFinding finding : inspected.findings()) {
      String id = commonRuleId(finding.requirementId());
      if (id == null) continue;
      (finding.severity() == PortolanSeverity.ERROR ? errors : warnings).add(id);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("profile_version", "1");
    result.put("spec_version", version == null ? null : version.version());
    result.put("valid", errors.isEmpty());
    result.put("errors", List.copyOf(errors)); result.put("warnings", List.copyOf(warnings));
    return result;
  }

  private static String commonRuleId(String id) {
    return switch (id) {
      case "PORTO-CORE-037" -> "PTL-TTL-001";
      case "PORTO-CORE-006" -> "PTL-CNF-001";
      case "PORTO-CORE-023", "PORTO-CORE-087" -> "PTL-AST-002";
      default -> null;
    };
  }

  private static Map<String, Object> result(String operation, PortolanDocument document) {
    return switch (operation) {
      case "document" -> Map.of("document", document.data());
      case "inspect" -> inspect(document);
      case "children" -> entries(document.children());
      case "collections" -> entries(
          document instanceof PortolanCatalog catalog ? new ArrayList<>(catalog.collections()) : List.of());
      case "items" -> entries(
          document instanceof PortolanCollection collection ? new ArrayList<>(collection.items()) : List.of());
      case "assets" -> assets(document);
      case "links" -> links(document);
      default -> throw error("UNSUPPORTED_OPERATION");
    };
  }

  private static Map<String, Object> inspect(PortolanDocument document) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("type", document.type()); result.put("id", document.id());
    result.put("title", document.title()); result.put("description", document.description());
    result.put("stac_version", document.stacVersion());
    result.put("stac_extensions", document.stacExtensions());
    return result;
  }

  private static Map<String, Object> entries(List<? extends PortolanDocument> documents) {
    List<Map<String, Object>> values = documents.stream()
        .map(document -> Map.<String, Object>of("id", document.id(), "uri", document.href().toString()))
        .sorted(Comparator.comparing((Map<String, Object> value) -> (String) value.get("id"))
            .thenComparing(value -> (String) value.get("uri"))).toList();
    return Map.of("entries", values);
  }

  private static Map<String, Object> assets(PortolanDocument document) {
    List<Map<String, Object>> values = document.assets().stream().map(asset -> {
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("key", asset.key()); value.put("href", asset.originalHref());
      value.put("resolved_href", asset.href().toString()); value.put("type", asset.mediaType());
      value.put("roles", asset.roles()); value.put("title", asset.title());
      value.put("description", asset.description());
      ObjectNode extra = asset.raw().deepCopy();
      extra.remove(List.of("href", "type", "media_type", "roles", "title", "description"));
      value.put("extra_fields", extra);
      return value;
    }).sorted(Comparator.comparing(value -> (String) value.get("key"))).toList();
    return Map.of("entries", values);
  }

  private static Map<String, Object> links(PortolanDocument document) {
    List<Map<String, Object>> values = document.links().stream().map(link -> {
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("rel", link.rel()); value.put("href", link.originalHref());
      value.put("resolved_href", link.href().toString()); value.put("type", link.mediaType());
      value.put("title", link.title());
      ObjectNode extra = link.raw().deepCopy();
      extra.remove(List.of("rel", "href", "type", "title")); value.put("extra_fields", extra);
      return value;
    }).sorted(Comparator.comparing((Map<String, Object> value) -> (String) value.get("rel"))
        .thenComparing(value -> (String) value.get("href"))).toList();
    return Map.of("entries", values);
  }

  private static AdapterError error(String code) { return new AdapterError(code); }
  private static Map<String, Object> errorResponse(String code) {
    return Map.of("status", "error", "error", Map.of("code", code));
  }
  private static final class AdapterError extends RuntimeException {
    private final String code;
    private AdapterError(String code) { this.code = code; }
  }
}
