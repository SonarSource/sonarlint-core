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

import java.util.List;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalSyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.sync.GlobalPropertiesSync;
import org.sonarsource.sonarlint.core.container.connected.sync.GlobalSync;
import org.sonarsource.sonarlint.core.container.connected.sync.PluginReferencesSync;
import org.sonarsource.sonarlint.core.container.connected.sync.ProjectSync;
import org.sonarsource.sonarlint.core.container.connected.sync.RulesSync;
import org.sonarsource.sonarlint.core.container.connected.validate.AuthenticationChecker;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;
import org.sonarsource.sonarlint.core.plugin.cache.PluginHashes;

public class ConnectedContainer extends ComponentContainer {

  private final ServerConfiguration serverConfiguration;
  private final GlobalConfiguration globalConfig;

  public ConnectedContainer(GlobalConfiguration globalConfig, ServerConfiguration serverConfiguration) {
    this.globalConfig = globalConfig;
    this.serverConfiguration = serverConfiguration;
  }

  @Override
  protected void doBeforeStart() {
    add(
      globalConfig,
      serverConfiguration,
      AuthenticationChecker.class,
      SonarLintWsClient.class,
      GlobalSync.class,
      ProjectSync.class,
      PluginReferencesSync.class,
      GlobalPropertiesSync.class,
      RulesSync.class,
      new PluginCacheProvider(),
      PluginHashes.class,
      StorageManager.class,
      ModuleFinder.class);
  }

  public ValidationResult validateCredentials() {
    return getComponentByType(AuthenticationChecker.class).validateCredentials();
  }

  public void sync() {
    getComponentByType(GlobalSync.class).sync();
  }

  public List<RemoteModule> searchModule(String exactKeyOrPartialName) {
    return getComponentByType(ModuleFinder.class).searchModules(exactKeyOrPartialName);
  }

  public void syncModule(String moduleKey) {
    GlobalSyncStatus syncStatus = getComponentByType(StorageManager.class).getGlobalSyncStatus();
    if (syncStatus == null) {
      throw new IllegalStateException("Please sync server first");
    }
    getComponentByType(ProjectSync.class).sync(moduleKey);
  }

}
