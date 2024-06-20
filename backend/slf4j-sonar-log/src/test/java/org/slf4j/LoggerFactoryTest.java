/*
 * SonarLint Core - SLF4J log adaptor
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.slf4j;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggerFactoryTest {
  @Test
  void it_should_return_off_logger_for_disabled_loggers() {
    var logger = LoggerFactory.getLogger("com.networknt.schema.ConstValidator");

    assertThat(logger.isTraceEnabled()).isFalse();
    assertThat(logger.isDebugEnabled()).isFalse();
    assertThat(logger.isInfoEnabled()).isFalse();
    assertThat(logger.isWarnEnabled()).isFalse();
    assertThat(logger.isErrorEnabled()).isFalse();
  }

  @Test
  void it_should_return_default_logger_for_undefined_loggers() {
    var logger = LoggerFactory.getLogger(LoggerFactoryTest.class);

    assertThat(logger.isTraceEnabled()).isFalse();
    assertThat(logger.isDebugEnabled()).isTrue();
  }
}