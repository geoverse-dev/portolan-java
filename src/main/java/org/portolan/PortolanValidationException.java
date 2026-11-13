package org.portolan;

/** Raised when strict reading finds unsupported or non-conformant metadata. */
public final class PortolanValidationException extends IllegalArgumentException {
  private final PortolanReadResult result;

  public PortolanValidationException(PortolanReadResult result) {
    super(message(result));
    this.result = result;
  }

  public PortolanReadResult result() {
    return result;
  }

  private static String message(PortolanReadResult result) {
    long errors =
        result.findings().stream()
            .filter(finding -> finding.severity() == PortolanSeverity.ERROR)
            .count();
    return "Portolan strict validation failed with " + errors + " error(s)";
  }
}
