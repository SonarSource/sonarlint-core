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

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class LogParams {

  private final LogLevel level;
  private final String message;
  private final String configScopeId;

  public LogParams(@NonNull LogLevel level, @NonNull String message, @Nullable String configScopeId) {
    this.level = level;
    this.message = message;
    this.configScopeId = configScopeId;
  }

  public LogLevel getLevel() {
    return level;
  }

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
}
