package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.container.storage.ProtobufUtilTest.toByteArray;

public class IssueDownloaderImplTest {

  @Test
  public void test_download() throws IOException {
    ScannerInput.ServerIssue issue = ScannerInput.ServerIssue.newBuilder().build();

    SonarLintWsClient wsClient = WsClientTestUtils.createMock();

    String key = "dummyKey";
    try (InputStream inputStream = new ByteArrayInputStream(toByteArray(issue))) {
      WsClientTestUtils.addResponse(wsClient, "/batch/issues?key=" + key, inputStream);
    }

    IssueDownloader issueDownloader = new IssueDownloaderImpl(wsClient);
    assertThat(issueDownloader.apply(key)).containsOnly(issue);
  }
}
