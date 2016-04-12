/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected;

import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalUpdateStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.update.GlobalPropertiesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.GlobalUpdateExecutor;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleConfigUpdateExecutor;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleListDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.PluginReferencesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.QualityProfilesDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.plugin.cache.PluginHashes;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ConnectedContainer extends ComponentContainer {

  private final ServerConfiguration serverConfiguration;
  private final ConnectedGlobalConfiguration globalConfig;

  public ConnectedContainer(ConnectedGlobalConfiguration globalConfig, ServerConfiguration serverConfiguration) {
    this.globalConfig = globalConfig;
    this.serverConfiguration = serverConfiguration;
  }

  @Override
  protected void doBeforeStart() {
    add(
      globalConfig,
      serverConfiguration,
      new GlobalTempFolderProvider(),
      ServerVersionAndStatusChecker.class,
      PluginVersionChecker.class,
      SonarLintWsClient.class,
      GlobalUpdateExecutor.class,
      ModuleConfigUpdateExecutor.class,
      PluginReferencesDownloader.class,
      GlobalPropertiesDownloader.class,
      ModuleListDownloader.class,
      RulesDownloader.class,
      QualityProfilesDownloader.class,
      new PluginCacheProvider(),
      PluginHashes.class,
      StorageManager.class);
  }

  public void update(ProgressWrapper progress) {
    getComponentByType(GlobalUpdateExecutor.class).update(progress);
  }

  public void updateModule(String moduleKey) {
    GlobalUpdateStatus updateStatus = getComponentByType(StorageManager.class).getGlobalUpdateStatus();
    if (updateStatus == null) {
      throw new IllegalStateException("Please update server first");
    }
    getComponentByType(ModuleConfigUpdateExecutor.class).update(moduleKey);
  }

}
