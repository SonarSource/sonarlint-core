/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.impl;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

class SonarLintRpcClientLogbackAppender extends AppenderBase<ILoggingEvent> {

  private final SonarLintRpcClient rpcClient;

  public SonarLintRpcClientLogbackAppender(SonarLintRpcClient client) {
    rpcClient = client;
    start();
  }

  @Override
  protected void append(ILoggingEvent eventObject) {
    var configScopeId = eventObject.getMDCPropertyMap().get("configScopeId");
    var threadName = eventObject.getThreadName();
    var loggerName = eventObject.getLoggerName();
    rpcClient.log(new LogParams(LogLevel.valueOf(eventObject.getLevel().levelStr), eventObject.getFormattedMessage(), configScopeId, threadName, loggerName));
  }
}
