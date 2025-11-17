/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.Set;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;

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
    var globalSettings = serverApi.settings().getGlobalSettings(cancelMonitor);
    var supportedFeatures = serverApi.isSonarCloud() ? getSupportedFeaturesForSonarQubeCloud(serverApi, cancelMonitor) : serverApi.features().list(cancelMonitor);
    storage.serverInfo().store(serverStatus, supportedFeatures, globalSettings);
  }

  private static Set<Feature> getSupportedFeaturesForSonarQubeCloud(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    return serverApi.sca().isScaEnabled(cancelMonitor).enabled() ? Set.of(Feature.SCA) : Set.of();
  }
}
