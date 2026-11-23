package org.portolan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Storage-independent Portolan 0.3.0 catalog authoring. */
public final class PortolanAuthoring {
  public static final String SPEC_VERSION = "0.3.0";
  public static final String SCHEMA_URI =
      "https://schemas.portolan-sdi.org/portolan/v0.3.0/schema.json";

  private PortolanAuthoring() {}

  public static final class AuthoringException extends IllegalArgumentException {
    private final String code;
    public AuthoringException(String code, String message) { super(message); this.code = code; }
    public AuthoringException(String code, String message, Throwable cause) {
      super(message, cause); this.code = code;
    }
    public String code() { return code; }
  }

  public record ManagedDocument(String key, ObjectNode document, String parentKey, String relation,
      String linkTitle) {
    public ManagedDocument {
      document = document.deepCopy();
      relation = relation == null ? "child" : relation;
    }
    public ManagedDocument(String key, ObjectNode document, String parentKey, String relation) {
      this(key, document, parentKey, relation, null);
    }
  }

  public record Documentation(String ownerKey, String key, String relation, String content,
      String mediaType) {
    public Documentation {
      mediaType = mediaType == null ? "text/markdown" : mediaType;
    }
  }

  public record CatalogGraph(String specVersion, List<ManagedDocument> documents,
      List<Documentation> documentation) {
    public CatalogGraph {
      documents = List.copyOf(documents);
      documentation = List.copyOf(documentation);
    }

    public static CatalogGraph fromJson(JsonNode value) {
      if (value == null || !value.isObject()) throw failure("INVALID_REQUEST", "graph is required");
      String version = JsonSupport.text(value, "spec_version");
      JsonNode rawDocuments = value.get("documents");
      if (version == null || rawDocuments == null || !rawDocuments.isArray()) {
        throw failure("INVALID_REQUEST", "spec_version and documents are required");
      }
      List<ManagedDocument> documents = new ArrayList<>();
      for (JsonNode raw : rawDocuments) {
        if (!raw.isObject() || !raw.path("document").isObject()) {
          throw failure("INVALID_REQUEST", "invalid document entry");
        }
        String key = JsonSupport.text(raw, "key");
        String parent = JsonSupport.text(raw, "parent_key");
        String relation = JsonSupport.text(raw, "relation");
        String linkTitle = JsonSupport.text(raw, "link_title");
        if (key == null) throw failure("INVALID_REQUEST", "document key is required");
        documents.add(new ManagedDocument(
            key, (ObjectNode) raw.get("document"), parent, relation, linkTitle));
      }
      List<Documentation> documentation = new ArrayList<>();
      JsonNode rawDocumentation = value.get("documentation");
      if (rawDocumentation != null) {
        if (!rawDocumentation.isArray()) throw failure("INVALID_REQUEST", "documentation must be an array");
        for (JsonNode raw : rawDocumentation) {
          String owner = JsonSupport.text(raw, "owner_key");
          String key = JsonSupport.text(raw, "key");
          String relation = JsonSupport.text(raw, "rel");
          String content = JsonSupport.text(raw, "content");
          String mediaType = JsonSupport.text(raw, "media_type");
          if (owner == null || key == null || relation == null || content == null) {
            throw failure("INVALID_REQUEST", "invalid documentation entry");
          }
          documentation.add(new Documentation(owner, key, relation, content, mediaType));
        }
      }
      return new CatalogGraph(version, documents, documentation);
    }
  }

  public static final class Editor {
    private final String specVersion;
    private final LinkedHashMap<String, ManagedDocument> documents = new LinkedHashMap<>();
    private final LinkedHashMap<String, Documentation> documentation = new LinkedHashMap<>();

    public Editor(CatalogGraph graph) {
      specVersion = graph.specVersion();
      graph.documents().forEach(entry -> documents.put(entry.key(), copy(entry)));
      graph.documentation().forEach(entry -> documentation.put(entry.key(), entry));
    }

    public Editor setField(String key, String field, JsonNode value) {
      ManagedDocument entry = document(key);
      ObjectNode data = entry.document().deepCopy();
      data.set(field, value.deepCopy());
      documents.put(key, new ManagedDocument(
          key, data, entry.parentKey(), entry.relation(), entry.linkTitle()));
      return this;
    }

    public Editor addDocument(ManagedDocument entry) {
      if (documents.containsKey(entry.key()) || documentation.containsKey(entry.key())) {
        throw failure("DUPLICATE_OUTPUT_KEY", "logical output keys must be unique");
      }
      documents.put(entry.key(), copy(entry));
      return this;
    }

    public Editor removeDocument(String key) {
      if ("catalog.json".equals(key)) throw failure("MISSING_REQUIRED_METADATA", "root is required");
      if (!documents.containsKey(key)) throw failure("INVALID_REQUEST", "document does not exist");
      Set<String> removed = new LinkedHashSet<>(); removed.add(key);
      boolean changed;
      do {
        changed = false;
        for (ManagedDocument entry : documents.values()) {
          if (removed.contains(entry.parentKey()) && removed.add(entry.key())) changed = true;
        }
      } while (changed);
      removed.forEach(documents::remove);
      documentation.entrySet().removeIf(entry -> removed.contains(entry.getValue().ownerKey()));
      return this;
    }

    public Editor setDocumentation(Documentation entry) {
      if (!documents.containsKey(entry.ownerKey())) {
        throw failure("MISSING_REQUIRED_METADATA", "documentation owner is not managed");
      }
      if (documents.containsKey(entry.key())) {
        throw failure("DUPLICATE_OUTPUT_KEY", "logical output keys must be unique");
      }
      documentation.put(entry.key(), entry);
      return this;
    }

    public Editor removeDocumentation(String key) {
      if (documentation.remove(key) == null) {
        throw failure("INVALID_REQUEST", "documentation entry does not exist");
      }
      return this;
    }

    public Editor setAsset(String key, String assetKey, ObjectNode asset) {
      ManagedDocument entry = document(key);
      ObjectNode data = entry.document().deepCopy();
      JsonNode existing = data.get("assets");
      if (existing != null && !existing.isObject()) throw failure("WRITE_ERROR", "assets must be an object");
      ObjectNode assets = existing == null ? JsonSupport.MAPPER.createObjectNode() : (ObjectNode) existing.deepCopy();
      assets.set(assetKey, asset.deepCopy()); data.set("assets", assets);
      documents.put(key, new ManagedDocument(
          key, data, entry.parentKey(), entry.relation(), entry.linkTitle()));
      return this;
    }

    public Editor removeAsset(String key, String assetKey) {
      ManagedDocument entry = document(key);
      ObjectNode data = entry.document().deepCopy();
      JsonNode existing = data.get("assets");
      if (existing == null || !existing.isObject() || !existing.has(assetKey)) {
        throw failure("INVALID_REQUEST", "asset does not exist");
      }
      ObjectNode assets = (ObjectNode) existing.deepCopy(); assets.remove(assetKey); data.set("assets", assets);
      documents.put(key, new ManagedDocument(
          key, data, entry.parentKey(), entry.relation(), entry.linkTitle()));
      return this;
    }

    public CatalogGraph build() {
      return new CatalogGraph(specVersion, List.copyOf(documents.values()), List.copyOf(documentation.values()));
    }

    private ManagedDocument document(String key) {
      ManagedDocument value = documents.get(key);
      if (value == null) throw failure("INVALID_REQUEST", "document does not exist");
      return value;
    }
  }

  public record Operation(String kind, String key, byte[] content) {
    public Operation { content = content == null ? new byte[0] : content.clone(); }
    @Override public byte[] content() { return content.clone(); }
  }

  public record WritePlan(String specVersion, List<Operation> operations) {
    public WritePlan { operations = List.copyOf(operations); }
  }

  public static final class Planner {
    public WritePlan plan(CatalogGraph graph) { return plan(graph, List.of(), ""); }

    public WritePlan plan(CatalogGraph graph, List<String> priorManagedKeys, String keyPrefix) {
      preflight(graph);
      if (!keyPrefix.isEmpty() && !safeKey(keyPrefix)) throw failure("INVALID_OUTPUT_PATH", "invalid prefix");
      if (priorManagedKeys.stream().anyMatch(key -> !safeKey(key))) {
        throw failure("INVALID_OUTPUT_PATH", "invalid prior key");
      }
      List<ManagedDocument> documents = graph.documents().stream().map(PortolanAuthoring::copy).toList();
      Map<String, ManagedDocument> byKey = new LinkedHashMap<>();
      documents.forEach(entry -> byKey.put(entry.key(), entry));
      for (ManagedDocument entry : documents) {
        ObjectNode data = entry.document(); addSchema(data);
        ArrayNode links = links(data); links.add(link("self", Path.of(entry.key()).getFileName().toString(), "application/json"));
        if (!hasRelation(links, "root")) {
          links.add(link("root", relative(entry.key(), "catalog.json"), "application/json"));
        }
        if (entry.parentKey() != null) {
          links.add(link("parent", relative(entry.key(), entry.parentKey()), "application/json"));
          ManagedDocument parent = byKey.get(entry.parentKey());
          if (parent == null) throw failure("MISSING_REQUIRED_METADATA", "parent is not managed");
          ArrayNode parentLinks = links(parent.document());
          String title = managedLinkTitle(entry);
          if (title == null) {
            throw failure(
                "MISSING_REQUIRED_METADATA", "managed child and item links require a title");
          }
          String mediaType = "item".equals(entry.relation())
              ? "application/geo+json" : "application/json";
          parentLinks.add(link(
              entry.relation(), relative(parent.key(), entry.key()), mediaType, title));
          parent.document().set("links", parentLinks);
          if ("Feature".equals(JsonSupport.text(data, "type"))
              && "Collection".equals(JsonSupport.text(parent.document(), "type"))) {
            links.add(link("collection", relative(entry.key(), parent.key()), "application/json"));
          }
        }
        data.set("links", links);
      }
      for (Documentation doc : graph.documentation()) {
        ManagedDocument owner = byKey.get(doc.ownerKey());
        ArrayNode ownerLinks = links(owner.document());
        ownerLinks.add(link(doc.relation(), relative(owner.key(), doc.key()), doc.mediaType()));
        owner.document().set("links", ownerLinks);
      }
      List<Operation> operations = new ArrayList<>();
      for (ManagedDocument entry : documents) {
        operations.add(new Operation("put", prefixed(keyPrefix, entry.key()), serialize(normalizeLinks(entry.document()))));
      }
      for (Documentation doc : graph.documentation()) {
        operations.add(new Operation("put", prefixed(keyPrefix, doc.key()), doc.content().getBytes(StandardCharsets.UTF_8)));
      }
      Set<String> targets = new LinkedHashSet<>(); operations.forEach(op -> targets.add(op.key()));
      priorManagedKeys.stream().filter(key -> !targets.contains(key))
          .forEach(key -> operations.add(new Operation("delete", key, new byte[0])));
      operations.sort(Comparator.comparing(Operation::key));
      return new WritePlan(graph.specVersion(), operations);
    }
  }

  public static byte[] serialize(ObjectNode data) {
    try {
      String value = JsonSupport.MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT)
          .writeValueAsString(sorted(data));
      return (value + "\n").getBytes(StandardCharsets.UTF_8);
    } catch (RuntimeException exception) {
      throw failure("WRITE_ERROR", "document cannot be serialized", exception);
    }
  }

  private static void preflight(CatalogGraph graph) {
    if (!SPEC_VERSION.equals(graph.specVersion())) throw failure("UNSUPPORTED_SPEC_VERSION", "only 0.3.0 is supported");
    Set<String> keys = new LinkedHashSet<>();
    for (ManagedDocument entry : graph.documents()) if (!keys.add(entry.key())) throw failure("DUPLICATE_OUTPUT_KEY", "duplicate key");
    for (Documentation entry : graph.documentation()) if (!keys.add(entry.key())) throw failure("DUPLICATE_OUTPUT_KEY", "duplicate key");
    if (keys.stream().anyMatch(key -> !safeKey(key))) throw failure("INVALID_OUTPUT_PATH", "invalid output path");
    Map<String, ManagedDocument> byKey = new LinkedHashMap<>(); graph.documents().forEach(entry -> byKey.put(entry.key(), entry));
    ManagedDocument root = byKey.get("catalog.json");
    if (root == null || !"Catalog".equals(JsonSupport.text(root.document(), "type"))) throw failure("MISSING_REQUIRED_METADATA", "root required");
    for (ManagedDocument entry : graph.documents()) {
      String type = JsonSupport.text(entry.document(), "type");
      require(entry.document(), "type", "stac_version", "id");
      if ("Catalog".equals(type) || "Collection".equals(type)) require(entry.document(), "title", "description");
      else if ("Feature".equals(type)) require(entry.document(), "collection", "properties");
      else throw failure("MISSING_REQUIRED_METADATA", "unsupported document type");
      if (entry.parentKey() != null && !byKey.containsKey(entry.parentKey())) throw failure("MISSING_REQUIRED_METADATA", "parent missing");
      if (!Set.of("child", "item").contains(entry.relation())) throw failure("INVALID_REQUEST", "invalid relation");
    }
    for (Documentation entry : graph.documentation()) if (!byKey.containsKey(entry.ownerKey())) throw failure("MISSING_REQUIRED_METADATA", "owner missing");
  }

  private static void require(ObjectNode data, String... fields) {
    for (String field : fields) {
      JsonNode value = data.get(field);
      if (value == null || value.isNull() || value.isTextual() && value.asText().isEmpty()) {
        throw failure("MISSING_REQUIRED_METADATA", "required metadata missing");
      }
    }
  }

  private static void addSchema(ObjectNode data) {
    JsonNode existing = data.get("stac_extensions");
    ArrayNode values = existing != null && existing.isArray() ? (ArrayNode) existing.deepCopy() : JsonSupport.MAPPER.createArrayNode();
    boolean found = false; for (JsonNode value : values) if (SCHEMA_URI.equals(value.asText())) found = true;
    if (!found) values.add(SCHEMA_URI); data.set("stac_extensions", values);
  }

  private static ArrayNode links(ObjectNode data) {
    JsonNode existing = data.get("links");
    if (existing == null) return JsonSupport.MAPPER.createArrayNode();
    if (!existing.isArray()) throw failure("WRITE_ERROR", "links must be an array");
    ArrayNode result = JsonSupport.MAPPER.createArrayNode();
    for (JsonNode value : existing) {
      if (!value.isObject()) throw failure("WRITE_ERROR", "links must contain objects");
      result.add(value.deepCopy());
    }
    return result;
  }

  private static ObjectNode normalizeLinks(ObjectNode data) {
    ObjectNode result = data.deepCopy();
    ArrayNode normalized = JsonSupport.MAPPER.createArrayNode();
    TreeMap<String, JsonNode> unique = new TreeMap<>();
    for (JsonNode value : links(result)) {
      String rel = JsonSupport.text(value, "rel"), href = JsonSupport.text(value, "href");
      if (rel == null || href == null) throw failure("WRITE_ERROR", "links require rel and href");
      unique.putIfAbsent(rel + "\u0000" + href, value);
    }
    unique.values().forEach(normalized::add); result.set("links", normalized); return result;
  }

  private static ObjectNode link(String rel, String href, String type) {
    ObjectNode value = JsonSupport.MAPPER.createObjectNode(); value.put("rel", rel); value.put("href", href); value.put("type", type); return value;
  }

  private static ObjectNode link(String rel, String href, String type, String title) {
    ObjectNode value = link(rel, href, type); value.put("title", title); return value;
  }

  private static boolean hasRelation(ArrayNode links, String relation) {
    for (JsonNode value : links) {
      if (relation.equals(JsonSupport.text(value, "rel"))) return true;
    }
    return false;
  }

  private static String managedLinkTitle(ManagedDocument entry) {
    if (entry.linkTitle() != null && !entry.linkTitle().isEmpty()) return entry.linkTitle();
    if (Set.of("Catalog", "Collection").contains(JsonSupport.text(entry.document(), "type"))) {
      return JsonSupport.text(entry.document(), "title");
    }
    JsonNode properties = entry.document().get("properties");
    return properties != null && properties.isObject()
        ? JsonSupport.text(properties, "title") : null;
  }

  private static JsonNode sorted(JsonNode value) {
    if (value.isObject()) {
      ObjectNode result = JsonSupport.MAPPER.createObjectNode();
      TreeMap<String, JsonNode> fields = new TreeMap<>(); value.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
      fields.forEach((key, child) -> result.set(key, sorted(child))); return result;
    }
    if (value.isArray()) { ArrayNode result = JsonSupport.MAPPER.createArrayNode(); value.forEach(child -> result.add(sorted(child))); return result; }
    return value.deepCopy();
  }

  private static ManagedDocument copy(ManagedDocument entry) {
    return new ManagedDocument(
        entry.key(), entry.document(), entry.parentKey(), entry.relation(), entry.linkTitle());
  }
  private static boolean safeKey(String key) {
    if (key == null || key.isEmpty() || key.contains("\\") || key.startsWith("/")) return false;
    String[] parts = key.split("/", -1); for (String part : parts) if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return false;
    return true;
  }
  private static String relative(String owner, String target) {
    Path parent = Path.of(owner).getParent(); return (parent == null ? Path.of(target) : parent.relativize(Path.of(target))).toString().replace('\\', '/');
  }
  private static String prefixed(String prefix, String key) { return prefix.isEmpty() ? key : prefix.replaceAll("/+$", "") + "/" + key; }
  private static AuthoringException failure(String code, String message) { return new AuthoringException(code, message); }
  private static AuthoringException failure(String code, String message, Throwable cause) { return new AuthoringException(code, message, cause); }
}
