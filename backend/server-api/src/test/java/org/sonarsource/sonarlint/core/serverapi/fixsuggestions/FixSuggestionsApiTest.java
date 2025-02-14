/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.fixsuggestions;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedBodyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class FixSuggestionsApiTest {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private FixSuggestionsApi underTest;

  @BeforeEach
  void setUp() {
    underTest = new FixSuggestionsApi(mockServer.serverApiHelper());
  }

  @Test
  void it_should_throw_an_exception_if_the_body_is_malformed() {
    mockServer.addStringResponse("/fix-suggestions/ai-suggestions", """
      {
        "id": "XXX
      }
      """);

    var throwable = catchThrowable(() -> underTest.getAiSuggestion(
      new AiSuggestionRequestBodyDto("orgKey", "projectKey", new AiSuggestionRequestBodyDto.Issue("message", 0, 0, "rule:key", "source")), new SonarLintCancelMonitor()));

    assertThat(throwable).isInstanceOf(UnexpectedBodyException.class);
  }

  @Test
  void it_should_return_the_generated_suggestion() {
    mockServer.addStringResponse("/fix-suggestions/ai-suggestions", """
      {
        "id": "9d4e18f6-f79f-41ad-a480-1c96bd58d58f",
        "explanation": "This is the way",
        "changes": [
          {
            "startLine": 0,
            "endLine": 0,
            "newCode": "This is the new code"
          }
        ]
      }
      """);

    var response = underTest.getAiSuggestion(new AiSuggestionRequestBodyDto("orgKey", "projectKey", new AiSuggestionRequestBodyDto.Issue("message", 0, 0, "rule:key", "source")),
      new SonarLintCancelMonitor());

    assertThat(response)
      .isEqualTo(new AiSuggestionResponseBodyDto(UUID.fromString("9d4e18f6-f79f-41ad-a480-1c96bd58d58f"), "This is the way",
        List.of(new AiSuggestionResponseBodyDto.ChangeDto(0, 0, "This is the new code"))));
  }

}
