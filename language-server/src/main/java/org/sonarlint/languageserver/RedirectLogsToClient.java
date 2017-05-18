/*
 * SonarLint Language Server
 * Copyright (C) 2009-2017 SonarSource SA
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

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;

class RedirectLogsToClient implements LogOutput {

  private final LanguageClient client;

  public RedirectLogsToClient(LanguageClient client) {
    this.client = client;
  }

  @Override
  public void log(String formattedMessage, Level level) {
    client.logMessage(new MessageParams(messageType(level), formattedMessage));
  }

  private MessageType messageType(Level level) {
    switch (level) {
      case ERROR:
        return MessageType.Error;
      case WARN:
        return MessageType.Warning;
      case INFO:
        return MessageType.Info;
      case DEBUG:
      case TRACE:
        return MessageType.Log;
      default:
        throw new IllegalStateException("Unexpected level: " + level);
    }
  }
}
