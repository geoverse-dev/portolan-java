package org.portolan;

import io.tileverse.stac.JsonStacReader;
import io.tileverse.stac.StacCatalog;
import io.tileverse.storage.ReadHandle;
import io.tileverse.storage.ReadOptions;
import io.tileverse.storage.Storage;
import io.tileverse.storage.StorageFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.node.ObjectNode;

/** Parquetry implementation of the optional Portolan STAC reader SPI. */
public final class ParquetryStacReaderProvider implements StacReaderProvider {
  private static final int MAX_CATALOG_DEPTH = 128;

  @Override
  public StacReaderBackend backend() {
    return StacReaderBackend.PARQUETRY;
  }

  @Override
  public PortolanCatalog open(URI source) {
    URI normalizedSource = source.normalize();
    URI catalogDirectory = normalizedSource.resolve(".");
    Map<URI, ObjectNode> documents = new ConcurrentHashMap<>();

    try (Storage delegate = StorageFactory.open(catalogDirectory)) {
      Storage capturing = capturingStorage(delegate, catalogDirectory, documents);
      StacCatalog catalog = new JsonStacReader().open(normalizedSource, capturing);
      materialize(catalog, 0);
    } catch (IOException failure) {
      throw new UncheckedIOException("closing STAC storage for " + source, failure);
    }

    ObjectNode root = documents.get(normalizedSource);
    if (root == null) {
      throw new IllegalStateException("Parquetry did not read the root STAC document " + source);
    }
    return new PortolanCatalog(root.deepCopy(), normalizedSource, uri -> readCaptured(documents, uri));
  }

  private static ObjectNode readCaptured(Map<URI, ObjectNode> documents, URI uri) {
    ObjectNode document = documents.get(uri.normalize());
    if (document == null) {
      throw new IllegalArgumentException("STAC document was not materialized by Parquetry: " + uri);
    }
    return document.deepCopy();
  }

  private static void materialize(StacCatalog catalog, int depth) {
    if (depth > MAX_CATALOG_DEPTH) {
      throw new IllegalArgumentException(
          "STAC catalog nesting exceeds " + MAX_CATALOG_DEPTH + " levels (possible child cycle)");
    }
    catalog.collections().forEach(collection -> collection.items());
    catalog.childCatalogs().forEach(child -> materialize(child, depth + 1));
  }

  private static Storage capturingStorage(
      Storage delegate, URI catalogDirectory, Map<URI, ObjectNode> documents) {
    return (Storage)
        Proxy.newProxyInstance(
            Storage.class.getClassLoader(),
            new Class<?>[] {Storage.class},
            (proxy, method, arguments) -> {
              if ("read".equals(method.getName())) {
                String key = (String) arguments[0];
                ReadOptions options =
                    arguments.length == 1 ? ReadOptions.defaults() : (ReadOptions) arguments[1];
                try (ReadHandle handle = delegate.read(key, options)) {
                  byte[] bytes = handle.content().readAllBytes();
                  URI documentUri = catalogDirectory.resolve(key).normalize();
                  documents.put(documentUri, JsonSupport.readObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
                  return new ReadHandle(new ByteArrayInputStream(bytes), handle.metadata());
                }
              }
              try {
                return method.invoke(delegate, arguments);
              } catch (InvocationTargetException failure) {
                throw failure.getCause();
              }
            });
  }
}
