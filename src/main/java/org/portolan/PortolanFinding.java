package org.portolan;

import java.util.Objects;

/** One diagnostic tied to a normative requirement or Java reader limitation. */
public record PortolanFinding(
    String requirementId, PortolanSeverity severity, String path, String message) {
  public PortolanFinding {
    Objects.requireNonNull(requirementId, "requirementId");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(message, "message");
  }
}
