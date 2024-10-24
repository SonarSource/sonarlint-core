/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.settings.SettingsApi;
import org.sonarsource.sonarlint.core.serverapi.system.MultiQualityMode;

public class ServerInfoSynchronizer {
  private final ConnectionStorage storage;

  public ServerInfoSynchronizer(ConnectionStorage storage) {
    this.storage = storage;
  }

  public StoredServerInfo readOrSynchronizeServerInfo(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    return storage.serverInfo().read()
      .orElseGet(() -> {
        synchronize(serverApi, cancelMonitor);
        return storage.serverInfo().read().get();
      });
  }

  public void synchronize(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    var serverStatus = serverApi.system().getStatus(cancelMonitor);
    var serverVersionAndStatusChecker = new ServerVersionAndStatusChecker(serverApi);
    serverVersionAndStatusChecker.checkVersionAndStatus(cancelMonitor);
    var multiQualityMode = getMultiQualityMode(serverApi.settings(), cancelMonitor);
    storage.serverInfo().store(serverStatus, multiQualityMode);
  }

  private static MultiQualityMode getMultiQualityMode(SettingsApi settingsApi, SonarLintCancelMonitor cancelMonitor) {
    var settingResponse = settingsApi.getGlobalSetting("sonar.multi-quality-mode.enabled", cancelMonitor);
    if (settingResponse == null) {
      return MultiQualityMode.DEFAULT;
    }
    var multiQualityMode = Boolean.parseBoolean(settingResponse);
    if (Boolean.TRUE.equals(multiQualityMode)) {
      return MultiQualityMode.MQR;
    } else {
      return MultiQualityMode.STANDARD;
    }
  }
}
