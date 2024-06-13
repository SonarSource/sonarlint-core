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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;

class GripMediumTests {

  public static final String CONFIG_SCOPE_ID = "configScope";
  private static final Issue JAVA_S1481_UNUSED_VARIABLE = new Issue("// this\n" +
    "// is\n" +
    "// a\n" +
    "// comment\n" +
    "public class Foo {\n"
    + "  public void foo() {\n"
    + "    int x;\n"
    + "  }\n"
    + "}", new TextRangeDto(7, 8, 7, 9), "Remove this unused \"x\" local variable.", "java:S1481");

  private static final Issue JAVA_S1219_NON_CASE_LABELS_IN_SWITCH = new Issue("public class Foo {\n"
    + "  public void foo() {\n"
    + "    switch (day) {\n"
    + "      case TOTO:\n"
    + "      case TATA:\n"
    + "      TUTU:\n"
    + "        help();\n"
    + "        break;\n"
    + "    }\n"
    + "  }\n"
    + "}", new TextRangeDto(6, 6, 6, 10), "Remove this misleading \"TUTU\" label.", "java:S1219");

  private static final Issue CURRENT_ISSUE = JAVA_S1219_NON_CASE_LABELS_IN_SWITCH;

  // @RegisterExtension
  // static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();
  private SonarLintRpcServer backend;

  @AfterEach
  void stop() {
    if (backend != null) {
      backend.shutdown().join();
    }
  }

  @Test
  void should_return_suggestions_to_the_client(@TempDir Path tempDir) {
    // System.setProperty("sonarlint.grip.url", mockServer.url(""));
    // mockServer.addStringResponse("/api/suggest", "{\n" +
    // " \"id\": \"chatcmpl-123\",\n" +
    // " \"object\": \"chat.completion\",\n" +
    // " \"created\": 1677652288,\n" +
    // " \"model\": \"gpt-3.5-turbo-0125\",\n" +
    // " \"system_fingerprint\": \"fp_44709d6fcb\",\n" +
    // " \"choices\": [{\n" +
    // " \"index\": 0,\n" +
    // " \"message\": {\n" +
    // " \"role\": \"assistant\",\n" +
    // " \"content\": \"Hello, world!\"\n" +
    // " },\n" +
    // " \"logprobs\": null,\n" +
    // " \"finish_reason\": \"stop\"\n" +
    // " }],\n" +
    // " \"usage\": {\n" +
    // " \"prompt_tokens\": 9,\n" +
    // " \"completion_tokens\": 12,\n" +
    // " \"total_tokens\": 21\n" +
    // " }\n" +
    // "}");
    var filePath = tempDir.resolve("file");
    var client = newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, tempDir,
        List.of(new ClientFileDto(filePath.toUri(), tempDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, null, CURRENT_ISSUE.sourceCode, null)))
      .build();
    backend = newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID)
      .build(client);

    var response = backend.getGripService()
      .suggestFix(new SuggestFixParams("token", CONFIG_SCOPE_ID, filePath.toUri(), CURRENT_ISSUE.message, CURRENT_ISSUE.textRange, CURRENT_ISSUE.ruleKey))
      .join();

    assertThat(response.getText()).isEqualTo("Hello, world!");
    // var recordedRequest = mockServer.takeRequest();
    // assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Basic dG9rZW46");
    // assertThat(recordedRequest.getBody().readString(Charset.defaultCharset())).isEqualTo("{\"source_code\":\"content\",\"message\":\"message\",\"rule_key\":\"rule:key\",\"text_range\":{\"start_line\":1,\"start_offset\":2,\"end_line\":3,\"end_offset\":4}}");
  }

  private static class Issue {
    private final String sourceCode;
    private final TextRangeDto textRange;
    private final String message;
    private final String ruleKey;

    private Issue(String sourceCode, TextRangeDto textRange, String message, String ruleKey) {
      this.sourceCode = sourceCode;
      this.textRange = textRange;
      this.message = message;
      this.ruleKey = ruleKey;
    }
  }
}
