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

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

class RpcClientLogOutput implements ClientLogOutput {

  private final SonarLintRpcClient client;

  private final InheritableThreadLocal<String> configScopeId = new InheritableThreadLocal<>();

  RpcClientLogOutput(SonarLintRpcClient client) {
    this.client = client;
  }

  @Override
  public void log(String msg, Level level) {
    client.log(new LogParams(LogLevel.valueOf(level.name()), msg, configScopeId.get()));
  }

  public void setConfigScopeId(@Nullable String configScopeId) {
    this.configScopeId.set(configScopeId);
  }
}