/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.global;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalSettingsTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private final AbstractGlobalConfiguration globalConfiguration = mock(AbstractGlobalConfiguration.class);

  @Test
  void setNodePathPropertyForSonarJS() {
    GlobalSettings underTest = new GlobalSettings(globalConfiguration, new PropertyDefinitions(System2.INSTANCE));
    assertThat(underTest.getString("sonar.nodejs.executable")).isNull();

    Path providedNodePath = Paths.get("foo/bar/node");
    when(globalConfiguration.getNodeJsPath()).thenReturn(providedNodePath);

    underTest = new GlobalSettings(globalConfiguration, new PropertyDefinitions(System2.INSTANCE));
    assertThat(underTest.getString("sonar.nodejs.executable")).isEqualTo(providedNodePath.toString());
  }

}
