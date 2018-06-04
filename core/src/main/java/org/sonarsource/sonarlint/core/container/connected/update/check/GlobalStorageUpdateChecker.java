/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update.check;

import java.util.List;

import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.container.connected.update.PluginListDownloader;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class GlobalStorageUpdateChecker {

  private final PluginsUpdateChecker pluginsUpdateChecker;
  private final GlobalSettingsUpdateChecker globalSettingsUpdateChecker;
  private final ServerVersionAndStatusChecker statusChecker;
  private final QualityProfilesUpdateChecker qualityProfilesUpdateChecker;
  private final PluginListDownloader pluginListDownloader;

  public GlobalStorageUpdateChecker(ServerVersionAndStatusChecker statusChecker, PluginsUpdateChecker pluginsUpdateChecker, PluginListDownloader pluginListDownloader,
    GlobalSettingsUpdateChecker globalSettingsUpdateChecker, QualityProfilesUpdateChecker qualityProfilesUpdateChecker) {
    this.statusChecker = statusChecker;
    this.pluginsUpdateChecker = pluginsUpdateChecker;
    this.pluginListDownloader = pluginListDownloader;
    this.globalSettingsUpdateChecker = globalSettingsUpdateChecker;
    this.qualityProfilesUpdateChecker = qualityProfilesUpdateChecker;
  }

  public StorageUpdateCheckResult checkForUpdate(ProgressWrapper progress) {
    DefaultStorageUpdateCheckResult result = new DefaultStorageUpdateCheckResult();

    progress.setProgressAndCheckCancel("Checking server version and status", 0.1f);
    ServerInfos serverStatus = statusChecker.checkVersionAndStatus();
    Version serverVersion = Version.create(serverStatus.getVersion());
    // Currently with don't check server version change since it is unlikely to have impact on SL

    progress.setProgressAndCheckCancel("Checking global properties", 0.3f);
    globalSettingsUpdateChecker.checkForUpdates(serverVersion, result);

    progress.setProgressAndCheckCancel("Checking plugins", 0.5f);
    List<SonarAnalyzer> pluginList = pluginListDownloader.downloadPluginList(serverVersion);
    pluginsUpdateChecker.checkForUpdates(result, pluginList);

    progress.setProgressAndCheckCancel("Checking quality profiles", 0.7f);
    qualityProfilesUpdateChecker.checkForUpdates(result);

    progress.setProgressAndCheckCancel("Done", 1.0f);

    return result;
  }

}
