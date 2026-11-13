package org.portolan;

/** Independently reportable validation passes. */
public enum PortolanValidationScope {
  JSON_STRUCTURE,
  STAC_CORE_METADATA,
  PORTOLAN_METADATA,
  PORTOLAN_STRUCTURE,
  ASSET_BYTES,
  LIVE_HOSTING
}
