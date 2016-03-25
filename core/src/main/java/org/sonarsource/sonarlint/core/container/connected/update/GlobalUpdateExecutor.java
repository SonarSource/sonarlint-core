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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.nio.file.Path;
import java.util.Date;
import java.util.Set;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ServerInfos;
import org.sonarsource.sonarlint.core.proto.Sonarlint.UpdateStatus;
import org.sonarsource.sonarlint.core.util.FileUtils;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class GlobalUpdateExecutor {

  private final StorageManager storageManager;
  private final PluginReferencesDownloader pluginReferenceDownloader;
  private final GlobalPropertiesDownloader globalPropertiesDownloader;
  private final RulesDownloader rulesDownloader;
  private final TempFolder tempFolder;
  private final ModuleListDownloader moduleListDownloader;
  private final ServerVersionAndStatusChecker statusChecker;
  private final SonarLintWsClient wsClient;

  public GlobalUpdateExecutor(StorageManager storageManager, SonarLintWsClient wsClient, ServerVersionAndStatusChecker statusChecker,
    PluginReferencesDownloader pluginReferenceDownloader, GlobalPropertiesDownloader globalPropertiesDownloader, RulesDownloader rulesDownloader,
    ModuleListDownloader moduleListDownloader, TempFolder tempFolder) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.statusChecker = statusChecker;
    this.pluginReferenceDownloader = pluginReferenceDownloader;
    this.globalPropertiesDownloader = globalPropertiesDownloader;
    this.rulesDownloader = rulesDownloader;
    this.moduleListDownloader = moduleListDownloader;
    this.tempFolder = tempFolder;
  }

  public void update() {
    ServerInfos serverStatus = statusChecker.checkVersionAndStatus();

    Path temp = tempFolder.newDir().toPath();
    ProtobufUtil.writeToFile(serverStatus, temp.resolve(StorageManager.SERVER_INFO_PB));

    Set<String> allowedPlugins = globalPropertiesDownloader.fetchGlobalPropertiesTo(temp);

    pluginReferenceDownloader.fetchPluginsTo(temp, allowedPlugins);

    rulesDownloader.fetchRulesTo(temp);
    moduleListDownloader.fetchModulesList(temp);

    UpdateStatus updateStatus = UpdateStatus.newBuilder()
      .setClientUserAgent(wsClient.getUserAgent())
      .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
      .setUpdateTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(updateStatus, temp.resolve(StorageManager.UPDATE_STATUS_PB));

    Path dest = storageManager.getGlobalStorageRoot();
    FileUtils.deleteDirectory(dest);
    FileUtils.forceMkDirs(dest.getParent());
    FileUtils.moveDir(temp, dest);
  }

}
