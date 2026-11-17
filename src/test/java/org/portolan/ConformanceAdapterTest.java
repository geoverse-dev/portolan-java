package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConformanceAdapterTest {
  @Test void mapsBlankUriToInvalidRequest() throws Exception {
    InputStream originalIn = System.in;
    PrintStream originalOut = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      System.setIn(new ByteArrayInputStream(
          "{\"protocol_version\":\"1\",\"operation\":\"inspect\",\"uri\":\"  \"}\n"
              .getBytes(StandardCharsets.UTF_8)));
      System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
      ConformanceAdapter.main(new String[0]);
    } finally {
      System.setIn(originalIn);
      System.setOut(originalOut);
    }
    var response = JsonSupport.MAPPER.readTree(output.toString(StandardCharsets.UTF_8));
    assertEquals("INVALID_REQUEST", response.at("/error/code").asText());
  }
}
