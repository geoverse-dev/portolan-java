package org.portolan;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Metadata-only strict reader profile. Asset bytes and live hosting belong to Rashid. */
final class StrictPortolanValidator {
  private StrictPortolanValidator() {}

  static PortolanReadResult inspect(PortolanCatalog catalog) {
    List<PortolanFinding> findings = new ArrayList<>();
    String rootSchema = portolanSchema(catalog.data());
    PortolanSpecVersion version = PortolanSpecVersion.fromSchema(rootSchema);
    if (rootSchema == null) {
      error(
          findings,
          "PORTO-CORE-006",
          "catalog.stac_extensions",
          "catalog must declare a versioned Portolan schema URI");
    } else if (version == null || !version.supported()) {
      error(
          findings,
          "PORTOLAN-JAVA-001",
          "catalog.stac_extensions",
          "unsupported Portolan specification schema: " + rootSchema);
    }
    walkCatalog(
        catalog.data(), catalog.href(), catalog.reader, rootSchema, true, new HashSet<>(), findings);
    return new PortolanReadResult(
        catalog,
        version,
        findings,
        EnumSet.of(
            PortolanValidationScope.JSON_STRUCTURE,
            PortolanValidationScope.STAC_CORE_METADATA,
            PortolanValidationScope.PORTOLAN_METADATA,
            PortolanValidationScope.PORTOLAN_STRUCTURE));
  }

  private static void walkCatalog(
      ObjectNode data,
      URI href,
      StacDocumentReader reader,
      String rootSchema,
      boolean root,
      Set<URI> visited,
      List<PortolanFinding> findings) {
    if (!visited.add(href)) return;
    String path = root ? "catalog" : "catalog[" + href + "]";
    validateCommon(data, path, "Catalog", rootSchema, findings);
    validateDocumentationLinks(data, path, findings);
    validateStructuralLinks(data, path, root, false, findings);
    validateAssets(data, path, findings);
    for (PortolanLink link : links(data, href)) {
      if (!"child".equals(link.rel())) continue;
      ObjectNode child = read(reader, link, path, findings);
      if (child == null) continue;
      String type = JsonSupport.text(child, "type");
      if ("Catalog".equals(type)) {
        walkCatalog(child, link.href(), reader, rootSchema, false, visited, findings);
      } else if ("Collection".equals(type)) {
        validateCollection(child, link.href(), reader, rootSchema, visited, findings);
      } else {
        error(
            findings,
            "PORTO-CORE-002",
            path + ".links",
            "child link must resolve to a Catalog or Collection");
      }
    }
  }

  private static void validateCollection(
      ObjectNode data,
      URI href,
      StacDocumentReader reader,
      String rootSchema,
      Set<URI> visited,
      List<PortolanFinding> findings) {
    if (!visited.add(href)) return;
    String path = "collection[" + href + "]";
    validateCommon(data, path, "Collection", rootSchema, findings);
    validateDocumentationLinks(data, path, findings);
    validateStructuralLinks(data, path, false, false, findings);
    validateAssets(data, path, findings);
    validateLicense(data, path, findings);
    validateProviders(data, path, findings);
    validateBbox(data.at("/extent/spatial/bbox/0"), path + ".extent.spatial.bbox[0]", findings);
    for (PortolanLink link : links(data, href)) {
      if ("child".equals(link.rel())) {
        ObjectNode child = read(reader, link, path, findings);
        if (child != null) {
          error(
              findings,
              "Collection".equals(JsonSupport.text(child, "type"))
                  ? "PORTO-CORE-004"
                  : "PORTO-CORE-002",
              path + ".links",
              "collections must contain only items or assets, never child objects");
        }
      } else if ("item".equals(link.rel())) {
        ObjectNode item = read(reader, link, path, findings);
        if (item != null) validateItem(item, link.href(), findings);
      }
    }
  }

  private static void validateItem(
      ObjectNode data, URI href, List<PortolanFinding> findings) {
    String path = "item[" + href + "]";
    requireText(data, "id", "PORTO-CORE-019", path, findings);
    requireText(data, "collection", "PORTO-CORE-019", path, findings);
    if (!"Feature".equals(JsonSupport.text(data, "type"))) {
      error(findings, "PORTO-CORE-019", path + ".type", "item type must be Feature");
    }
    if (!"1.1.0".equals(JsonSupport.text(data, "stac_version"))) {
      error(findings, "PORTO-CORE-001", path + ".stac_version", "STAC version must be 1.1.0");
    }
    JsonNode properties = data.get("properties");
    if (properties == null || !properties.isObject()) {
      error(findings, "PORTO-CORE-019", path + ".properties", "item properties are required");
    } else if (properties.get("datetime") == null
        && (properties.get("start_datetime") == null || properties.get("end_datetime") == null)) {
      error(
          findings,
          "PORTO-CORE-019",
          path + ".properties.datetime",
          "datetime or a start/end datetime interval is required");
    }
    validateStructuralLinks(data, path, false, true, findings);
    validateAssets(data, path, findings);
    validateBbox(data.get("bbox"), path + ".bbox", findings);
  }

  private static void validateCommon(
      ObjectNode data,
      String path,
      String expectedType,
      String rootSchema,
      List<PortolanFinding> findings) {
    if (!expectedType.equals(JsonSupport.text(data, "type"))) {
      error(findings, "PORTO-CORE-001", path + ".type", "type must be " + expectedType);
    }
    if (!"1.1.0".equals(JsonSupport.text(data, "stac_version"))) {
      error(findings, "PORTO-CORE-001", path + ".stac_version", "STAC version must be 1.1.0");
    }
    requireText(data, "id", "PORTO-CORE-001", path, findings);
    requireText(data, "title", "PORTO-CORE-037", path, findings);
    requireText(data, "description", "PORTO-CORE-037", path, findings);
    String schema = portolanSchema(data);
    if (schema == null) {
      error(
          findings,
          "PORTO-CORE-006",
          path + ".stac_extensions",
          "catalogs and collections must declare the Portolan schema");
    } else if (rootSchema != null && !rootSchema.equals(schema)) {
      warning(
          findings,
          "PORTO-CORE-010",
          path + ".stac_extensions",
          "Portolan schema differs from the root catalog schema");
    }
  }

  private static void validateDocumentationLinks(
      ObjectNode data, String path, List<PortolanFinding> findings) {
    requireLink(data, path, "agents", "text/markdown", "PORTO-CORE-061", findings);
    requireLink(data, path, "describedby", "text/markdown", "PORTO-CORE-062", findings);
  }

  private static void validateStructuralLinks(
      ObjectNode data,
      String path,
      boolean root,
      boolean item,
      List<PortolanFinding> findings) {
    List<JsonNode> links = rawLinks(data);
    requireRelation(links, "root", path, "PORTO-CORE-032", findings);
    if (!root) requireRelation(links, "parent", path, "PORTO-CORE-032", findings);
    if (item) requireRelation(links, "collection", path, "PORTO-CORE-032", findings);
    for (JsonNode link : links) {
      String rel = text(link, "rel");
      if (!Set.of("root", "parent", "child", "item", "collection").contains(rel)) continue;
      String expected = "item".equals(rel) ? "application/geo+json" : "application/json";
      if (!expected.equals(text(link, "type"))) {
        error(
            findings,
            "PORTO-CORE-033",
            path + ".links",
            "structural link " + rel + " must declare type " + expected);
      }
      if (("child".equals(rel) || "item".equals(rel)) && blank(text(link, "title"))) {
        error(
            findings,
            "PORTO-CORE-039",
            path + ".links",
            rel + " links must include a title");
      }
    }
  }

  private static void validateAssets(
      ObjectNode data, String path, List<PortolanFinding> findings) {
    JsonNode assets = data.get("assets");
    if (assets == null) return;
    if (!assets.isObject()) {
      error(findings, "PORTO-CORE-022", path + ".assets", "assets must be an object");
      return;
    }
    assets.properties().forEach(entry -> {
      JsonNode asset = entry.getValue();
      String assetPath = path + ".assets." + entry.getKey();
      if (!asset.isObject() || blank(text(asset, "href"))) {
        error(findings, "PORTO-CORE-022", assetPath + ".href", "asset href is required");
        return;
      }
      String href = text(asset, "href");
      if (href.contains("://") && !href.startsWith("https://")) {
        error(
            findings,
            "PORTO-CORE-023",
            assetPath + ".href",
            "absolute asset href must use https");
      }
      if (blank(text(asset, "type"))) {
        error(findings, "PORTO-CORE-025", assetPath + ".type", "asset media type is required");
      }
      JsonNode roles = asset.get("roles");
      if (roles == null || !roles.isArray() || roles.isEmpty()) {
        error(findings, "PORTO-CORE-025", assetPath + ".roles", "at least one asset role is required");
      }
    });
  }

  private static void validateLicense(
      ObjectNode data, String path, List<PortolanFinding> findings) {
    String license = text(data, "license");
    if (blank(license)) {
      error(findings, "PORTO-CORE-058", path + ".license", "collection license is required");
    } else if ("proprietary".equals(license)) {
      error(findings, "PORTO-CORE-060", path + ".license", "proprietary is not a valid STAC 1.1 license");
    } else if ("other".equals(license) && !hasRelation(rawLinks(data), "license")) {
      error(findings, "PORTO-CORE-059", path + ".links", "license=other requires a license link");
    }
  }

  private static void validateProviders(
      ObjectNode data, String path, List<PortolanFinding> findings) {
    JsonNode providers = data.get("providers");
    if (providers == null || !providers.isArray() || providers.isEmpty()) {
      error(findings, "PORTO-CORE-046", path + ".providers", "collection providers are required");
      return;
    }
    int hosts = 0;
    boolean producer = false;
    JsonNode last = providers.get(providers.size() - 1);
    for (JsonNode provider : providers) {
      JsonNode roles = provider.get("roles");
      if (contains(roles, "producer")) producer = true;
      if (contains(roles, "host")) {
        hosts++;
        if (blank(text(provider, "url")) && blank(text(provider, "email"))) {
          error(
              findings,
              "PORTO-CORE-051",
              path + ".providers",
              "host provider must include url or email");
        }
      }
    }
    if (!producer || hosts != 1 || !contains(last.get("roles"), "host")) {
      error(
          findings,
          "PORTO-CORE-047",
          path + ".providers",
          "providers need a producer and exactly one final host");
    }
  }

  private static void validateBbox(
      JsonNode bbox, String path, List<PortolanFinding> findings) {
    if (bbox == null || !bbox.isArray()) return;
    if (bbox.size() != 4 && bbox.size() != 6) {
      error(findings, "PORTO-CORE-041", path, "bbox must contain four or six numbers");
      return;
    }
    for (int i = 0; i < bbox.size(); i++) {
      JsonNode coordinate = bbox.get(i);
      if (!coordinate.isNumber() || !Double.isFinite(coordinate.doubleValue())) {
        error(findings, "PORTO-CORE-041", path, "bbox coordinates must be finite numbers");
        return;
      }
    }
    double west = bbox.get(0).doubleValue();
    double south = bbox.get(1).doubleValue();
    double east = bbox.get(bbox.size() == 4 ? 2 : 3).doubleValue();
    double north = bbox.get(bbox.size() == 4 ? 3 : 4).doubleValue();
    if (west < -180 || west > 180 || east < -180 || east > 180 || south < -90 || north > 90 || south > north) {
      error(findings, "PORTO-CORE-041", path, "bbox must use ordered WGS84 coordinates");
    }
  }

  private static ObjectNode read(
      StacDocumentReader reader,
      PortolanLink link,
      String path,
      List<PortolanFinding> findings) {
    try {
      return reader.read(link.href());
    } catch (RuntimeException e) {
      error(
          findings,
          "PORTO-CORE-035",
          path + ".links",
          "link does not resolve: " + link.href());
      return null;
    }
  }

  private static List<PortolanLink> links(ObjectNode data, URI href) {
    return new PortolanCatalog(data, href, uri -> {
      throw new UnsupportedOperationException();
    }).links();
  }

  private static String portolanSchema(ObjectNode data) {
    JsonNode extensions = data.get("stac_extensions");
    if (extensions == null || !extensions.isArray()) return null;
    for (JsonNode extension : extensions) {
      if (extension.isString()) {
        String value = extension.stringValue();
        if (PortolanSpecVersion.fromSchema(value) != null) return value;
      }
    }
    return null;
  }

  private static void requireText(
      ObjectNode data,
      String field,
      String requirement,
      String path,
      List<PortolanFinding> findings) {
    if (blank(JsonSupport.text(data, field))) {
      error(findings, requirement, path + "." + field, field + " is required");
    }
  }

  private static void requireLink(
      ObjectNode data,
      String path,
      String relation,
      String type,
      String requirement,
      List<PortolanFinding> findings) {
    boolean present = rawLinks(data).stream()
        .anyMatch(link -> relation.equals(text(link, "rel")) && type.equals(text(link, "type")));
    if (!present) error(findings, requirement, path + ".links", relation + " Markdown link is required");
  }

  private static void requireRelation(
      List<JsonNode> links,
      String relation,
      String path,
      String requirement,
      List<PortolanFinding> findings) {
    if (!hasRelation(links, relation)) {
      error(findings, requirement, path + ".links", relation + " link is required");
    }
  }

  private static boolean hasRelation(List<JsonNode> links, String relation) {
    return links.stream().anyMatch(link -> relation.equals(text(link, "rel")));
  }

  private static List<JsonNode> rawLinks(ObjectNode data) {
    JsonNode links = data.get("links");
    List<JsonNode> result = new ArrayList<>();
    if (links != null && links.isArray()) links.forEach(result::add);
    return result;
  }

  private static boolean contains(JsonNode values, String expected) {
    if (values == null || !values.isArray()) return false;
    for (JsonNode value : values) {
      if (value.isString() && expected.equals(value.stringValue())) return true;
    }
    return false;
  }

  private static String text(JsonNode data, String field) {
    JsonNode value = data == null ? null : data.get(field);
    return value != null && value.isString() ? value.stringValue() : null;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static void error(
      List<PortolanFinding> findings, String requirement, String path, String message) {
    findings.add(new PortolanFinding(requirement, PortolanSeverity.ERROR, path, message));
  }

  private static void warning(
      List<PortolanFinding> findings, String requirement, String path, String message) {
    findings.add(new PortolanFinding(requirement, PortolanSeverity.WARNING, path, message));
  }
}
