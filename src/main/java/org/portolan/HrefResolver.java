package org.portolan;

import java.net.URI;
import java.net.URISyntaxException;

/** Resolves hrefs that can contain unescaped spaces from published catalog JSON. */
final class HrefResolver {
  private HrefResolver() {}

  static URI resolve(URI base, String href) {
    try {
      URI reference = URI.create(href.replace(" ", "%20"));
      URI resolved = base.resolve(reference);
      if ("file".equals(resolved.getScheme()) && resolved.getAuthority() == null) {
        return new URI("file", "", resolved.getPath(), resolved.getQuery(), resolved.getFragment());
      }
      return resolved;
    } catch (URISyntaxException error) {
      throw new IllegalArgumentException("Invalid resolved href '" + href + "' in " + base, error);
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("Invalid href '" + href + "' in " + base, error);
    }
  }
}
