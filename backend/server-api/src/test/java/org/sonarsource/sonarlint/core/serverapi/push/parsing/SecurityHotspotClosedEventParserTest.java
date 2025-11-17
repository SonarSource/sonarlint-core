/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

class SecurityHotspotClosedEventParserTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  SecurityHotspotClosedEventParser parser = new SecurityHotspotClosedEventParser();
  private static final String TEST_PAYLOAD_WITHOUT_KEY = """
    {
      "projectKey": "test",
      "filePath": "/project/path/to/file"
    }""";

  private static final String TEST_PAYLOAD_WITHOUT_PROJECT_KEY = """
    {
      "key": "AYhSN6mVrRF_krvNbHl1",
      "filePath": "/project/path/to/file"
    }""";

  private static final String VALID_PAYLOAD = """
    {
      "key": "AYhSN6mVrRF_krvNbHl1",
      "projectKey": "test",
      "filePath": "/project/path/to/file"
    }""";

  @ParameterizedTest
  @ValueSource(strings = {TEST_PAYLOAD_WITHOUT_KEY, TEST_PAYLOAD_WITHOUT_PROJECT_KEY})
  void shouldReturnEmptyOptionalWhenPayloadIsInvalid(String invalidPayload) {
    var parseResult = parser.parse(invalidPayload);
    assertThat(parseResult).isEmpty();
  }

  @Test
  void shouldReturnChangeEventWhenPayloadIsValid() {
    var parsedResult = parser.parse(VALID_PAYLOAD);
    assertThat(parsedResult).isPresent();
    assertThat(parsedResult.get().getHotspotKey()).isEqualTo("AYhSN6mVrRF_krvNbHl1");
    assertThat(parsedResult.get().getProjectKey()).isEqualTo("test");
    assertThat(parsedResult.get().getFilePath()).isEqualTo(Path.of("/project/path/to/file"));
  }
}
