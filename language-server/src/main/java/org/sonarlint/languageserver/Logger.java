/*
 * SonarLint Language Server
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarlint.languageserver;

public class Logger {

  enum MessageType {
    ERR_INCOMPLETE_SERVER_CONFIG("Incomplete server configuration. Required parameters must not be blank: serverId, serverUrl, token"),
    ERR_INCOMPLETE_BINDING("Incomplete binding configuration. Required parameters must not be blank: serverId, projectKey"),
    ERR_INVALID_BINDING_SERVER("Invalid binding: the specified serverId doesn't exist"),
    ERR_ANALYSIS_FAILED("Analysis failed")
    ;

    private final String message;

    MessageType(String message) {
      this.message = message;
    }
  }

  public void warn(MessageType messageType) {
    warn(messageType.message);
  }

  public void info(String message) {

  }

  public void warn(String message) {

  }

  public void error(String message, Exception e) {
  }
}
