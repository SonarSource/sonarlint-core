/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.container.global;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.System2;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSettingsTests {

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void emptyNodePathPropertyForSonarJS() {
    var underTest = new GlobalSettings(AnalysisEngineConfiguration.builder().build(), new PropertyDefinitions(System2.INSTANCE));

    var nodeJsExecutableValue = underTest.getString("sonar.nodejs.executable");

    assertThat(nodeJsExecutableValue).isNull();
  }

  @Test
  void customNodePathPropertyForSonarJS() {
    var providedNodePath = Paths.get("foo/bar/node");
    var underTest = new GlobalSettings(AnalysisEngineConfiguration.builder().setNodeJs(providedNodePath).build(),
      new PropertyDefinitions(System2.INSTANCE));

    var nodeJsExecutableValue = underTest.getString("sonar.nodejs.executable");

    assertThat(nodeJsExecutableValue).isEqualTo(providedNodePath.toString());
  }

}
