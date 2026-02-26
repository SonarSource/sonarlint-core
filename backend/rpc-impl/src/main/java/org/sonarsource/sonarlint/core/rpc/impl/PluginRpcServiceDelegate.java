/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.plugin.ArtifactSource;
import org.sonarsource.sonarlint.core.plugin.PluginState;
import org.sonarsource.sonarlint.core.plugin.PluginStatus;
import org.sonarsource.sonarlint.core.plugin.PluginsService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;

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
      return new GetPluginStatusesResponse(toDto(statuses));
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

  private static List<PluginStatusDto> toDto(List<PluginStatus> statuses) {
    return statuses.stream().map(PluginRpcServiceDelegate::toDto).toList();
  }

  private static PluginStatusDto toDto(PluginStatus status) {
    return new PluginStatusDto(
      status.pluginName(),
      toDto(status.state()),
      toDto(status.source()),
      status.actualVersion() == null ? null : status.actualVersion().toString(),
      status.overriddenVersion() == null ? null : status.overriddenVersion().toString());
  }

  private static PluginStateDto toDto(PluginState state) {
    return switch (state) {
      case ACTIVE -> PluginStateDto.ACTIVE;
      case SYNCED -> PluginStateDto.SYNCED;
      case DOWNLOADING -> PluginStateDto.DOWNLOADING;
      case FAILED -> PluginStateDto.FAILED;
      case PREMIUM -> PluginStateDto.PREMIUM;
      case UNSUPPORTED -> PluginStateDto.UNSUPPORTED;
    };
  }

  @Nullable
  private static ArtifactSourceDto toDto(@Nullable ArtifactSource source) {
    if (source == null) {
      return null;
    }
    return switch (source) {
      case EMBEDDED -> ArtifactSourceDto.EMBEDDED;
      case ON_DEMAND -> ArtifactSourceDto.ON_DEMAND;
      case SONARQUBE_SERVER -> ArtifactSourceDto.SONARQUBE_SERVER;
      case SONARQUBE_CLOUD -> ArtifactSourceDto.SONARQUBE_CLOUD;
    };
  }

}
