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
package org.sonarlint.languageserver.log;

public interface ClientLogger {

  enum ErrorType {
    INCOMPLETE_SERVER_CONFIG("Incomplete server configuration. Required parameters must not be blank: serverId, serverUrl, token."),
    INCOMPLETE_BINDING("Incomplete binding configuration. Required parameters must not be blank: serverId, projectKey."),
    INVALID_BINDING_SERVER("Invalid binding: the specified serverId doesn't exist."),
    ANALYSIS_FAILED("Analysis failed."),
    START_CONNECTED_ENGINE_FAILED("Failed to start connected engine."),
    PROJECT_NOT_FOUND("Project was not found on SonarQube server (was it deleted?)"),
    ;

    final String message;

    ErrorType(String message) {
      this.message = message;
    }

  }

  void error(ErrorType errorType);

  void error(ErrorType errorType, Throwable t);

  void error(String message, Throwable t);

  void error(String message);

  void warn(String message);

  void info(String message);

  void debug(String message);

}
