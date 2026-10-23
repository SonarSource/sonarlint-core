/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) SonarSource Sàrl
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

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.plugin.PluginStatusMapper;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginRpcService;

public class PluginRpcServiceDelegate extends AbstractRpcServiceDelegate implements PluginRpcService {

  public PluginRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<GetPluginStatusesResponse> getPluginStatuses(GetPluginStatusesParams params) {
    return requestAsync(cancelMonitor -> {
      var configScopeId = params.getConfigurationScopeId();
      var connectionId = resolveConnectionId(configScopeId);
      var statuses = getBean(PluginsService.class).getPluginStatuses(connectionId);
      return new GetPluginStatusesResponse(PluginStatusMapper.toDto(statuses));
    }, params.getConfigurationScopeId());
  }

  @Nullable
  private String resolveConnectionId(@Nullable String configurationScopeId) {
    if (configurationScopeId == null) {
      return null;
    }
    return getBean(ConfigurationRepository.class)
      .getEffectiveBinding(configurationScopeId)
      .map(Binding::connectionId)
      .orElse(null);
  }

}
