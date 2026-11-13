package org.portolan;

import java.net.URI;

/**
 * Service-provider interface for optional STAC readers.
 *
 * <p>Providers are discovered through {@link java.util.ServiceLoader}. Applications normally select
 * one through {@link PortolanCatalog#open(URI, StacReaderBackend)} rather than invoking it directly.
 */
public interface StacReaderProvider {
  StacReaderBackend backend();

  PortolanCatalog open(URI source);
}
