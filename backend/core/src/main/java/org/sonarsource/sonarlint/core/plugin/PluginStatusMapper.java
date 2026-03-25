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

import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;

import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class PluginStatusMapper {

  private PluginStatusMapper() {
  }

  public static List<PluginStatusDto> toDto(List<PluginStatus> statuses) {
    return statuses.stream().map(PluginStatusMapper::toDto).toList();
  }

  public static PluginStatusDto toDto(PluginStatus status) {
    return new PluginStatusDto(
      status.language() != null ? Language.valueOf(status.language().name()) : null,
      status.language() != null ? status.language().getName() : null,
      toDto(status.state()),
      toDto(status.source()),
      status.actualVersion() == null ? null : status.actualVersion().toString(),
      status.overriddenVersion() == null ? null : status.overriddenVersion().toString(),
      status.serverVersion()
    );
  }

  public static PluginStateDto toDto(ArtifactState state) {
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
  public static ArtifactSourceDto toDto(@Nullable ArtifactSource source) {
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
