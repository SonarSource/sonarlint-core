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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.InputFile;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSensorDescriptorTests {

  @Test
  void describe() {
    var descriptor = new DefaultSensorDescriptor();
    descriptor
      .name("Foo")
      .onlyOnLanguage("java")
      .onlyOnFileType(InputFile.Type.MAIN)
      .onlyWhenConfiguration(c -> c.hasKey("sonar.foo.reportPath") && c.hasKey("sonar.foo.reportPath2"))
      .createIssuesForRuleRepository("squid-java");

    assertThat(descriptor.name()).isEqualTo("Foo");
    assertThat(descriptor.languages()).containsOnly("java");
    assertThat(descriptor.type()).isEqualTo(InputFile.Type.MAIN);
    var settings = new MapSettings(Map.of("sonar.foo.reportPath", "foo"));
    assertThat(descriptor.configurationPredicate().test(settings.asConfig())).isFalse();
    settings = new MapSettings(Map.of("sonar.foo.reportPath", "foo", "sonar.foo.reportPath2", "foo"));
    assertThat(descriptor.configurationPredicate().test(settings.asConfig())).isTrue();
    assertThat(descriptor.ruleRepositories()).containsOnly("squid-java");
  }

}
