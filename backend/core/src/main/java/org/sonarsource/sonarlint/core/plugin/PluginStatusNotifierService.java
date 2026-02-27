/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidChangePluginStatusesParams;
import org.springframework.context.event.EventListener;

public class PluginStatusNotifierService {

  private final PluginsService pluginsService;
  private final SonarLintRpcClient client;
  private final AtomicReference<List<PluginStatusDto>> lastSentStatuses = new AtomicReference<>();

  public PluginStatusNotifierService(PluginsService pluginsService, SonarLintRpcClient client) {
    this.pluginsService = pluginsService;
    this.client = client;
  }

  @EventListener
  public void onPluginStatusesChanged(PluginStatusesChangedEvent event) {
    var newStatuses = PluginStatusMapper.toDto(pluginsService.getPluginStatuses(event.connectionId()));
    if (!newStatuses.equals(lastSentStatuses.getAndSet(newStatuses))) {
      client.didChangePluginStatuses(new DidChangePluginStatusesParams(newStatuses));
    }
  }

}
