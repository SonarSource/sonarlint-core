/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.push.parsing;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.commons.HotspotReviewStatus.TO_REVIEW;

class SecurityHotspotRaisedEventParserTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  SecurityHotspotRaisedEventParser parser = new SecurityHotspotRaisedEventParser();
  final String TEST_PAYLOAD_WITHOUT_KEY = "{\n" +
    "  \"status\": \"TO_REVIEW\",\n" +
    "  \"vulnerabilityProbability\": \"MEDIUM\",\n" +
    "  \"creationDate\": 1685006550000,\n" +
    "  \"mainLocation\": {\n" +
    "    \"filePath\": \"src/main/java/org/example/Main.java\",\n" +
    "    \"message\": \"Make sure that using this pseudorandom number generator is safe here.\",\n" +
    "    \"textRange\": {\n" +
    "      \"startLine\": 12,\n" +
    "      \"startLineOffset\": 29,\n" +
    "      \"endLine\": 12,\n" +
    "      \"endLineOffset\": 36,\n" +
    "      \"hash\": \"43b5c9175984c071f30b873fdce0a000\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"ruleKey\": \"java:S2245\",\n" +
    "  \"projectKey\": \"test\",\n" +
    "  \"branch\": \"some-branch\"\n" +
    "}";

  final String TEST_PAYLOAD_WITHOUT_BRANCH = "{\n" +
    "  \"status\": \"TO_REVIEW\",\n" +
    "  \"vulnerabilityProbability\": \"MEDIUM\",\n" +
    "  \"creationDate\": 1685006550000,\n" +
    "  \"mainLocation\": {\n" +
    "    \"filePath\": \"src/main/java/org/example/Main.java\",\n" +
    "    \"message\": \"Make sure that using this pseudorandom number generator is safe here.\",\n" +
    "    \"textRange\": {\n" +
    "      \"startLine\": 12,\n" +
    "      \"startLineOffset\": 29,\n" +
    "      \"endLine\": 12,\n" +
    "      \"endLineOffset\": 36,\n" +
    "      \"hash\": \"43b5c9175984c071f30b873fdce0a000\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"ruleKey\": \"java:S2245\",\n" +
    "  \"key\": \"AYhSN6mVrRF_krvNbHl1\",\n" +
    "  \"projectKey\": \"test\"\n" +
    "}";

  final String TEST_PAYLOAD_WITHOUT_PROJECT_KEY = "{\n" +
    "  \"status\": \"TO_REVIEW\",\n" +
    "  \"vulnerabilityProbability\": \"MEDIUM\",\n" +
    "  \"creationDate\": 1685006550000,\n" +
    "  \"mainLocation\": {\n" +
    "    \"filePath\": \"src/main/java/org/example/Main.java\",\n" +
    "    \"message\": \"Make sure that using this pseudorandom number generator is safe here.\",\n" +
    "    \"textRange\": {\n" +
    "      \"startLine\": 12,\n" +
    "      \"startLineOffset\": 29,\n" +
    "      \"endLine\": 12,\n" +
    "      \"endLineOffset\": 36,\n" +
    "      \"hash\": \"43b5c9175984c071f30b873fdce0a000\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"ruleKey\": \"java:S2245\",\n" +
    "  \"key\": \"AYhSN6mVrRF_krvNbHl1\",\n" +
    "  \"branch\": \"some-branch\"\n" +
    "}";

  final String TEST_PAYLOAD_WITHOUT_FILE_PATH = "{\n" +
    "  \"status\": \"TO_REVIEW\",\n" +
    "  \"vulnerabilityProbability\": \"MEDIUM\",\n" +
    "  \"creationDate\": 1685006550000,\n" +
    "  \"mainLocation\": {\n" +
    "    \"message\": \"Make sure that using this pseudorandom number generator is safe here.\",\n" +
    "    \"textRange\": {\n" +
    "      \"startLine\": 12,\n" +
    "      \"startLineOffset\": 29,\n" +
    "      \"endLine\": 12,\n" +
    "      \"endLineOffset\": 36,\n" +
    "      \"hash\": \"43b5c9175984c071f30b873fdce0a000\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"ruleKey\": \"java:S2245\",\n" +
    "  \"key\": \"AYhSN6mVrRF_krvNbHl1\",\n" +
    "  \"projectKey\": \"test\",\n" +
    "  \"branch\": \"some-branch\"\n" +
    "}";

  final String VALID_PAYLOAD = "{\n" +
    "  \"status\": \"TO_REVIEW\",\n" +
    "  \"vulnerabilityProbability\": \"MEDIUM\",\n" +
    "  \"creationDate\": 1685006550000,\n" +
    "  \"mainLocation\": {\n" +
    "    \"filePath\": \"src/main/java/org/example/Main.java\",\n" +
    "    \"message\": \"Make sure that using this pseudorandom number generator is safe here.\",\n" +
    "    \"textRange\": {\n" +
    "      \"startLine\": 12,\n" +
    "      \"startLineOffset\": 29,\n" +
    "      \"endLine\": 12,\n" +
    "      \"endLineOffset\": 36,\n" +
    "      \"hash\": \"43b5c9175984c071f30b873fdce0a000\"\n" +
    "    }\n" +
    "  },\n" +
    "  \"ruleKey\": \"java:S2245\",\n" +
    "  \"key\": \"AYhSN6mVrRF_krvNbHl1\",\n" +
    "  \"projectKey\": \"test\",\n" +
    "  \"branch\": \"some-branch\"\n" +
    "}";

  @ParameterizedTest
  @ValueSource(strings = {TEST_PAYLOAD_WITHOUT_KEY, TEST_PAYLOAD_WITHOUT_PROJECT_KEY, TEST_PAYLOAD_WITHOUT_FILE_PATH, TEST_PAYLOAD_WITHOUT_BRANCH})
  void shouldReturnEmptyOptionalWhenPayloadIsInvalid(String invalidPayload) {
    var parseResult = parser.parse(invalidPayload);
    assertThat(parseResult).isEmpty();
  }

  @Test
  void shouldReturnChangeEventWhenPayloadIsValid() {
    var parsedResult = parser.parse(VALID_PAYLOAD);
    assertThat(parsedResult).isPresent();
    assertThat(parsedResult.get().getHotspotKey()).isEqualTo("AYhSN6mVrRF_krvNbHl1");
    assertThat(parsedResult.get().getStatus()).isEqualTo(TO_REVIEW);
    assertThat(parsedResult.get().getProjectKey()).isEqualTo("test");
    assertThat(parsedResult.get().getMainLocation().getFilePath()).isEqualTo(Path.of("src/main/java/org/example/Main.java"));
    assertThat(parsedResult.get().getBranch()).isEqualTo("some-branch");
    assertThat(parsedResult.get().getRuleKey()).isEqualTo("java:S2245");
    assertThat(parsedResult.get().getMainLocation().getMessage()).isEqualTo("Make sure that using this pseudorandom number generator is safe here.");
  }
}
