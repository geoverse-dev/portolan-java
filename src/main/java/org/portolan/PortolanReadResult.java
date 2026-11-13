package org.portolan;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Catalog plus the diagnostics and validation passes performed while reading it. */
public record PortolanReadResult(
    PortolanCatalog catalog,
    PortolanSpecVersion specVersion,
    List<PortolanFinding> findings,
    Set<PortolanValidationScope> checkedScopes) {

  public PortolanReadResult {
    Objects.requireNonNull(catalog, "catalog");
    findings = List.copyOf(findings);
    checkedScopes = Set.copyOf(checkedScopes);
  }

  public boolean strictlyValid() {
    return specVersion != null
        && specVersion.supported()
        && findings.stream().noneMatch(finding -> finding.severity() == PortolanSeverity.ERROR);
  }

  public Set<PortolanValidationScope> uncheckedScopes() {
    EnumSet<PortolanValidationScope> unchecked = EnumSet.allOf(PortolanValidationScope.class);
    unchecked.removeAll(checkedScopes);
    return Set.copyOf(unchecked);
  }
}
