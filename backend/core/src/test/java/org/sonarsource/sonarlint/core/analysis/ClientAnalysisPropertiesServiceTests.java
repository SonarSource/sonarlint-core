/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.analysis;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAnalysisPropertiesServiceTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String CONFIG_SCOPE_ID = "scope-id";
  private static final String ANOTHER_CONFIG_SCOPE_ID = "another-scope-id";
  UserAnalysisPropertiesRepository underTest;

  @BeforeEach
  public void setup() {
    underTest = new UserAnalysisPropertiesRepository();
  }

  @Test
  void it_should_remove_previous_config_and_set_provided_user_properties() {
    var properties = underTest.getUserProperties(CONFIG_SCOPE_ID);
    assertThat(properties).isEmpty();

    underTest.setUserProperties(CONFIG_SCOPE_ID, Map.of("key1", "value1", "key2", "value2"));
    properties = underTest.getUserProperties(CONFIG_SCOPE_ID);
    assertThat(properties).hasSize(2).containsEntry("key1", "value1").containsEntry("key2", "value2");

    underTest.setUserProperties(CONFIG_SCOPE_ID, Map.of("key2", "new-value2", "key3", "new-value3"));

    properties = underTest.getUserProperties(CONFIG_SCOPE_ID);
    assertThat(properties).hasSize(2).containsEntry("key2", "new-value2").containsEntry("key3", "new-value3");
  }

  @Test
  void it_should_not_modify_other_config_scope_properties() {
    var properties = underTest.getUserProperties(CONFIG_SCOPE_ID);
    assertThat(properties).isEmpty();

    underTest.setUserProperties(CONFIG_SCOPE_ID, Map.of("key1", "value1", "key2", "value2"));
    underTest.setUserProperties(ANOTHER_CONFIG_SCOPE_ID, Map.of("key1", "value1"));
    properties = underTest.getUserProperties(CONFIG_SCOPE_ID);
    assertThat(properties).hasSize(2).containsEntry("key1", "value1").containsEntry("key2", "value2");

    underTest.setUserProperties(CONFIG_SCOPE_ID, Map.of("key2", "new-value2", "key3", "new-value3"));

    properties = underTest.getUserProperties(CONFIG_SCOPE_ID);
    assertThat(properties).hasSize(2).containsEntry("key2", "new-value2").containsEntry("key3", "new-value3");
    var anotherProperties = underTest.getUserProperties(ANOTHER_CONFIG_SCOPE_ID);
    assertThat(anotherProperties).hasSize(1).containsEntry("key1", "value1");
  }

}
