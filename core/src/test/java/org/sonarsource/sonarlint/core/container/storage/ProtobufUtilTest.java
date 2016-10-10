package org.sonarsource.sonarlint.core.container.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.sonar.scanner.protocol.input.ScannerInput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.container.storage.ProtobufUtil.readMessages;

public class ProtobufUtilTest {

  @Test
  public void test_readMessages_empty() throws IOException {
    try (InputStream inputStream = newEmptyStream()) {
      assertThat(readMessages(inputStream, ScannerInput.ServerIssue.parser())).isEmpty();
    }
  }

  @Test
  public void test_readMessages_multiple() throws IOException {
    ScannerInput.ServerIssue issue1 = ScannerInput.ServerIssue.newBuilder().build();
    ScannerInput.ServerIssue issue2 = ScannerInput.ServerIssue.newBuilder().build();

    try (InputStream inputStream = new ByteArrayInputStream(toByteArray(issue1, issue2))) {
      assertThat(readMessages(inputStream, issue1.getParserForType())).containsOnly(issue1, issue2);
    }
  }

  public static byte[] toByteArray(ScannerInput.ServerIssue... issues) throws IOException {
    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
      for (ScannerInput.ServerIssue issue : issues) {
        issue.writeDelimitedTo(byteStream);
      }
      return byteStream.toByteArray();
    }
  }

  public static ByteArrayInputStream newEmptyStream() {
    return new ByteArrayInputStream(new byte[0]);
  }
}
