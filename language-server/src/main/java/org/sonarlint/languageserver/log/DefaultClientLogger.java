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

import java.io.PrintWriter;
import java.io.StringWriter;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.sonarlint.languageserver.SonarLintExtendedLanguageClient;

public class DefaultClientLogger implements ClientLogger {

  private final SonarLintExtendedLanguageClient client;

  public DefaultClientLogger(SonarLintExtendedLanguageClient client) {
    this.client = client;
  }

  @Override
  public void error(ErrorType errorType) {
    showMessage(new MessageParams(MessageType.Error, errorType.message));
  }

  @Override
  public void error(ErrorType errorType, Throwable t) {
    this.error(errorType.message, t);
  }

  @Override
  public void error(String message, Throwable t) {
    showMessage(new MessageParams(MessageType.Error, message + "\n" + t.getMessage()));
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    logMessage(new MessageParams(MessageType.Error, message + "\n" + sw.toString()));
  }

  @Override
  public void error(String message) {
    showMessage(new MessageParams(MessageType.Error, message));
    logMessage(new MessageParams(MessageType.Error, message));
  }

  @Override
  public void warn(String message) {
    logMessage(new MessageParams(MessageType.Warning, message));
  }

  @Override
  public void info(String message) {
    logMessage(new MessageParams(MessageType.Info, message));
  }

  @Override
  public void debug(String message) {
    logMessage(new MessageParams(MessageType.Log, message));
  }

  private void logMessage(MessageParams message) {
    client.logMessage(message);
  }

  private void showMessage(MessageParams message) {
    client.showMessage(message);
  }

}
