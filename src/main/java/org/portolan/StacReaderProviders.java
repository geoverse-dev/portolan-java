package org.portolan;

import java.net.URI;
import java.util.List;
import java.util.ServiceLoader;

final class StacReaderProviders {
  private StacReaderProviders() {}

  static PortolanCatalog open(URI source, StacReaderBackend backend) {
    if (backend == StacReaderBackend.LIGHTWEIGHT) {
      return PortolanCatalog.openLightweight(source);
    }
    List<StacReaderProvider> providers =
        ServiceLoader.load(StacReaderProvider.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(provider -> provider.backend() == backend)
        .toList();
    if (providers.isEmpty()) {
      throw new IllegalStateException(
          "STAC reader provider "
              + backend
              + " is not installed. Add its optional runtime artifact and use its required Java runtime.");
    }
    if (providers.size() > 1) {
      throw new IllegalStateException("More than one STAC reader provider is installed for " + backend);
    }
    return providers.get(0).open(source);
  }
}
