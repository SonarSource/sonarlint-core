/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.config.ConfigurationService;
import org.sonarsource.sonarlint.core.clientapi.connection.ConnectionService;

public class SonarLintBackendImpl implements SonarLintBackend {

  private final ConfigurationServiceImpl configurationService = new ConfigurationServiceImpl();
  private final ConnectionServiceImpl connectionService = new ConnectionServiceImpl();

  @Override
  public ConnectionService getConnectionService() {
    return connectionService;
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return configurationService;
  }
}
