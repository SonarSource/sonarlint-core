/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarlint.languageserver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

public class UserSettingsTest {

  @Test
  public void shouldParseEmptySettings() {
    UserSettings settings = fromJsonString("{}");
    assertThat(settings.testMatcher.matches(new File("./someTest").toPath())).isFalse();
    assertThat(settings.testMatcher.matches(new File("./someTest.ext").toPath())).isFalse();
    assertThat(settings.analyzerProperties).isEmpty();
    assertThat(settings.disableTelemetry).isFalse();
    assertThat(settings.excludedRules).isEmpty();
    assertThat(settings.includedRules).isEmpty();
    assertThat(settings.hasLocalRuleConfiguration()).isFalse();
  }

  @Test
  public void shouldParseFullWellFormedJsonSettings() {
    UserSettings settings = fromJsonString("{\n" +
      "  \"testFilePattern\": \"**/*Test.*\",\n" +
      "  \"analyzerProperties\": {\n" +
      "    \"sonar.polop\": \"palap\"\n" +
      "  },\n" +
      "  \"disableTelemetry\": true,\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"off\"\n" +
      "    },\n" +
      "    \"xoo:rule2\": {\n" +
      "      \"level\": \"warn\"\n" +
      "    },\n" +
      "    \"xoo:rule3\": {\n" +
      "      \"level\": \"on\"\n" +
      "    },\n" +
      "    \"xoo:notEvenARule\": \"definitely not a rule\",\n" +
      "    \"somethingNotParsedByRuleKey\": {\n" +
      "      \"level\": \"off\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n");

    assertThat(settings.testMatcher.matches(new File("./someTest").toPath())).isFalse();
    assertThat(settings.testMatcher.matches(new File("./someTest.ext").toPath())).isTrue();
    assertThat(settings.analyzerProperties).containsExactly(entry("sonar.polop", "palap"));
    assertThat(settings.disableTelemetry).isTrue();
    assertThat(settings.excludedRules).extracting("repository", "rule").containsExactly(tuple("xoo", "rule1"));
    assertThat(settings.includedRules).extracting("repository", "rule").containsExactly(tuple("xoo", "rule3"));
    assertThat(settings.hasLocalRuleConfiguration()).isTrue();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowOnUnexpectedObjectInSettings() {
    fromJsonString("\"not a JSON object\"");
  }

  @Test
  public void shouldHaveLocalRuleConfigurationWithDisabledRule() {
    assertThat(fromJsonString("{\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"off\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n").hasLocalRuleConfiguration()).isTrue();
  }

  @Test
  public void shouldHaveLocalRuleConfigurationWithEnabledRule() {
    assertThat(fromJsonString("{\n" +
      "  \"rules\": {\n" +
      "    \"xoo:rule1\": {\n" +
      "      \"level\": \"on\"\n" +
      "    }\n" +
      "  }\n" +
      "}\n").hasLocalRuleConfiguration()).isTrue();
  }

  private static UserSettings fromJsonString(String json) {
    return new UserSettings(UserSettings.parseToMap(new Gson().fromJson(json, JsonElement.class)));
  }
}
