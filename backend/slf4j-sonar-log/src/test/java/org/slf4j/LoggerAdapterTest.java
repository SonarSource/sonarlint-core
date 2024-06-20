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
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LoggerAdapterTest {

  @Test
  void level_should_be_debug_for_default_constructor() {
    var logger = new LoggerAdapter(mock(SonarLintLogger.class));

    assertThat(logger.isTraceEnabled()).isFalse();
    assertThat(logger.isTraceEnabled(mock(Marker.class))).isFalse();
    assertThat(logger.isDebugEnabled()).isTrue();
    assertThat(logger.isDebugEnabled(mock(Marker.class))).isTrue();
  }

  @Test
  void it_should_only_delegate_trace_methods_if_level_is_trace() {
    var delegated = mock(SonarLintLogger.class);
    var logger = new LoggerAdapter(delegated, LogOutput.Level.TRACE);

    verifyTrace(logger, delegated, 1);
    verifyDebug(logger, delegated, 1);
    verifyInfo(logger, delegated, 1);
    verifyWarn(logger, delegated, 1);
    verifyError(logger, delegated, 1);
  }

  @Test
  void it_should_only_delegate_trace_methods_if_level_is_debug() {
    var delegated = mock(SonarLintLogger.class);
    var logger = new LoggerAdapter(delegated, LogOutput.Level.DEBUG);

    verifyTrace(logger, delegated, 0);
    verifyDebug(logger, delegated, 1);
    verifyInfo(logger, delegated, 1);
    verifyWarn(logger, delegated, 1);
    verifyError(logger, delegated, 1);
  }

  @Test
  void it_should_only_delegate_trace_methods_if_level_is_info() {
    var delegated = mock(SonarLintLogger.class);
    var logger = new LoggerAdapter(delegated, LogOutput.Level.INFO);

    verifyTrace(logger, delegated, 0);
    verifyDebug(logger, delegated, 0);
    verifyInfo(logger, delegated, 1);
    verifyWarn(logger, delegated, 1);
    verifyError(logger, delegated, 1);
  }

  @Test
  void it_should_only_delegate_trace_methods_if_level_is_warn() {
    var delegated = mock(SonarLintLogger.class);
    var logger = new LoggerAdapter(delegated, LogOutput.Level.WARN);

    verifyTrace(logger, delegated, 0);
    verifyDebug(logger, delegated, 0);
    verifyInfo(logger, delegated, 0);
    verifyWarn(logger, delegated, 1);
    verifyError(logger, delegated, 1);
  }

  @Test
  void it_should_only_delegate_trace_methods_if_level_is_error() {
    var delegated = mock(SonarLintLogger.class);
    var logger = new LoggerAdapter(delegated, LogOutput.Level.ERROR);

    verifyTrace(logger, delegated, 0);
    verifyDebug(logger, delegated, 0);
    verifyInfo(logger, delegated, 0);
    verifyWarn(logger, delegated, 0);
    verifyError(logger, delegated, 1);
  }

  @Test
  void it_should_not_log_if_logger_is_disabled() {
    var delegated = mock(SonarLintLogger.class);
    var logger = new LoggerAdapter(delegated, LogOutput.Level.OFF);

    verifyTrace(logger, delegated, 0);
    verifyDebug(logger, delegated, 0);
    verifyInfo(logger, delegated, 0);
    verifyWarn(logger, delegated, 0);
    verifyError(logger, delegated, 0);
  }

  private static void verifyTrace(LoggerAdapter logger, SonarLintLogger delegated, int invocationCount) {
    assertThat(logger.isTraceEnabled()).isEqualTo(invocationCount > 0);
    assertThat(logger.isTraceEnabled(mock(Marker.class))).isEqualTo(invocationCount > 0);

    var logMessage = "log message";
    logger.trace(logMessage);
    verify(delegated, times(invocationCount)).trace(logMessage);

    var runtimeException = new RuntimeException("runtime exception");
    logger.trace(logMessage, runtimeException);
    verify(delegated, times(invocationCount)).trace(logMessage, runtimeException);

    logger.trace(logMessage, "object");
    verify(delegated, times(invocationCount)).trace(logMessage, "object");

    logger.trace(logMessage, "object1", "object2");
    verify(delegated, times(invocationCount)).trace(logMessage, "object1", "object2");

    String[] argArray = {"object1", "object2"};
    logger.trace(logMessage, argArray);
    verify(delegated, times(invocationCount)).trace(logMessage, argArray);
  }

  private static void verifyDebug(LoggerAdapter logger, SonarLintLogger delegated, int invocationCount) {
    assertThat(logger.isDebugEnabled()).isEqualTo(invocationCount > 0);
    assertThat(logger.isDebugEnabled(mock(Marker.class))).isEqualTo(invocationCount > 0);

    var logMessage = "log message";
    logger.debug(logMessage);
    verify(delegated, times(invocationCount)).debug(logMessage);

    var runtimeException = new RuntimeException("runtime exception");
    logger.debug(logMessage, runtimeException);
    verify(delegated, times(invocationCount == 0 ? 0 : invocationCount + 1)).debug(logMessage);

    logger.debug(logMessage, "object");
    verify(delegated, times(invocationCount)).debug(logMessage, "object");

    logger.debug(logMessage, "object1", "object2");
    verify(delegated, times(invocationCount)).debug(logMessage, "object1", "object2");

    String[] argArray = {"object1", "object2"};
    logger.debug(logMessage, argArray);
    verify(delegated, times(invocationCount)).debug(logMessage, argArray);
  }

  private static void verifyInfo(LoggerAdapter logger, SonarLintLogger delegated, int invocationCount) {
    assertThat(logger.isInfoEnabled()).isEqualTo(invocationCount > 0);
    assertThat(logger.isInfoEnabled(mock(Marker.class))).isEqualTo(invocationCount > 0);

    var logMessage = "log message";
    logger.info(logMessage);
    verify(delegated, times(invocationCount)).info(logMessage);

    var runtimeException = new RuntimeException("runtime exception");
    logger.info(logMessage, runtimeException);
    verify(delegated, times(invocationCount == 0 ? 0 : invocationCount + 1)).info(logMessage);

    logger.info(logMessage, "object");
    verify(delegated, times(invocationCount)).info(logMessage, "object");

    logger.info(logMessage, "object1", "object2");
    verify(delegated, times(invocationCount)).info(logMessage, "object1", "object2");

    String[] argArray = {"object1", "object2"};
    logger.info(logMessage, argArray);
    verify(delegated, times(invocationCount)).info(logMessage, argArray);
  }

  private static void verifyWarn(LoggerAdapter logger, SonarLintLogger delegated, int invocationCount) {
    assertThat(logger.isWarnEnabled()).isEqualTo(invocationCount > 0);
    assertThat(logger.isWarnEnabled(mock(Marker.class))).isEqualTo(invocationCount > 0);

    var logMessage = "log message";
    logger.warn(logMessage);
    verify(delegated, times(invocationCount)).warn(logMessage);

    var runtimeException = new RuntimeException("runtime exception");
    logger.warn(logMessage, runtimeException);
    verify(delegated, times(invocationCount)).warn(logMessage);

    logger.warn(logMessage, "object");
    verify(delegated, times(invocationCount)).warn(logMessage, "object");

    logger.warn(logMessage, "object1", "object2");
    verify(delegated, times(invocationCount)).warn(logMessage, "object1", "object2");

    String[] argArray = {"object1", "object2"};
    logger.warn(logMessage, argArray);
    verify(delegated, times(invocationCount)).warn(logMessage, argArray);
  }

  private static void verifyError(LoggerAdapter logger, SonarLintLogger delegated, int invocationCount) {
    assertThat(logger.isErrorEnabled()).isEqualTo(invocationCount > 0);
    assertThat(logger.isErrorEnabled(mock(Marker.class))).isEqualTo(invocationCount > 0);

    var logMessage = "log message";
    logger.error(logMessage);
    verify(delegated, times(invocationCount)).error(logMessage);

    var runtimeException = new RuntimeException("runtime exception");
    logger.error(logMessage, runtimeException);
    verify(delegated, times(invocationCount == 0 ? 0 : invocationCount + 1)).error(logMessage);

    logger.error(logMessage, "object");
    verify(delegated, times(invocationCount)).error(logMessage, "object");

    logger.error(logMessage, "object1", "object2");
    verify(delegated, times(invocationCount)).error(logMessage, "object1", "object2");

    String[] argArray = {"object1", "object2"};
    logger.error(logMessage, argArray);
    verify(delegated, times(invocationCount)).error(logMessage, argArray);
  }

}