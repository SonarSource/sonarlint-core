/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.mode;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.repository.ServerInfoRepository;

public class SeverityModeService {

  private final ServerInfoRepository serverInfoRepository;
  private final ConnectionConfigurationRepository connectionConfigurationRepository;

  public SeverityModeService(ServerInfoRepository serverInfoRepository, ConnectionConfigurationRepository connectionConfigurationRepository) {
    this.serverInfoRepository = serverInfoRepository;
    this.connectionConfigurationRepository = connectionConfigurationRepository;
  }

  public boolean isMQRModeForConnection(@Nullable String connectionId) {
    if (connectionId == null) {
      return true;
    }
    var connection = connectionConfigurationRepository.getConnectionById(connectionId);
    if (connection == null) {
      throw new IllegalArgumentException("Connection with id '" + connectionId + "' not found");
    }
    if (connection.getKind() == ConnectionKind.SONARCLOUD) {
      return true;
    }
    return serverInfoRepository.read(connectionId)
      .map(StoredServerInfo::shouldConsiderMultiQualityModeEnabled)
      // if no storage, use MQR
      .orElse(true);
  }

}
