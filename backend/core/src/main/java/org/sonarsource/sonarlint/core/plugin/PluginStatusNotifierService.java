/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.plugin;

import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.BoundScope;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidChangePluginStatusesParams;
import org.springframework.context.event.EventListener;

public class PluginStatusNotifierService {

  private final PluginsService pluginsService;
  private final SonarLintRpcClient client;
  private final ConfigurationRepository configurationRepository;

  public PluginStatusNotifierService(PluginsService pluginsService, SonarLintRpcClient client, ConfigurationRepository configurationRepository) {
    this.pluginsService = pluginsService;
    this.client = client;
    this.configurationRepository = configurationRepository;
  }

  @EventListener
  public void onPluginStatusesChanged(PluginStatusesChangedEvent event) {
    var connectionId = event.connectionId();
    var affectedScopeIds = connectionId == null
      ? configurationRepository.getConfigScopeIds()
      : configurationRepository.getBoundScopesToConnection(connectionId).stream()
        .map(BoundScope::getConfigScopeId).toList();

    for (var configScopeId : affectedScopeIds) {
      var effectiveConnectionId = connectionId != null ? connectionId
        : configurationRepository.getEffectiveBinding(configScopeId)
          .map(Binding::connectionId).orElse(null);
      var newStatuses = pluginsService.getPluginStatuses(effectiveConnectionId);
      client.didChangePluginStatuses(new DidChangePluginStatusesParams(configScopeId, PluginStatusMapper.toDto(newStatuses)));
    }
  }

}
