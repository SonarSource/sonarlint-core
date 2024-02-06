/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class LogParams {

  private final LogLevel level;
  @Nullable
  private final String message;
  @Nullable
  private final String configScopeId;
  private final String threadName;
  private final String loggerName;
  @Nullable
  private final String stackTrace;
  private final Instant loggedAt;
  private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());

  public LogParams(LogLevel level, @Nullable String message, @Nullable String configScopeId, @Nullable String stackTrace, Instant loggedAt) {
    this(level, message, configScopeId, Thread.currentThread().getName(), "sonarlint", stackTrace, loggedAt);
  }

  public LogParams(LogLevel level, @Nullable String message, @Nullable String configScopeId, String threadName, String loggerName, @Nullable String stackTrace, Instant loggedAt) {
    this.level = level;
    this.message = message;
    this.configScopeId = configScopeId;
    this.threadName = threadName;
    this.loggerName = loggerName;
    this.stackTrace = stackTrace;
    this.loggedAt = loggedAt;
  }

  public LogLevel getLevel() {
    return level;
  }

  @CheckForNull
  public String getMessage() {
    return message;
  }

  /**
   * Some logs are specific to a certain config scope.
   * This can be used to display the log in the appropriate window, for IDEs that support multiple windows in the same instance (like IntelliJ)
   */
  @CheckForNull
  public String getConfigScopeId() {
    return configScopeId;
  }

  public String getThreadName() {
    return threadName;
  }

  public String getLoggerName() {
    return loggerName;
  }

  @CheckForNull
  public String getStackTrace() {
    return stackTrace;
  }

  public Instant getLoggedAt() {
    return loggedAt;
  }

  @Override
  public String toString() {
    var sb = new StringBuilder();
    sb.append(" [");
    sb.append(formatter.format(loggedAt));
    sb.append("] [");
    sb.append(threadName);
    sb.append("] ");
    sb.append(level.toString());
    sb.append(" ");
    sb.append(loggerName);
    sb.append(" - ");
    sb.append(message);
    if (stackTrace != null) {
      sb.append(System.lineSeparator());
      sb.append(stackTrace);
    }
    return sb.toString();
  }
}
