/*
 * SonarLint Core - Plugin Common
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
package org.sonarsource.sonarlint.core.plugin.common.log;

import org.junit.jupiter.api.Test;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.api.utils.log.SonarLintLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SonarLintLoggerFactoryTests {
  private final SonarLintLogger logger = mock(SonarLintLogger.class);
  private final SonarLintLoggerFactory factory = new SonarLintLoggerFactory(logger);

  @Test
  void getters_should_always_return_logger() {
    assertThat(factory.newInstance("name")).isEqualTo(logger);
    assertThat(SonarLintLoggerFactory.get("name")).isInstanceOf(SonarLintLogger.class);
    assertThat(SonarLintLoggerFactory.get(String.class)).isInstanceOf(SonarLintLogger.class);
    assertThat(factory.getLevel()).isEqualTo(LoggerLevel.DEBUG);
  }
}
