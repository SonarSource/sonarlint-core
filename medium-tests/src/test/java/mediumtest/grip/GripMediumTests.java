/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package mediumtest.grip;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;
import testutils.MockWebServerExtensionWithProtobuf;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;

class GripMediumTests {

  public static final String CONFIG_SCOPE_ID = "configScope";

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();
  private SonarLintRpcServer backend;

  @AfterEach
  void stop() {
    if (backend != null) {
      backend.shutdown().join();
    }
  }

  @Test
  void should_return_suggestions_to_the_client(@TempDir Path tempDir) {
    System.setProperty("sonarlint.grip.url", mockServer.url(""));
    mockServer.addStringResponse("/api/suggest", "{\n" +
      "  \"id\": \"chatcmpl-123\",\n" +
      "  \"object\": \"chat.completion\",\n" +
      "  \"created\": 1677652288,\n" +
      "  \"model\": \"gpt-3.5-turbo-0125\",\n" +
      "  \"system_fingerprint\": \"fp_44709d6fcb\",\n" +
      "  \"choices\": [{\n" +
      "    \"index\": 0,\n" +
      "    \"message\": {\n" +
      "      \"role\": \"assistant\",\n" +
      "      \"content\": \"Hello, world!\"\n" +
      "    },\n" +
      "    \"logprobs\": null,\n" +
      "    \"finish_reason\": \"stop\"\n" +
      "  }],\n" +
      "  \"usage\": {\n" +
      "    \"prompt_tokens\": 9,\n" +
      "    \"completion_tokens\": 12,\n" +
      "    \"total_tokens\": 21\n" +
      "  }\n" +
      "}");
    var filePath = tempDir.resolve("file");
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, tempDir, List.of(new ClientFileDto(filePath.toUri(), tempDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, null, "content", null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .build(client);

    var response = backend.getGripService().suggestFix(new SuggestFixParams("token", CONFIG_SCOPE_ID, filePath.toUri(), "message", new TextRangeDto(1, 2, 3, 4), "rule:key"))
      .join();

    assertThat(response.getText()).isEqualTo("Hello, world!");
    var recordedRequest = mockServer.takeRequest();
    assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Basic dG9rZW46");
    assertThat(recordedRequest.getBody().readString(Charset.defaultCharset())).isEqualTo("{\"source_code\":\"content\",\"message\":\"message\",\"rule_key\":\"rule:key\",\"text_range\":{\"start_line\":1,\"start_offset\":2,\"end_line\":3,\"end_offset\":4}}");
  }
}
