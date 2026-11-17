package org.portolan;

/** A stable read failure suitable for adapters and applications. */
public final class PortolanReadException extends IllegalArgumentException {
  public enum Code { NOT_FOUND, INVALID_JSON, INVALID_REQUEST, READ_ERROR, UNSUPPORTED_PROTOCOL }

  private final Code code;

  public PortolanReadException(Code code, String message) { super(message); this.code = code; }

  public PortolanReadException(Code code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public Code code() { return code; }
}
