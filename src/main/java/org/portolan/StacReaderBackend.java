package org.portolan;

/** STAC document reader implementations available to {@link PortolanCatalog}. */
public enum StacReaderBackend {
  /** Small Java 17 reader bundled with portolan-java and used by default. */
  LIGHTWEIGHT,

  /** Optional Java 25 preview provider backed by Parquetry and Tileverse Storage. */
  PARQUETRY
}
