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
package org.sonarsource.sonarlint.core.container.storage;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.sonar.api.Plugin;
import org.sonar.api.SonarQubeVersion;
import org.sonar.api.internal.ApiVersion;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.UriReader;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.container.ComponentContainer;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.global.ExtensionInstaller;
import org.sonarsource.sonarlint.core.container.global.GlobalConfigurationProvider;
import org.sonarsource.sonarlint.core.container.global.GlobalExtensionContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalSettings;
import org.sonarsource.sonarlint.core.container.global.GlobalTempFolderProvider;
import org.sonarsource.sonarlint.core.plugin.DefaultPluginJarExploder;
import org.sonarsource.sonarlint.core.plugin.PluginCacheLoader;
import org.sonarsource.sonarlint.core.plugin.PluginClassloaderFactory;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;
import org.sonarsource.sonarlint.core.plugin.PluginLoader;
import org.sonarsource.sonarlint.core.plugin.PluginRepository;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCacheProvider;

public class StorageContainer extends ComponentContainer {
  private static final Logger LOG = Loggers.get(StorageContainer.class);
  private static final DateFormat DATE_FORMAT = new SimpleDateFormat();

  public static StorageContainer create(ConnectedGlobalConfiguration globalConfig) {
    StorageContainer container = new StorageContainer();
    container.add(globalConfig);
    return container;
  }

  private GlobalExtensionContainer globalExtensionContainer;

  @Override
  protected void doBeforeStart() {
    Version version = ApiVersion.load(System2.INSTANCE);
    add(
      StorageContainerHandler.class,

      // storage directories and tmp
      StoragePaths.class,
      StorageReader.class,
      new GlobalTempFolderProvider(),

      // plugins
      PluginRepository.class,
      PluginCacheLoader.class,
      PluginVersionChecker.class,
      PluginLoader.class,
      PluginClassloaderFactory.class,
      DefaultPluginJarExploder.class,
      StoragePluginIndexProvider.class,
      new PluginCacheProvider(),

      // storage readers
      AllProjectReader.class,
      IssueStoreReader.class,
      GlobalUpdateStatusReader.class,
      ProjectStorageStatusReader.class,
      StorageRuleDetailsReader.class,
      IssueStoreFactory.class,

      // analysis
      StorageAnalyzer.class,
      StorageFileExclusions.class,

      // needed during analysis (immutable)
      UriReader.class,
      GlobalSettings.class,
      new GlobalConfigurationProvider(),
      ExtensionInstaller.class,
      new StorageRulesProvider(),
      new StorageQProfilesProvider(),
      new SonarQubeRulesProvider(),
      new SonarQubeVersion(version),
      SonarRuntimeImpl.forSonarLint(version),
      System2.INSTANCE);
  }

  @Override
  protected void doAfterStart() {
    ConnectedGlobalConfiguration config = getComponentByType(ConnectedGlobalConfiguration.class);
    GlobalStorageStatus updateStatus = getComponentByType(StorageContainerHandler.class).getGlobalStorageStatus();

    if (updateStatus != null) {
      LOG.info("Using storage for server '{}' (last update {})", config.getServerId(), DATE_FORMAT.format(updateStatus.getLastUpdateDate()));
      installPlugins();
    } else {
      LOG.warn("No storage for server '{}'. Please update.", config.getServerId());
    }

    this.globalExtensionContainer = new GlobalExtensionContainer(this);
    globalExtensionContainer.startComponents();
  }

  @Override
  public ComponentContainer stopComponents(boolean swallowException) {
    try {
      if (globalExtensionContainer != null) {
        globalExtensionContainer.stopComponents(swallowException);
      }
    } finally {
      super.stopComponents(swallowException);
    }
    return this;
  }

  protected void installPlugins() {
    PluginRepository pluginRepository = getComponentByType(PluginRepository.class);
    for (PluginInfo pluginInfo : pluginRepository.getPluginInfos()) {
      Plugin instance = pluginRepository.getPluginInstance(pluginInfo.getKey());
      addExtension(pluginInfo, instance);
    }
  }

  public GlobalExtensionContainer getGlobalExtensionContainer() {
    return globalExtensionContainer;
  }

  public StorageContainerHandler getHandler() {
    return getComponentByType(StorageContainerHandler.class);
  }
}
