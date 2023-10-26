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