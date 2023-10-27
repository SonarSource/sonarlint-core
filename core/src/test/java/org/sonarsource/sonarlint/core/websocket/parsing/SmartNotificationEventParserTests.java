/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.websocket.parsing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class SmartNotificationEventParserTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private SmartNotificationEventParser smartNotificationEventParser;

  @Test
  void should_parse_valid_json_date() {
    smartNotificationEventParser = new SmartNotificationEventParser("QA");
    var jsonData = "{\"message\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\", \"date\": \"2023-07-19T15:08:01+0000\"}";

    var optionalEvent = smartNotificationEventParser.parse(jsonData);

    assertThat(optionalEvent).isPresent();
    var event = optionalEvent.get();
    assertThat(event.getCategory()).isEqualTo("QA");
    assertThat(event.getDate()).isEqualTo("2023-07-19T15:08:01+0000");
    assertThat(event.getMessage()).isEqualTo("msg");
    assertThat(event.getProject()).isEqualTo("projectKey");
    assertThat(event.getLink()).isEqualTo("lnk");
  }

  @Test
  void should_not_parse_invalid_json_date() {
    smartNotificationEventParser = new SmartNotificationEventParser("QA");
    var jsonData = "{\"invalid\": \"msg\", \"link\": \"lnk\", \"project\": \"projectKey\", \"date\": \"2023-07-19T15:08:01+0000\"}";

    var optionalEvent = smartNotificationEventParser.parse(jsonData);

    assertThat(optionalEvent).isEmpty();
  }

}
