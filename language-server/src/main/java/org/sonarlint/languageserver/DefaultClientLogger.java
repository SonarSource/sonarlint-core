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

import java.io.PrintWriter;
import java.io.StringWriter;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

class DefaultClientLogger implements ClientLogger {

  private final SonarLintLanguageClient client;

  DefaultClientLogger(SonarLintLanguageClient client) {
    this.client = client;
  }

  @Override
  public void error(ErrorType errorType) {
    client.logMessage(new MessageParams(MessageType.Error, errorType.message));
  }

  @Override
  public void error(ErrorType errorType, String message) {
    client.logMessage(new MessageParams(MessageType.Error, errorType.message + " - " + message));
  }

  @Override
  public void error(String message, Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    client.logMessage(new MessageParams(MessageType.Error, message + "\n" + sw.toString()));
  }

  @Override
  public void warn(String message) {
    client.logMessage(new MessageParams(MessageType.Warning, message));
  }

  @Override
  public void debug(String message) {
    client.logMessage(new MessageParams(MessageType.Log, message));
  }
}
